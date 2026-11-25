package com.runiverse.running_service.unit_test.relationship.domain;

import com.runiverse.running_service.domain.common.vo.RelationLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("관계 등급 단위 테스트")
class RelationLevelTest {

    @Test
    @DisplayName("등급은 차단 < 남 < 친구 < 본인 순서다 — 선언 순서가 곧 공개 규칙이라 고정한다")
    void order() {
        assertThat(RelationLevel.values())
                .containsExactly(RelationLevel.BLOCKED, RelationLevel.OTHER, RelationLevel.FRIEND, RelationLevel.SELF);
    }

    @ParameterizedTest
    @EnumSource(RelationLevel.class)
    @DisplayName("자기 등급 이상이다")
    void atLeastItself(RelationLevel level) {
        assertThat(level.isAtLeast(level)).isTrue();
    }

    @ParameterizedTest(name = "{0}.isAtLeast({1}) = {2}")
    @CsvSource({
            "SELF,    FRIEND,  true",
            "SELF,    BLOCKED, true",
            "FRIEND,  OTHER,   true",
            "FRIEND,  SELF,    false",
            "OTHER,   FRIEND,  false",
            "BLOCKED, OTHER,   false"
    })
    @DisplayName("높은 등급은 낮은 등급 이상이고, 낮은 등급은 높은 등급에 못 미친다")
    void compares(RelationLevel level, RelationLevel required, boolean expected) {
        assertThat(level.isAtLeast(required)).isEqualTo(expected);
    }
}
