package com.travelmate.expense;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByTripIdOrderBySpentAtDescIdDesc(Long tripId);

    /** Admin search over the title (case-insensitive); empty q matches all. */
    @Query("select e from Expense e where :q = '' or lower(e.title) like lower(concat('%', :q, '%'))")
    Page<Expense> search(@Param("q") String q, Pageable pageable);

    /** Used by settlement (paidFromFund=false: personal debt) and the fund balance (true). */
    List<Expense> findByTripIdAndPaidFromFund(Long tripId, boolean paidFromFund);

    Optional<Expense> findByRid(String rid);

    /** Expenses paid by a member — used to re-point the payer when two members are merged. */
    List<Expense> findByPayerId(Long payerId);

    @Query("SELECT COALESCE(SUM(e.amountBase), 0) FROM Expense e "
            + "WHERE e.tripId = :tripId AND e.paidFromFund = true")
    BigDecimal sumFundPaidAmountBaseByTrip(@Param("tripId") Long tripId);

    /** Total of every expense (personal + fund-paid), each counted once — used by dashboard/report. */
    @Query("SELECT COALESCE(SUM(e.amountBase), 0) FROM Expense e WHERE e.tripId = :tripId")
    BigDecimal sumAmountBaseByTrip(@Param("tripId") Long tripId);
}
