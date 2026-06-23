package com.travelmate.trip;

import com.travelmate.checklist.ChecklistCompletion;
import com.travelmate.checklist.ChecklistCompletionRepository;
import com.travelmate.checklist.ChecklistItem;
import com.travelmate.checklist.ChecklistItemRepository;
import com.travelmate.expense.Expense;
import com.travelmate.expense.ExpenseRepository;
import com.travelmate.expense.ExpenseShare;
import com.travelmate.expense.ExpenseShareRepository;
import com.travelmate.fund.FundContribution;
import com.travelmate.fund.FundContributionRepository;
import com.travelmate.ticket.TicketMember;
import com.travelmate.ticket.TicketMemberRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    private final TicketMemberRepository ticketMemberRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistCompletionRepository checklistCompletionRepository;

    public MemberMergeService(ExpenseRepository expenseRepository,
                              ExpenseShareRepository shareRepository,
                              FundContributionRepository fundContributionRepository,
                              TicketMemberRepository ticketMemberRepository,
                              ChecklistItemRepository checklistItemRepository,
                              ChecklistCompletionRepository checklistCompletionRepository) {
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.fundContributionRepository = fundContributionRepository;
        this.ticketMemberRepository = ticketMemberRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.checklistCompletionRepository = checklistCompletionRepository;
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

        // Ticket memberships: re-point each of the source's rows to the target, but skip (delete) a
        // row when the target is already on that same ticket (the join is unique per ticket+member).
        Set<Long> targetTicketIds = new HashSet<>();
        for (TicketMember tm : ticketMemberRepository.findByMemberId(targetId)) {
            targetTicketIds.add(tm.getTicketId());
        }
        for (TicketMember tm : ticketMemberRepository.findByMemberId(sourceId)) {
            if (targetTicketIds.contains(tm.getTicketId())) {
                ticketMemberRepository.delete(tm);
            } else {
                tm.setMemberId(targetId);
                targetTicketIds.add(tm.getTicketId());
            }
        }

        for (ChecklistItem i : checklistItemRepository.findByAssigneeId(sourceId)) {
            i.setAssigneeId(targetId);
        }

        // Checklist completions: move the source's ticks to the target, skipping items the target
        // already ticked (the join is unique per item+member).
        Set<Long> targetDoneItems = new HashSet<>();
        for (ChecklistCompletion c : checklistCompletionRepository.findByMemberId(targetId)) {
            targetDoneItems.add(c.getItemId());
        }
        for (ChecklistCompletion c : checklistCompletionRepository.findByMemberId(sourceId)) {
            if (targetDoneItems.contains(c.getItemId())) {
                checklistCompletionRepository.delete(c);
            } else {
                c.setMemberId(targetId);
                targetDoneItems.add(c.getItemId());
            }
        }
    }
}
