package com.travelmate.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByTripIdOrderBySpentAtDescIdDesc(Long tripId);

    /** Used by settlement (paidFromFund=false: personal debt) and the fund balance (true). */
    List<Expense> findByTripIdAndPaidFromFund(Long tripId, boolean paidFromFund);

    Optional<Expense> findByRid(String rid);

    @Query("SELECT COALESCE(SUM(e.amountBase), 0) FROM Expense e "
            + "WHERE e.tripId = :tripId AND e.paidFromFund = true")
    BigDecimal sumFundPaidAmountBaseByTrip(@Param("tripId") Long tripId);
}
