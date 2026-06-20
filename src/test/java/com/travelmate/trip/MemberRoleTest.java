package com.travelmate.trip;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberRoleTest {

    @Test
    void satisfies_followsOwnerEditorViewerHierarchy() {
        assertThat(MemberRole.OWNER.satisfies(MemberRole.EDITOR)).isTrue();
        assertThat(MemberRole.OWNER.satisfies(MemberRole.OWNER)).isTrue();
        assertThat(MemberRole.EDITOR.satisfies(MemberRole.VIEWER)).isTrue();
        assertThat(MemberRole.EDITOR.satisfies(MemberRole.OWNER)).isFalse();
        assertThat(MemberRole.VIEWER.satisfies(MemberRole.VIEWER)).isTrue();
        assertThat(MemberRole.VIEWER.satisfies(MemberRole.EDITOR)).isFalse();
    }
}
