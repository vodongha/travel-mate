package com.travelmate.checklist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findByTripIdOrderBySortOrderAscIdAsc(Long tripId);

    Optional<ChecklistItem> findByRid(String rid);

    /** Items assigned to a member — used to re-point the assignee when two members are merged. */
    List<ChecklistItem> findByAssigneeId(Long assigneeId);
}
