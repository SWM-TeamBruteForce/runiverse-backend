package com.runiverse.running_service.unit_test.relationship.domain;

import com.runiverse.running_service.domain.relationship.exception.InvalidRelationshipTransitionException;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RelationshipTypeTest {

    // 명세로서의 전이표 — 구현과 따로 적어 둔다. 둘이 어긋나면 테스트가 깨진다.
    private static final Map<RelationshipType, Set<RelationshipType>> ALLOWED =
            new EnumMap<>(Map.of(
                    RelationshipType.NONE,
                    EnumSet.of(RelationshipType.FRIEND_REQUEST_SENT,
                            RelationshipType.FRIEND_REQUEST_RECEIVED,
                            RelationshipType.BLOCKED),
                    RelationshipType.FRIEND_REQUEST_SENT,
                    EnumSet.of(RelationshipType.FRIEND, RelationshipType.NONE, RelationshipType.BLOCKED),
                    RelationshipType.FRIEND_REQUEST_RECEIVED,
                    EnumSet.of(RelationshipType.FRIEND, RelationshipType.NONE, RelationshipType.BLOCKED),
                    RelationshipType.FRIEND,
                    EnumSet.of(RelationshipType.NONE, RelationshipType.BLOCKED),
                    RelationshipType.BLOCKED,
                    EnumSet.of(RelationshipType.NONE)));

    private static Stream<Arguments> allowedPairs() {
        return ALLOWED.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(next -> Arguments.of(entry.getKey(), next)));
    }

    private static Stream<Arguments> deniedPairs() {
        return Arrays.stream(RelationshipType.values())
                .flatMap(current -> Arrays.stream(RelationshipType.values())
                        .filter(next -> !ALLOWED.get(current).contains(next))
                        .map(next -> Arguments.of(current, next)));
    }

    @Nested
    @DisplayName("관계 전이 테스트")
    class TransitionTest {

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("com.runiverse.running_service.unit_test.relationship.domain."
                + "RelationshipTypeTest#allowedPairs")
        @DisplayName("전이표에 있는 조합은 다음 관계를 돌려준다")
        void transitionToAllowedTypeSuccess(RelationshipType current, RelationshipType next) {
            // when & then
            assertThat(current.transitionTo(next)).isEqualTo(next);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("com.runiverse.running_service.unit_test.relationship.domain."
                + "RelationshipTypeTest#deniedPairs")
        @DisplayName("전이표에 없는 조합은 예외가 발생한다")
        void transitionToDeniedTypeFails(RelationshipType current, RelationshipType next) {
            // when & then
            assertThatThrownBy(() -> current.transitionTo(next))
                    .isInstanceOf(InvalidRelationshipTransitionException.class);
        }

        @ParameterizedTest
        @EnumSource(RelationshipType.class)
        @DisplayName("같은 관계로는 전이할 수 없다")
        void transitionToSelfFails(RelationshipType current) {
            // when & then -> 중복 호출을 성공으로 넘기는 것은 애플리케이션이 전이 전에 거른다
            assertThatThrownBy(() -> current.transitionTo(current))
                    .isInstanceOf(InvalidRelationshipTransitionException.class);
        }

        @Test
        @DisplayName("관계가 없는 상태에서 바로 친구가 될 수 없다")
        void noneCannotBecomeFriendDirectly() {
            // when & then -> 친구는 요청을 거쳐서만 된다
            assertThatThrownBy(() -> RelationshipType.NONE.transitionTo(RelationshipType.FRIEND))
                    .isInstanceOf(InvalidRelationshipTransitionException.class);
        }

        @Test
        @DisplayName("차단은 해제로만 풀린다")
        void blockedOnlyMovesToNone() {
            // when & then -> 차단을 풀어도 친구·요청으로 바로 돌아가지 않는다
            assertThat(RelationshipType.BLOCKED.transitionTo(RelationshipType.NONE))
                    .isEqualTo(RelationshipType.NONE);
            assertThatThrownBy(() -> RelationshipType.BLOCKED.transitionTo(RelationshipType.FRIEND))
                    .isInstanceOf(InvalidRelationshipTransitionException.class);
        }
    }
}
