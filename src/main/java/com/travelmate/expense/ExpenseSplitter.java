package com.travelmate.expense;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.common.money.MoneyService;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits an expense's {@code AMOUNT_BASE} among members per {@link SplitType} (SPEC §7 Module 11).
 *
 * <p>All arithmetic runs on <b>integer minor units</b> (a {@link BigInteger} count of 1e-4 units),
 * never divided {@link BigDecimal}, so the shares sum to the total <b>exactly</b> with no rounding
 * drift. Any indivisible remainder is handed out one minor unit at a time — for {@code EQUAL} to the
 * first members by id; for {@code PERCENT}/{@code SHARES} by the largest fractional remainder
 * (ties by member id) — which keeps every share within one minor unit of its fair value.
 */
@Component
public class ExpenseSplitter {

    /** One participant's input. {@code value} meaning depends on the split type (null for EQUAL). */
    public record Participant(Long memberId, BigDecimal value) {
    }

    /**
     * @return an ordered map memberId -&gt; share (base currency, scale 4) summing to {@code amountBase}.
     */
    public Map<Long, BigDecimal> split(BigDecimal amountBase, SplitType type, List<Participant> participants) {
        if (participants == null || participants.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "At least one participant is required.");
        }
        long distinct = participants.stream().map(Participant::memberId).distinct().count();
        if (distinct != participants.size()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A member appears more than once in the split.");
        }
        BigInteger totalMinor = minor(amountBase);
        if (totalMinor.signum() < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount must not be negative.");
        }
        List<Long> ids = participants.stream().map(Participant::memberId).toList();
        BigInteger[] shares = switch (type) {
            case EQUAL -> equal(totalMinor, participants);
            case EXACT -> exact(totalMinor, participants);
            case PERCENT -> proportional(totalMinor, participants, true);
            case SHARES -> proportional(totalMinor, participants, false);
        };

        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            result.put(ids.get(i), fromMinor(shares[i]));
        }
        return result;
    }

    private BigInteger[] equal(BigInteger totalMinor, List<Participant> participants) {
        int n = participants.size();
        BigInteger nBig = BigInteger.valueOf(n);
        BigInteger base = totalMinor.divide(nBig);
        int remainder = totalMinor.mod(nBig).intValueExact();
        // The remainder goes to the first members by id (deterministic).
        List<Integer> byId = indicesSortedByMemberId(participants);
        BigInteger[] shares = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            shares[i] = base;
        }
        for (int r = 0; r < remainder; r++) {
            int idx = byId.get(r);
            shares[idx] = shares[idx].add(BigInteger.ONE);
        }
        return shares;
    }

    private BigInteger[] exact(BigInteger totalMinor, List<Participant> participants) {
        int n = participants.size();
        BigInteger[] shares = new BigInteger[n];
        BigInteger sum = BigInteger.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal v = require(participants.get(i).value(), "EXACT split requires an amount per member.");
            if (v.signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Split amounts must not be negative.");
            }
            shares[i] = minor(v);
            sum = sum.add(shares[i]);
        }
        if (!sum.equals(totalMinor)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "EXACT split must sum to the expense amount.");
        }
        return shares;
    }

    /** PERCENT (weights are percentages summing to 100) or SHARES (arbitrary positive weights). */
    private BigInteger[] proportional(BigInteger totalMinor, List<Participant> participants, boolean percent) {
        int n = participants.size();
        BigDecimal[] weights = new BigDecimal[n];
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal w = require(participants.get(i).value(),
                    percent ? "PERCENT split requires a percent per member." : "SHARES split requires a weight per member.");
            if (w.signum() <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Split weights must be positive.");
            }
            weights[i] = w;
            weightTotal = weightTotal.add(w);
        }
        if (percent && weightTotal.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "PERCENT split must sum to 100.");
        }

        BigDecimal totalMinorBd = new BigDecimal(totalMinor);
        BigInteger[] floors = new BigInteger[n];
        BigDecimal[] remainders = new BigDecimal[n];
        BigInteger allocated = BigInteger.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal exact = totalMinorBd.multiply(weights[i])
                    .divide(weightTotal, MathContext.DECIMAL64);
            floors[i] = exact.setScale(0, RoundingMode.FLOOR).toBigInteger();
            remainders[i] = exact.subtract(new BigDecimal(floors[i]));
            allocated = allocated.add(floors[i]);
        }
        int leftover = totalMinor.subtract(allocated).intValueExact();
        // Largest fractional remainder first; ties by member id (deterministic).
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort(Comparator
                .comparing((Integer i) -> remainders[i]).reversed()
                .thenComparing(i -> participants.get(i).memberId()));
        BigInteger[] shares = floors.clone();
        for (int k = 0; k < leftover; k++) {
            int idx = order.get(k);
            shares[idx] = shares[idx].add(BigInteger.ONE);
        }
        return shares;
    }

    private static List<Integer> indicesSortedByMemberId(List<Participant> participants) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            idx.add(i);
        }
        idx.sort(Comparator.comparing(i -> participants.get(i).memberId()));
        return idx;
    }

    private static BigDecimal require(BigDecimal value, String message) {
        if (value == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value;
    }

    /** A base-currency amount (scale 4) as an exact integer count of minor (1e-4) units. */
    private static BigInteger minor(BigDecimal amount) {
        return amount.setScale(MoneyService.AMOUNT_SCALE, RoundingMode.HALF_UP).unscaledValue();
    }

    private static BigDecimal fromMinor(BigInteger minor) {
        return new BigDecimal(minor, MoneyService.AMOUNT_SCALE);
    }
}
