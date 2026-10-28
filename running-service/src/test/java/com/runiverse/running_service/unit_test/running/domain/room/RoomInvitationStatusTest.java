package com.runiverse.running_service.unit_test.running.domain.room;

import com.runiverse.running_service.domain.running.room.exception.InvalidInvitationStatusTransitionException;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RoomInvitationStatusTest {

    // 명세로서의 전이표 — 구현과 따로 적어 둔다. 둘이 어긋나면 테스트가 깨진다.
    private static final Map<RoomInvitationStatus, Set<RoomInvitationStatus>> ALLOWED =
            new EnumMap<>(Map.of(
                    RoomInvitationStatus.PENDING,
                    EnumSet.of(RoomInvitationStatus.ACCEPTED,
                            RoomInvitationStatus.DECLINED,
                            RoomInvitationStatus.EXPIRED),
                    RoomInvitationStatus.ACCEPTED,
                    EnumSet.of(RoomInvitationStatus.LEFT),
                    RoomInvitationStatus.DECLINED,
                    EnumSet.of(RoomInvitationStatus.PENDING),
                    RoomInvitationStatus.LEFT,
                    EnumSet.of(RoomInvitationStatus.PENDING),
                    RoomInvitationStatus.EXPIRED,
                    EnumSet.noneOf(RoomInvitationStatus.class)));

    private static Stream<Arguments> allowedPairs() {
        return ALLOWED.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(next -> Arguments.of(entry.getKey(), next)));
    }

    private static Stream<Arguments> deniedPairs() {
        return Arrays.stream(RoomInvitationStatus.values())
                .flatMap(current -> Arrays.stream(RoomInvitationStatus.values())
                        .filter(next -> !ALLOWED.get(current).contains(next))
                        .map(next -> Arguments.of(current, next)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allowedPairs")
    @DisplayName("전이표에 있는 조합은 다음 상태를 돌려준다")
    void transitionToAllowedStatusSuccess(RoomInvitationStatus current,
                                          RoomInvitationStatus next) {
        // when & then
        assertThat(current.transitionTo(next)).isEqualTo(next);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("deniedPairs")
    @DisplayName("전이표에 없는 조합은 예외가 발생한다")
    void transitionToDeniedStatusFails(RoomInvitationStatus current,
                                       RoomInvitationStatus next) {
        // when & then -> 자기 자신으로의 전이도 막힌다(대기 중에 다시 보내도 상태는 그대로라 전이가 아니다)
        assertThatThrownBy(() -> current.transitionTo(next))
                .isInstanceOf(InvalidInvitationStatusTransitionException.class);
    }
}
