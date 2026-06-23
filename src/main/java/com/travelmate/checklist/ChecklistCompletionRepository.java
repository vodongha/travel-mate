package com.travelmate.checklist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChecklistCompletionRepository extends JpaRepository<ChecklistCompletion, Long> {

    /** This member's completions (member ids are trip-scoped, so this is one trip's worth). */
    List<ChecklistCompletion> findByMemberId(Long memberId);

    Optional<ChecklistCompletion> findByItemIdAndMemberId(Long itemId, Long memberId);

    void deleteByItemId(Long itemId);
}
