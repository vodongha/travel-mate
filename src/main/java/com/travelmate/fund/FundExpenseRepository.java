package com.travelmate.fund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FundExpenseRepository extends JpaRepository<FundExpense, Long> {

    List<FundExpense> findByTripIdOrderByCreatedAtDescIdDesc(Long tripId);

    Optional<FundExpense> findByRid(String rid);

    @Query("SELECT COALESCE(SUM(e.amountBase), 0) FROM FundExpense e WHERE e.tripId = :tripId")
    BigDecimal sumAmountBaseByTrip(@Param("tripId") Long tripId);
}
