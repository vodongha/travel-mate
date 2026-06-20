package com.travelmate.settlement;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Debt simplification (SPEC §7 Module 11). Given each member's net balance in integer minor units
 * (positive = is owed money, negative = owes money), produces a <b>minimised</b> set of transfers
 * via greedy min-cash-flow: repeatedly settle the biggest debtor against the biggest creditor.
 *
 * <p>Greedy is not provably the absolute minimum number of transfers (that is NP-hard) but is near
 * optimal and standard. All arithmetic is on {@link BigInteger} minor units, so amounts are exact.
 * Ties are broken by member id for deterministic, testable output.
 */
@Component
public class SettlementEngine {

    /** One payment: {@code fromMemberId} pays {@code toMemberId} {@code amountMinor} (minor units). */
    public record Transfer(Long fromMemberId, Long toMemberId, BigInteger amountMinor) {
    }

    public List<Transfer> settle(Map<Long, BigInteger> netByMember) {
        PriorityQueue<Node> creditors = new PriorityQueue<>(nodeOrder());
        PriorityQueue<Node> debtors = new PriorityQueue<>(nodeOrder());
        for (Map.Entry<Long, BigInteger> e : netByMember.entrySet()) {
            int sign = e.getValue().signum();
            if (sign > 0) {
                creditors.add(new Node(e.getKey(), e.getValue()));
            } else if (sign < 0) {
                debtors.add(new Node(e.getKey(), e.getValue().negate()));
            }
        }

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Node creditor = creditors.poll();
            Node debtor = debtors.poll();
            BigInteger pay = creditor.amount.min(debtor.amount);
            transfers.add(new Transfer(debtor.memberId, creditor.memberId, pay));

            BigInteger creditorLeft = creditor.amount.subtract(pay);
            BigInteger debtorLeft = debtor.amount.subtract(pay);
            if (creditorLeft.signum() > 0) {
                creditors.add(new Node(creditor.memberId, creditorLeft));
            }
            if (debtorLeft.signum() > 0) {
                debtors.add(new Node(debtor.memberId, debtorLeft));
            }
        }
        return transfers;
    }

    private static Comparator<Node> nodeOrder() {
        return Comparator.comparing((Node n) -> n.amount).reversed()
                .thenComparingLong(n -> n.memberId);
    }

    private record Node(Long memberId, BigInteger amount) {
    }
}
