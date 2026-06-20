package com.travelmate.trip;

/** Trip role, ordered by privilege: OWNER &gt; EDITOR &gt; VIEWER. */
public enum MemberRole {
    VIEWER,
    EDITOR,
    OWNER;

    /** True if this role is at least as privileged as {@code required}. */
    public boolean satisfies(MemberRole required) {
        return this.ordinal() >= required.ordinal();
    }
}
