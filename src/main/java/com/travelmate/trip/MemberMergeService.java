package com.travelmate.trip;

import com.travelmate.checklist.ChecklistItem;
import com.travelmate.checklist.ChecklistItemRepository;
import com.travelmate.expense.Expense;
import com.travelmate.expense.ExpenseRepository;
import com.travelmate.expense.ExpenseShare;
import com.travelmate.expense.ExpenseShareRepository;
import com.travelmate.fund.FundContribution;
import com.travelmate.fund.FundContributionRepository;
import com.travelmate.ticket.Ticket;
import com.travelmate.ticket.TicketRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Re-points every member-scoped record from one trip member onto another — the data half of the
 * "merge member" action. Because all money/ticket/checklist references use {@code TRIP_MEMBERS.ID},
 * a merge is just moving those ids; the caller soft-deletes the now-empty source afterwards.
 */
@Service
public class MemberMergeService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository shareRepository;
    private final FundContributionRepository fundContributionRepository;
    private final TicketRepository ticketRepository;
    private final ChecklistItemRepository checklistItemRepository;

    public MemberMergeService(ExpenseRepository expenseRepository,
                              ExpenseShareRepository shareRepository,
                              FundContributionRepository fundContributionRepository,
                              TicketRepository ticketRepository,
                              ChecklistItemRepository checklistItemRepository) {
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.fundContributionRepository = fundContributionRepository;
        this.ticketRepository = ticketRepository;
        this.checklistItemRepository = checklistItemRepository;
    }

    /** Move all of {@code sourceId}'s expenses, shares, contributions, tickets and checklist
     *  assignments onto {@code targetId}. Runs inside the caller's transaction. */
    public void repoint(Long sourceId, Long targetId) {
        // Expenses the source paid for.
        for (Expense e : expenseRepository.findByPayerId(sourceId)) {
            e.setPayerId(targetId);
        }

        // Expense shares: at most one share per member per expense, so if the target already has a
        // share in the same expense, fold the source's amount into it; otherwise re-point the row.
        Map<Long, ExpenseShare> targetShareByExpense = new HashMap<>();
        for (ExpenseShare ts : shareRepository.findByMemberId(targetId)) {
            targetShareByExpense.put(ts.getExpenseId(), ts);
        }
        for (ExpenseShare ss : shareRepository.findByMemberId(sourceId)) {
            ExpenseShare existing = targetShareByExpense.get(ss.getExpenseId());
            if (existing != null) {
                existing.setShareBase(existing.getShareBase().add(ss.getShareBase()));
                ss.setDeleted(true);
            } else {
                ss.setMemberId(targetId);
                targetShareByExpense.put(ss.getExpenseId(), ss);
            }
        }

        for (FundContribution c : fundContributionRepository.findByMemberId(sourceId)) {
            c.setMemberId(targetId);
        }
        for (Ticket t : ticketRepository.findByMemberId(sourceId)) {
            t.setMemberId(targetId);
        }
        for (ChecklistItem i : checklistItemRepository.findByAssigneeId(sourceId)) {
            i.setAssigneeId(targetId);
        }
    }
}
