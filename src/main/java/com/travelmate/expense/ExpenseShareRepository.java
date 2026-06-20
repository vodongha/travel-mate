package com.travelmate.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {

    List<ExpenseShare> findByExpenseId(Long expenseId);

    List<ExpenseShare> findByExpenseIdIn(List<Long> expenseIds);
}
