package com.travelmate.settlement;

import com.travelmate.common.money.MoneyService;
import com.travelmate.expense.Expense;
import com.travelmate.expense.ExpenseRepository;
import com.travelmate.expense.ExpenseShare;
import com.travelmate.expense.ExpenseShareRepository;
import com.travelmate.settlement.SettlementEngine.Transfer;
import com.travelmate.settlement.dto.SettlementResponse;
import com.travelmate.settlement.dto.SettlementResponse.Balance;
import com.travelmate.settlement.dto.SettlementResponse.Transaction;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes settlement on the fly (SPEC §7 Module 11): each member's net = paid - owed over personal
 * (non-fund) expenses, then {@link SettlementEngine} minimises the transfers. All maths is in
 * integer minor units so balances net to zero exactly. Fund contributions/spending are deliberately
 * excluded — that is a separate reconciliation (Module 10).
 */
@Service
public class SettlementService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository shareRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripAccessGuard guard;
    private final SettlementEngine engine;

    public SettlementService(ExpenseRepository expenseRepository,
                             ExpenseShareRepository shareRepository,
                             TripMemberRepository tripMemberRepository,
                             TripAccessGuard guard,
                             SettlementEngine engine) {
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.guard = guard;
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public SettlementResponse settle(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();

        // Personal (debt-creating) expenses only; fund-paid ones don't settle between members.
        List<Expense> expenses = expenseRepository.findByTripIdAndPaidFromFund(trip.getId(), false);
        Map<Long, BigInteger> paid = new HashMap<>();
        for (Expense e : expenses) {
            paid.merge(e.getPayerId(), minor(e.getAmountBase()), BigInteger::add);
        }
        Map<Long, BigInteger> owed = new HashMap<>();
        if (!expenses.isEmpty()) {
            List<ExpenseShare> shares = shareRepository.findByExpenseIdIn(expenses.stream().map(Expense::getId).toList());
            for (ExpenseShare s : shares) {
                owed.merge(s.getMemberId(), minor(s.getShareBase()), BigInteger::add);
            }
        }

        Set<Long> memberIds = new LinkedHashSet<>();
        memberIds.addAll(paid.keySet());
        memberIds.addAll(owed.keySet());
        Map<Long, BigInteger> net = new HashMap<>();
        for (Long id : memberIds) {
            net.put(id, paid.getOrDefault(id, BigInteger.ZERO).subtract(owed.getOrDefault(id, BigInteger.ZERO)));
        }

        Map<Long, TripMember> members = membersById(memberIds);
        List<Balance> balances = memberIds.stream()
                .map(id -> new Balance(
                        rid(members, id), name(members, id),
                        money(paid.getOrDefault(id, BigInteger.ZERO)),
                        money(owed.getOrDefault(id, BigInteger.ZERO)),
                        money(net.get(id))))
                .sorted(Comparator.comparing(Balance::net).reversed())
                .toList();

        List<Transaction> transactions = new ArrayList<>();
        for (Transfer t : engine.settle(net)) {
            transactions.add(new Transaction(
                    rid(members, t.fromMemberId()), name(members, t.fromMemberId()),
                    rid(members, t.toMemberId()), name(members, t.toMemberId()),
                    money(t.amountMinor())));
        }
        return new SettlementResponse(trip.getBaseCurrency(), balances, transactions);
    }

    /** Resolve members by id, <b>including soft-deleted</b> ones (a member can leave but still owe). */
    private Map<Long, TripMember> membersById(Set<Long> ids) {
        Map<Long, TripMember> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (TripMember m : tripMemberRepository.findAllByIdInIncludingDeleted(ids)) {
                map.put(m.getId(), m);
            }
        }
        return map;
    }

    private static String rid(Map<Long, TripMember> members, Long id) {
        TripMember m = members.get(id);
        return m == null ? null : m.getRid();
    }

    private static String name(Map<Long, TripMember> members, Long id) {
        TripMember m = members.get(id);
        return m == null ? null : m.getDisplayName();
    }

    private static BigInteger minor(BigDecimal amount) {
        return amount.setScale(MoneyService.AMOUNT_SCALE, RoundingMode.HALF_UP).unscaledValue();
    }

    private static BigDecimal money(BigInteger minor) {
        return new BigDecimal(minor, MoneyService.AMOUNT_SCALE);
    }
}
