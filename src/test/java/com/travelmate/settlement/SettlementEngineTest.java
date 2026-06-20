package com.travelmate.settlement;

import com.travelmate.settlement.SettlementEngine.Transfer;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the greedy debt-simplification engine (SPEC §7 Module 11). */
class SettlementEngineTest {

    private final SettlementEngine engine = new SettlementEngine();

    private static Map<Long, BigInteger> net(Object... pairs) {
        Map<Long, BigInteger> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(((Number) pairs[i]).longValue(), BigInteger.valueOf(((Number) pairs[i + 1]).longValue()));
        }
        return m;
    }

    @Test
    void twoParty_singleTransfer() {
        List<Transfer> t = engine.settle(net(1, 100, 2, -100));
        assertThat(t).hasSize(1);
        assertThat(t.get(0).fromMemberId()).isEqualTo(2L);
        assertThat(t.get(0).toMemberId()).isEqualTo(1L);
        assertThat(t.get(0).amountMinor()).isEqualTo(BigInteger.valueOf(100));
    }

    @Test
    void oneCreditorTwoDebtors_twoTransfers_conserved() {
        List<Transfer> t = engine.settle(net(1, 350, 2, -230, 3, -120));
        assertThat(t).hasSize(2);
        // every debtor clears exactly their debt, the creditor receives the whole 350
        assertThat(paidBy(t, 2L)).isEqualTo(230);
        assertThat(paidBy(t, 3L)).isEqualTo(120);
        assertThat(receivedBy(t, 1L)).isEqualTo(350);
    }

    @Test
    void twoCreditorsOneDebtor_splitsDeterministicallyByIdOnTie() {
        List<Transfer> t = engine.settle(net(1, 100, 2, 100, 3, -200));
        assertThat(t).hasSize(2);
        // tie on creditor amount → lower id (1) settled first
        assertThat(t.get(0).toMemberId()).isEqualTo(1L);
        assertThat(receivedBy(t, 1L)).isEqualTo(100);
        assertThat(receivedBy(t, 2L)).isEqualTo(100);
        assertThat(paidBy(t, 3L)).isEqualTo(200);
    }

    @Test
    void balanced_noDebt_noTransfers() {
        assertThat(engine.settle(net(1, 0, 2, 0))).isEmpty();
        assertThat(engine.settle(Map.of())).isEmpty();
    }

    @Test
    void transfersNeverExceedMemberCountMinusOne() {
        // 4 members, mixed — greedy yields at most n-1 transfers.
        List<Transfer> t = engine.settle(net(1, 500, 2, 300, 3, -350, 4, -450));
        assertThat(t.size()).isLessThanOrEqualTo(3);
        assertThat(receivedBy(t, 1L) + receivedBy(t, 2L)).isEqualTo(paidBy(t, 3L) + paidBy(t, 4L));
    }

    private static long paidBy(List<Transfer> transfers, long memberId) {
        return transfers.stream().filter(x -> x.fromMemberId() == memberId)
                .mapToLong(x -> x.amountMinor().longValue()).sum();
    }

    private static long receivedBy(List<Transfer> transfers, long memberId) {
        return transfers.stream().filter(x -> x.toMemberId() == memberId)
                .mapToLong(x -> x.amountMinor().longValue()).sum();
    }
}
