package com.travelmate.expense;

import com.travelmate.common.exception.ApiException;
import com.travelmate.expense.ExpenseSplitter.Participant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the split math — especially that shares sum to the total exactly (SPEC §7 Module 11). */
class ExpenseSplitterTest {

    private final ExpenseSplitter splitter = new ExpenseSplitter();

    private static BigDecimal money(String v) {
        return new BigDecimal(v);
    }

    private static BigDecimal sum(Map<Long, BigDecimal> shares) {
        return shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void equal_distributesRemainderToFirstMembersById() {
        // 100.0000 / 3 = 33.3333 each, 0.0001 remainder → the first member by id absorbs it.
        Map<Long, BigDecimal> shares = splitter.split(money("100.0000"), SplitType.EQUAL,
                List.of(new Participant(3L, null), new Participant(1L, null), new Participant(2L, null)));

        assertThat(shares.get(1L)).isEqualByComparingTo("33.3334");
        assertThat(shares.get(2L)).isEqualByComparingTo("33.3333");
        assertThat(shares.get(3L)).isEqualByComparingTo("33.3333");
        assertThat(sum(shares)).isEqualByComparingTo("100.0000");
    }

    @Test
    void exact_mustSumToTheAmount() {
        Map<Long, BigDecimal> ok = splitter.split(money("100.00"), SplitType.EXACT,
                List.of(new Participant(1L, money("60")), new Participant(2L, money("40"))));
        assertThat(ok.get(1L)).isEqualByComparingTo("60");
        assertThat(sum(ok)).isEqualByComparingTo("100");

        assertThatThrownBy(() -> splitter.split(money("100.00"), SplitType.EXACT,
                List.of(new Participant(1L, money("60")), new Participant(2L, money("30")))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void percent_mustSumTo100_andSharesSumToAmount() {
        Map<Long, BigDecimal> shares = splitter.split(money("100.0000"), SplitType.PERCENT,
                List.of(new Participant(1L, money("33.33")),
                        new Participant(2L, money("33.33")),
                        new Participant(3L, money("33.34"))));
        assertThat(sum(shares)).isEqualByComparingTo("100.0000");

        assertThatThrownBy(() -> splitter.split(money("100"), SplitType.PERCENT,
                List.of(new Participant(1L, money("50")), new Participant(2L, money("40")))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void shares_splitByWeight() {
        Map<Long, BigDecimal> shares = splitter.split(money("100.0000"), SplitType.SHARES,
                List.of(new Participant(1L, money("1")),
                        new Participant(2L, money("2")),
                        new Participant(3L, money("1"))));
        assertThat(shares.get(1L)).isEqualByComparingTo("25.0000");
        assertThat(shares.get(2L)).isEqualByComparingTo("50.0000");
        assertThat(shares.get(3L)).isEqualByComparingTo("25.0000");
        assertThat(sum(shares)).isEqualByComparingTo("100.0000");
    }

    @Test
    void shares_indivisible_stillSumsExactly() {
        // 10.00 over 3 equal weights never divides evenly — must still total exactly.
        Map<Long, BigDecimal> shares = splitter.split(money("10.0000"), SplitType.SHARES,
                List.of(new Participant(1L, money("1")),
                        new Participant(2L, money("1")),
                        new Participant(3L, money("1"))));
        assertThat(sum(shares)).isEqualByComparingTo("10.0000");
    }

    @Test
    void rejectsDuplicateMemberAndEmpty() {
        assertThatThrownBy(() -> splitter.split(money("10"), SplitType.EQUAL,
                List.of(new Participant(1L, null), new Participant(1L, null))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> splitter.split(money("10"), SplitType.EQUAL, List.of()))
                .isInstanceOf(ApiException.class);
    }
}
