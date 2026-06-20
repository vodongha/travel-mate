package com.travelmate.common.entity;

/**
 * Unified spending category shared by BUDGET and EXPENSE (SPEC §6 enums) so budget-vs-actual
 * reports line up per category. Lives in common so both modules depend on one definition.
 */
public enum Category {
    TRANSPORT,
    ACCOMMODATION,
    FOOD,
    SHOPPING,
    ACTIVITY,
    SIGHTSEEING,
    MEDICAL,
    PARKING,
    OTHER
}
