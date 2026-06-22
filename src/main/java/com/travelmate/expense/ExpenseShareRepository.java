package com.travelmate.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {

    List<ExpenseShare> findByExpenseId(Long expenseId);

    List<ExpenseShare> findByExpenseIdIn(List<Long> expenseIds);

    /** A member's shares across all expenses — used to re-point/merge shares when members merge. */
    List<ExpenseShare> findByMemberId(Long memberId);
}
