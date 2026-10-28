package com.runiverse.running_service.unit_test.match.application;

import com.runiverse.running_service.application.common.exception.InviteErrorCode;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.exception.InvalidInviteOptionException;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 초대 방 개설이 이 판정으로 400·409를 가른다 — 문구가 응답 메시지로 그대로 나간다
@DisplayName("초대 방 시작 시각·거리 정책 단위 테스트")
class InviteStartPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 15, 0);
    private static final LocalDateTime VALID_START = NOW.plusHours(1);
    private static final int VALID_DISTANCE = 7_000;
    private static final Duration MIN_LEAD_TIME = Duration.ofMinutes(10);
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(1);
    private static final Duration READY_OFFSET = Duration.ofSeconds(10);
    // 400 응답 문구
    private static final String START_AT_MESSAGE = "시작 시각은 분 단위로만 선택할 수 있습니다.";
    private static final String DISTANCE_MESSAGE = "목표 거리는 1000m 이상 42195m 이하여야 합니다.";

    private static InviteStartPolicy policyOf(Duration closeOffset, Duration readyOffset) {
        InviteProperties inviteProperties = new InviteProperties(MIN_LEAD_TIME, closeOffset, 1_000, 42_195, 5);
        MatchProperties matchProperties = new MatchProperties(
                Duration.ofMinutes(10), readyOffset, Duration.ofHours(6), 10, Duration.ofMinutes(20),
                LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
        return new InviteStartPolicy(inviteProperties, matchProperties);
    }

    private final InviteStartPolicy policy = policyOf(CLOSE_OFFSET, READY_OFFSET);

    @Nested
    @DisplayName("요청 값 검증 테스트")
    class ValidateTest {

        @Test
        @DisplayName("분 단위 시각과 범위 안 거리는 통과한다")
        void validOptionPasses() {
            // when & then
            assertThatCode(() -> policy.validate(VALID_START, VALID_DISTANCE)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0}m")
        @ValueSource(ints = {1_000, 42_195})
        @DisplayName("거리 범위의 양 끝은 받는다")
        void distanceBoundsAreInclusive(int distanceMeters) {
            // when & then
            assertThatCode(() -> policy.validate(VALID_START, distanceMeters)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0}m")
        @ValueSource(ints = {999, 42_196})
        @DisplayName("범위 밖 거리는 운영값으로 만든 문구와 함께 400으로 거절한다")
        void distanceOutOfRangeIsRejected(int distanceMeters) {
            // when & then
            assertThatThrownBy(() -> policy.validate(VALID_START, distanceMeters))
                    .isInstanceOf(InvalidInviteOptionException.class)
                    .hasMessage(DISTANCE_MESSAGE)
                    .extracting("errorCode").isEqualTo(InviteErrorCode.INVALID_INVITE_OPTION);
        }

        @Test
        @DisplayName("초가 붙은 시각은 거절한다")
        void secondsAreRejected() {
            // when & then
            assertThatThrownBy(() -> policy.validate(VALID_START.withSecond(30), VALID_DISTANCE))
                    .isInstanceOf(InvalidInviteOptionException.class)
                    .hasMessage(START_AT_MESSAGE);
        }

        @Test
        @DisplayName("초 아래 단위가 붙은 시각도 거절한다")
        void nanosAreRejected() {
            // when & then -> 직렬화 형식에 따라 초는 0인데 나노초만 남을 수 있다
            assertThatThrownBy(() -> policy.validate(VALID_START.withNano(1), VALID_DISTANCE))
                    .isInstanceOf(InvalidInviteOptionException.class)
                    .hasMessage(START_AT_MESSAGE);
        }

        @Test
        @DisplayName("둘 다 어긋나면 두 문장을 한 번에 보낸다")
        void bothViolationsAreReportedTogether() {
            // when & then
            assertThatThrownBy(() -> policy.validate(VALID_START.withSecond(30), 500))
                    .isInstanceOf(InvalidInviteOptionException.class)
                    .hasMessage(START_AT_MESSAGE + " " + DISTANCE_MESSAGE);
        }
    }

    @Nested
    @DisplayName("시작 시각 범위 테스트")
    class SelectableTest {

        @Test
        @DisplayName("지금 + 최소 준비 시간은 고를 수 있다")
        void earliestIsInclusive() {
            // when & then
            assertThat(policy.isSelectable(NOW.plus(MIN_LEAD_TIME), NOW)).isTrue();
        }

        @Test
        @DisplayName("최소 준비 시간보다 가까운 시각은 고를 수 없다")
        void tooSoonIsRejected() {
            // when & then
            assertThat(policy.isSelectable(NOW.plusMinutes(9), NOW)).isFalse();
        }

        @Test
        @DisplayName("지금 초가 지났으면 같은 분의 10분 뒤는 이미 너무 가깝다")
        void secondsOfNowCount() {
            // given -> 15:00:30에 15:10을 고르면 준비 시간이 9분 30초다
            LocalDateTime now = NOW.withSecond(30);

            // when & then
            assertThat(policy.isSelectable(NOW.plusMinutes(10), now)).isFalse();
        }

        @Test
        @DisplayName("오늘 23:59는 고를 수 있다")
        void lastStartTimeIsInclusive() {
            // when & then
            assertThat(policy.isSelectable(NOW.toLocalDate().atTime(23, 59), NOW)).isTrue();
        }

        @Test
        @DisplayName("다음 날 0시는 고를 수 없다")
        void tomorrowIsRejected() {
            // when & then
            assertThat(policy.isSelectable(NOW.toLocalDate().plusDays(1).atStartOfDay(), NOW)).isFalse();
        }

        @Test
        @DisplayName("23:49가 지나면 고를 수 있는 시각이 없다")
        void nothingSelectableLateAtNight() {
            // given
            LocalDateTime now = NOW.toLocalDate().atTime(23, 50);

            // when & then -> 하한(다음 날 0시)이 상한(23:59)을 넘는다
            assertThat(policy.isSelectable(now.toLocalDate().atTime(23, 59), now)).isFalse();
            assertThat(policy.isSelectable(now.plus(MIN_LEAD_TIME), now)).isFalse();
        }

        @Test
        @DisplayName("지난 시각은 고를 수 없다")
        void pastIsRejected() {
            // when & then
            assertThat(policy.isSelectable(NOW.minusMinutes(1), NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("모집 중 판정 테스트")
    class RecruitingTest {

        private RunningRoom roomOf(RunningRoomStatus status) {
            return RunningRoom.builder()
                    .runningRoomId(130L)
                    .type(RunningRoomType.INVITE)
                    .status(status)
                    .startAt(VALID_START)
                    .closeAt(status.isTerminal() ? NOW : null)
                    .targetDistance(VALID_DISTANCE)
                    .avgPace(330)
                    .currentPlayerCount(status == RunningRoomStatus.CANCELLED ? 0 : 1)
                    .maxPlayerCount(4)
                    .build();
        }

        @Test
        @DisplayName("모집 중이고 마감 전이면 받는다")
        void recruitingBeforeCloseAt() {
            // when & then
            assertThat(policy.isRecruiting(roomOf(RunningRoomStatus.MATCHING), NOW)).isTrue();
        }

        @Test
        @DisplayName("마감 1나노초 전까지는 받는다")
        void justBeforeCloseAt() {
            // when & then
            assertThat(policy.isRecruiting(roomOf(RunningRoomStatus.MATCHING),
                    policy.closeAt(VALID_START).minusNanos(1))).isTrue();
        }

        @Test
        @DisplayName("마감 시각 자체는 이미 닫혔다")
        void closeAtIsExclusive() {
            // when & then -> 초대·수락이 같은 경계를 쓴다
            assertThat(policy.isRecruiting(roomOf(RunningRoomStatus.MATCHING), policy.closeAt(VALID_START)))
                    .isFalse();
        }

        @Test
        @DisplayName("마감 예약이 안 돌아 모집 중으로 남아 있어도 시각이 지났으면 닫혔다")
        void pastCloseAtWhileMatching() {
            // when & then
            assertThat(policy.isRecruiting(roomOf(RunningRoomStatus.MATCHING), VALID_START)).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RunningRoomStatus.class, names = "MATCHING", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("모집 중이 아닌 방은 마감 전이어도 닫혔다")
        void notMatchingIsClosed(RunningRoomStatus status) {
            // when & then
            assertThat(policy.isRecruiting(roomOf(status), NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("모집 마감·설정 검증 테스트")
    class CloseAtTest {

        @Test
        @DisplayName("모집 마감은 시작 시각에서 초대 마감 오프셋을 뺀 시각이다")
        void closeAtIsStartMinusOffset() {
            // when & then
            assertThat(policy.closeAt(VALID_START)).isEqualTo(VALID_START.minusMinutes(1));
        }

        @Test
        @DisplayName("초대 마감이 시작 통지와 같으면 기동하지 않는다")
        void closeOffsetEqualToReadyOffsetFails() {
            // when & then -> 마감과 통지가 겹치면 그 사이 입장자가 통지를 놓칠 수 있다
            assertThatThrownBy(() -> policyOf(Duration.ofSeconds(10), Duration.ofSeconds(10)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("초대 마감이 시작 통지보다 늦으면 기동하지 않는다")
        void closeOffsetShorterThanReadyOffsetFails() {
            // when & then
            assertThatThrownBy(() -> policyOf(Duration.ofSeconds(5), Duration.ofSeconds(10)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
