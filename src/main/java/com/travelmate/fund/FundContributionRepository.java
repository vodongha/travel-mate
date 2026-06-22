package com.travelmate.fund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FundContributionRepository extends JpaRepository<FundContribution, Long> {

    List<FundContribution> findByTripIdOrderByCreatedAtDescIdDesc(Long tripId);

    Optional<FundContribution> findByRid(String rid);

    /** A member's fund contributions — used to re-point them when two members are merged. */
    List<FundContribution> findByMemberId(Long memberId);

    @Query("SELECT COALESCE(SUM(c.amountBase), 0) FROM FundContribution c WHERE c.tripId = :tripId")
    BigDecimal sumAmountBaseByTrip(@Param("tripId") Long tripId);
}
