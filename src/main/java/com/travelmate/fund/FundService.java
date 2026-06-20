package com.travelmate.fund;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.common.money.MoneyService;
import com.travelmate.common.money.RateResolver;
import com.travelmate.expense.ExpenseRepository;
import com.travelmate.fund.dto.AddContributionRequest;
import com.travelmate.fund.dto.AddFundExpenseRequest;
import com.travelmate.fund.dto.ContributionResponse;
import com.travelmate.fund.dto.FundBalanceResponse;
import com.travelmate.fund.dto.FundExpenseResponse;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared fund: contributions in, fund spending out, and a derived balance (SPEC §7 Module 10). The
 * balance is always aggregated, never stored, so it can't go stale or race. A fund spend creates no
 * personal debt — that separation is what keeps the settlement engine (Module 11) fund-free.
 */
@Service
public class FundService {

    private final FundContributionRepository contributionRepository;
    private final FundExpenseRepository fundExpenseRepository;
    private final ExpenseRepository expenseRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripAccessGuard guard;
    private final MoneyService moneyService;
    private final RateResolver rateResolver;

    public FundService(FundContributionRepository contributionRepository,
                       FundExpenseRepository fundExpenseRepository,
                       ExpenseRepository expenseRepository,
                       TripMemberRepository tripMemberRepository,
                       TripAccessGuard guard,
                       MoneyService moneyService,
                       RateResolver rateResolver) {
        this.contributionRepository = contributionRepository;
        this.fundExpenseRepository = fundExpenseRepository;
        this.expenseRepository = expenseRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.guard = guard;
        this.moneyService = moneyService;
        this.rateResolver = rateResolver;
    }

    // ── contributions ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContributionResponse> listContributions(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        Map<Long, String> memberRids = memberRidMap(trip.getId());
        return contributionRepository.findByTripIdOrderByCreatedAtDescIdDesc(trip.getId()).stream()
                .map(c -> ContributionResponse.from(c, memberRids.get(c.getMemberId())))
                .toList();
    }

    @Transactional
    public ContributionResponse addContribution(Long userId, String tripRid, AddContributionRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        Long memberId = requireMemberId(request.memberRid(), trip.getId());

        String currency = request.currency().toUpperCase();
        BigDecimal rate = rateResolver.resolve(currency, trip.getBaseCurrency(), request.exchangeRate());
        BigDecimal amount = moneyService.normalizeAmount(request.amount());

        FundContribution c = new FundContribution();
        c.setTripId(trip.getId());
        c.setMemberId(memberId);
        c.setCurrency(currency);
        c.setAmount(amount);
        c.setExchangeRate(rate);
        c.setAmountBase(moneyService.toAmountBase(amount, rate));
        c.setNote(request.note());
        c = contributionRepository.save(c);
        return ContributionResponse.from(c, request.memberRid());
    }

    @Transactional
    public void deleteContribution(Long userId, String tripRid, String contributionRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        FundContribution c = contributionRepository.findByRid(contributionRid)
                .filter(x -> trip.getId().equals(x.getTripId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Contribution not found."));
        c.setDeleted(true);
    }

    // ── fund expenses ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FundExpenseResponse> listFundExpenses(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        return fundExpenseRepository.findByTripIdOrderByCreatedAtDescIdDesc(trip.getId()).stream()
                .map(FundExpenseResponse::from)
                .toList();
    }

    @Transactional
    public FundExpenseResponse addFundExpense(Long userId, String tripRid, AddFundExpenseRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        String currency = request.currency().toUpperCase();
        BigDecimal rate = rateResolver.resolve(currency, trip.getBaseCurrency(), request.exchangeRate());
        BigDecimal amount = moneyService.normalizeAmount(request.amount());

        FundExpense e = new FundExpense();
        e.setTripId(trip.getId());
        e.setTitle(request.title().trim());
        e.setCategory(request.category());
        e.setCurrency(currency);
        e.setAmount(amount);
        e.setExchangeRate(rate);
        e.setAmountBase(moneyService.toAmountBase(amount, rate));
        e.setNote(request.note());
        return FundExpenseResponse.from(fundExpenseRepository.save(e));
    }

    @Transactional
    public void deleteFundExpense(Long userId, String tripRid, String fundExpenseRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        FundExpense e = fundExpenseRepository.findByRid(fundExpenseRid)
                .filter(x -> trip.getId().equals(x.getTripId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Fund expense not found."));
        e.setDeleted(true);
    }

    // ── balance (derived) ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FundBalanceResponse balance(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        BigDecimal contributions = moneyService.normalizeAmount(
                contributionRepository.sumAmountBaseByTrip(trip.getId()));
        BigDecimal fundExpenses = moneyService.normalizeAmount(
                fundExpenseRepository.sumAmountBaseByTrip(trip.getId()));
        BigDecimal personalFromFund = moneyService.normalizeAmount(
                expenseRepository.sumFundPaidAmountBaseByTrip(trip.getId()));
        BigDecimal balance = contributions.subtract(fundExpenses).subtract(personalFromFund);
        return new FundBalanceResponse(trip.getBaseCurrency(), contributions, fundExpenses,
                personalFromFund, balance);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Long requireMemberId(String memberRid, Long tripId) {
        TripMember member = tripMemberRepository.findByRid(memberRid)
                .filter(m -> tripId.equals(m.getTripId()))
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Contributor is not a member of this trip."));
        return member.getId();
    }

    private Map<Long, String> memberRidMap(Long tripId) {
        return tripMemberRepository.findByTripId(tripId).stream()
                .collect(Collectors.toMap(TripMember::getId, TripMember::getRid));
    }
}
