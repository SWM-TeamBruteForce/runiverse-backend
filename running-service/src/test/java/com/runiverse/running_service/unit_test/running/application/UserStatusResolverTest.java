package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.application.running.query.status.UserRunningStatus;
import com.runiverse.running_service.application.running.query.status.UserStatusResolver;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 유저 상태는 저장값이 아니라 파생값이다. 방 상태 5종을 클라이언트의 다음 동작 4종으로
// 접는 규칙이 전부 여기 있고, now를 인자로 받아 시계 없이 검증한다
@DisplayName("유저 상태 판정 단위 테스트")
class UserStatusResolverTest {

    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 9, 11, 19, 0);
    // 실제 application.properties와 같은 값으로 맞춘다
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(10);
    // 모집 마감 = start_at - 오프셋. 저장하지 않고 매번 계산한다
    private static final LocalDateTime CLOSE_AT = START_AT.minus(CLOSE_OFFSET);

    @Test
    @DisplayName("모집 중이고 마감 전이면 매칭을 기다리는 중이다")
    void matchingBeforeCloseIsWaiting() {
        // given -> 마감 1초 전
        LocalDateTime now = CLOSE_AT.minusSeconds(1);

        // when & then
        assertThat(resolve(RunningRoomStatus.MATCHING, now)).isEqualTo(UserRunningStatus.WAITING);
    }

    @Test
    @DisplayName("모집 중이어도 마감이 지났으면 확정으로 본다")
    void matchingAfterCloseIsReady() {
        // given -> 확정 예약이 아직 깨지 않은 틈. 방은 MATCHING이지만 곧 확정될 자리다
        LocalDateTime now = CLOSE_AT.plusSeconds(1);

        // when & then -> 대기 화면을 그리게 두면 잠시 뒤 화면이 다시 바뀐다
        assertThat(resolve(RunningRoomStatus.MATCHING, now)).isEqualTo(UserRunningStatus.READY);
    }

    @Test
    @DisplayName("마감 시각 정각은 이미 마감이다")
    void matchingAtCloseIsReady() {
        // given -> 경계. 신청 거절 판정(!now.isBefore(closeAt))과 같은 기준이어야 한다

        // when & then
        assertThat(resolve(RunningRoomStatus.MATCHING, CLOSE_AT)).isEqualTo(UserRunningStatus.READY);
    }

    @Test
    @DisplayName("확정된 방은 시작을 기다리는 중이다")
    void matchedIsReady() {
        // given & when & then -> 인원과 무관하다. 1인 확정도 여기 해당한다
        assertThat(resolve(RunningRoomStatus.MATCHED, START_AT.minusMinutes(1)))
                .isEqualTo(UserRunningStatus.READY);
    }

    @Test
    @DisplayName("시작한 방은 달리는 중이다")
    void startedIsRunning() {
        // given & when & then
        assertThat(resolve(RunningRoomStatus.STARTED, START_AT.plusMinutes(11)))
                .isEqualTo(UserRunningStatus.RUNNING);
    }

    @Test
    @DisplayName("시작한 방은 마감 시각과 무관하게 달리는 중이다")
    void startedIgnoresCloseAt() {
        // given -> 마감을 훨씬 지난 시각에도 시작 여부가 우선이다
        assertThat(resolve(RunningRoomStatus.STARTED, CLOSE_AT.minusHours(1)))
                .isEqualTo(UserRunningStatus.RUNNING);
    }

    @Test
    @DisplayName("닫힌 방이 남아 있어도 활동 중으로 보지 않는다")
    void closedRoomIsIdle() {
        // given -> 종료·취소는 deleted_at을 함께 찍으므로 조회에 잡히지 않는 것이 정상이다.
        // 어긋난 데이터가 조회를 깨뜨리지 않게 떨어뜨릴 자리를 둔다
        assertThat(resolve(RunningRoomStatus.FINISHED, START_AT)).isEqualTo(UserRunningStatus.IDLE);
        assertThat(resolve(RunningRoomStatus.CANCELLED, START_AT)).isEqualTo(UserRunningStatus.IDLE);
    }

    @Test
    @DisplayName("솔로 방도 같은 규칙으로 판정한다")
    void soloUsesSameRules() {
        // given -> 솔로는 모집 없이 MATCHED로 태어나고 start_at이 개시 시각(과거)이다.
        // 방 종류로 분기하지 않는다 — 클라이언트가 type을 보고 채널을 고른다
        UserStatusRow solo = new UserStatusRow(
                126L, RunningRoomType.SOLO, RunningRoomStatus.MATCHED, START_AT, null);

        // when & then
        assertThat(UserStatusResolver.resolve(solo, START_AT.plusMinutes(1), CLOSE_OFFSET))
                .isEqualTo(UserRunningStatus.READY);
    }

    private static UserRunningStatus resolve(RunningRoomStatus roomStatus, LocalDateTime now) {
        UserStatusRow row = new UserStatusRow(
                125L, RunningRoomType.MATCH, roomStatus, START_AT, 5_000);
        return UserStatusResolver.resolve(row, now, CLOSE_OFFSET);
    }
}
