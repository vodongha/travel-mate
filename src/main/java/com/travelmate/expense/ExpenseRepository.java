package com.travelmate.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByTripIdOrderBySpentAtDescIdDesc(Long tripId);

    Optional<Expense> findByRid(String rid);
}
