package com.travelmate.budget;

import com.travelmate.common.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByTripIdOrderByCategoryAsc(Long tripId);

    Optional<Budget> findByRid(String rid);

    boolean existsByTripIdAndCategory(Long tripId, Category category);
}
