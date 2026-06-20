package com.travelmate.expense;

/** How a personal expense is divided among members (SPEC §7 Module 11). */
public enum SplitType {
    /** Split evenly; any rounding remainder goes to the first participant (by member id). */
    EQUAL,
    /** Each participant's exact base-currency amount is given; they must sum to AMOUNT_BASE. */
    EXACT,
    /** Each participant's percentage is given; they must sum to 100. */
    PERCENT,
    /** Each participant's weight is given (e.g. 1:2:1); split proportionally. */
    SHARES
}
