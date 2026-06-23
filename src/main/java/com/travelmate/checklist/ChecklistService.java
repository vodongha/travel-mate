package com.travelmate.checklist;

import com.travelmate.checklist.dto.ChecklistItemResponse;
import com.travelmate.checklist.dto.CreateChecklistItemRequest;
import com.travelmate.checklist.dto.UpdateChecklistItemRequest;
import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Checklist CRUD (SPEC §7 Module 12). All access goes through {@link TripAccessGuard}. */
@Service
public class ChecklistService {

    private final ChecklistItemRepository checklistRepository;
    private final ChecklistCompletionRepository completionRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripAccessGuard guard;

    public ChecklistService(ChecklistItemRepository checklistRepository,
                            ChecklistCompletionRepository completionRepository,
                            TripMemberRepository tripMemberRepository,
                            TripAccessGuard guard) {
        this.checklistRepository = checklistRepository;
        this.completionRepository = completionRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> list(Long userId, String tripRid) {
        var ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Trip trip = ctx.trip();
        List<ChecklistItem> items = checklistRepository.findByTripIdOrderBySortOrderAscIdAsc(trip.getId());

        // Resolve assignee rids in one query rather than N.
        Map<Long, String> memberRids = tripMemberRepository
                .findAllById(items.stream().map(ChecklistItem::getAssigneeId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(TripMember::getId, TripMember::getRid));

        // Every member sees every item, but only their OWN completion state (not anyone else's).
        Set<Long> doneByMe = completionRepository.findByMemberId(ctx.membership().getId()).stream()
                .map(ChecklistCompletion::getItemId).collect(Collectors.toSet());

        return items.stream()
                .map(i -> ChecklistItemResponse.from(i,
                        i.getAssigneeId() == null ? null : memberRids.get(i.getAssigneeId()),
                        doneByMe.contains(i.getId())))
                .toList();
    }

    @Transactional
    public ChecklistItemResponse create(Long userId, String tripRid, CreateChecklistItemRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        ChecklistItem item = new ChecklistItem();
        item.setTripId(trip.getId());
        item.setTitle(request.title().trim());
        item.setAssigneeId(resolveAssigneeId(request.assigneeRid(), trip.getId()));
        if (request.sortOrder() != null) {
            item.setSortOrder(request.sortOrder());
        }
        item = checklistRepository.save(item);
        // Completion is per-member and starts unticked for everyone.
        return ChecklistItemResponse.from(item, request.assigneeRid(), false);
    }

    @Transactional
    public ChecklistItemResponse update(Long userId, String tripRid, String itemRid,
                                        UpdateChecklistItemRequest request) {
        // Any member may tick/untick an item (collaborative — each person checks their packing
        // off); only structural edits (rename, reassign, reorder) need the EDITOR role.
        var ctx = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER);
        Trip trip = ctx.trip();
        ChecklistItem item = loadInTrip(itemRid, trip.getId());
        boolean structural = request.title() != null
                || request.assigneeRid() != null || request.sortOrder() != null;
        if (structural && !ctx.effectiveRole().satisfies(MemberRole.EDITOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Editing a checklist item requires the EDITOR role.");
        }
        if (request.title() != null) {
            item.setTitle(request.title().trim());
        }
        // Ticking/unticking only affects the CALLER's own completion row.
        if (request.completed() != null) {
            setCompletion(item.getId(), ctx.membership().getId(), request.completed());
        }
        if (request.assigneeRid() != null) {
            item.setAssigneeId(request.assigneeRid().isBlank()
                    ? null
                    : resolveAssigneeId(request.assigneeRid(), trip.getId()));
        }
        if (request.sortOrder() != null) {
            item.setSortOrder(request.sortOrder());
        }
        boolean mineDone = completionRepository
                .findByItemIdAndMemberId(item.getId(), ctx.membership().getId()).isPresent();
        return ChecklistItemResponse.from(item, assigneeRidOf(item.getAssigneeId()), mineDone);
    }

    /** Create or remove this member's completion row for an item (idempotent). */
    private void setCompletion(Long itemId, Long memberId, boolean done) {
        var existing = completionRepository.findByItemIdAndMemberId(itemId, memberId);
        if (done && existing.isEmpty()) {
            completionRepository.save(new ChecklistCompletion(itemId, memberId));
        } else if (!done) {
            existing.ifPresent(completionRepository::delete);
        }
    }

    @Transactional
    public void delete(Long userId, String tripRid, String itemRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        ChecklistItem item = loadInTrip(itemRid, trip.getId());
        completionRepository.deleteByItemId(item.getId());
        item.setDeleted(true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ChecklistItem loadInTrip(String itemRid, Long tripId) {
        ChecklistItem item = checklistRepository.findByRid(itemRid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checklist item not found."));
        if (!tripId.equals(item.getTripId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Checklist item not found.");
        }
        return item;
    }

    /** Resolve an assignee member rid to its id, requiring it be a member of this trip. */
    private Long resolveAssigneeId(String assigneeRid, Long tripId) {
        if (assigneeRid == null || assigneeRid.isBlank()) {
            return null;
        }
        TripMember member = tripMemberRepository.findByRid(assigneeRid)
                .filter(m -> tripId.equals(m.getTripId()))
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Assignee is not a member of this trip."));
        return member.getId();
    }

    private String assigneeRidOf(Long assigneeId) {
        return assigneeId == null ? null
                : tripMemberRepository.findById(assigneeId).map(TripMember::getRid).orElse(null);
    }
}
