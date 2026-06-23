package com.travelmate.checklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Join row: a trip member has completed a checklist item (their own tick). Presence = done. A pure
 * join — no rid/audit; rows are created/deleted as a member ticks/unticks.
 */
@Entity
@Table(name = "CHECKLIST_COMPLETIONS")
public class ChecklistCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ITEM_ID", nullable = false)
    private Long itemId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    public ChecklistCompletion() {
    }

    public ChecklistCompletion(Long itemId, Long memberId) {
        this.itemId = itemId;
        this.memberId = memberId;
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }
}
