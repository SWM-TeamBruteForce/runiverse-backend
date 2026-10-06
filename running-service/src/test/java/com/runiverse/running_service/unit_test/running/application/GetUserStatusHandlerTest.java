package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.port.out.MatchCooldownPort;
import com.runiverse.running_service.application.running.port.out.LoadUserStatusPort;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.application.running.query.status.GetUserStatusHandler;
import com.runiverse.running_service.application.running.query.status.GetUserStatusQuery;
import com.runiverse.running_service.application.running.query.status.GetUserStatusResult;
import com.runiverse.running_service.application.running.query.status.UserRunningStatus;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

// 판정 규칙은 UserStatusResolver가 맡고, 여기서는 조립을 본다 —
// 쿨다운을 함께 싣는지, 활동 중이 아닐 때 방 값을 비우는지
@ExtendWith(MockitoExtension.class)
@DisplayName("유저 상태 조회 단위 테스트")
class GetUserStatusHandlerTest {

    private static final LocalDateTime START_AT = LocalDateTime.of(2099, 9, 11, 19, 0);
    // 실제 application.properties와 같은 값으로 맞춘다
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofHours(6),
            10, Duration.ofMinutes(20));

    @Mock
    private LoadUserStatusPort loadUserStatusPort;

    @Mock
    private MatchCooldownPort matchCooldownPort;

    private GetUserStatusHandler handler;
    private UUID userId;

    @BeforeEach
    void setUp() {
        handler = new GetUserStatusHandler(
                loadUserStatusPort, matchCooldownPort, MATCH_PROPERTIES);
        userId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("활성 신청이 없으면 아무것도 하지 않는 상태다")
    void noActiveApplicationIsIdle() {
        // given
        given(loadUserStatusPort.loadStatus(any(UserId.class))).willReturn(Optional.empty());
        given(matchCooldownPort.until(any(UserId.class))).willReturn(Optional.empty());

        // when
        GetUserStatusResult result = handler.handle(new GetUserStatusQuery(userId));

        // then -> 방에 딸린 값은 전부 비어야 한다
        assertThat(result.status()).isEqualTo(UserRunningStatus.IDLE);
        assertThat(result.type()).isNull();
        assertThat(result.runningRoomId()).isNull();
        assertThat(result.scheduledStartAt()).isNull();
        assertThat(result.targetDistanceMeters()).isNull();
    }

    @Test
    @DisplayName("활동 중이 아니어도 남은 쿨다운은 실어 보낸다")
    void idleCarriesCooldown() {
        // given -> 제재 이탈로 재신청이 막힌 상태. 클라가 버튼에 남은 시간을 띄운다
        LocalDateTime until = LocalDateTime.of(2099, 9, 11, 12, 45);
        given(loadUserStatusPort.loadStatus(any(UserId.class))).willReturn(Optional.empty());
        given(matchCooldownPort.until(any(UserId.class))).willReturn(Optional.of(until));

        // when
        GetUserStatusResult result = handler.handle(new GetUserStatusQuery(userId));

        // then
        assertThat(result.status()).isEqualTo(UserRunningStatus.IDLE);
        assertThat(result.cooldownUntil()).isEqualTo(until);
    }

    @Test
    @DisplayName("확정된 방이 있으면 방 값을 그대로 싣는다")
    void matchedRoomIsCarried() {
        // given
        given(loadUserStatusPort.loadStatus(any(UserId.class))).willReturn(Optional.of(
                new UserStatusRow(125L, RunningRoomType.MATCH,
                        RunningRoomStatus.MATCHED, START_AT, 5_000)));
        given(matchCooldownPort.until(any(UserId.class))).willReturn(Optional.empty());

        // when
        GetUserStatusResult result = handler.handle(new GetUserStatusQuery(userId));

        // then
        assertThat(result.status()).isEqualTo(UserRunningStatus.READY);
        assertThat(result.type()).isEqualTo(RunningRoomType.MATCH);
        assertThat(result.runningRoomId()).isEqualTo(125L);
        assertThat(result.scheduledStartAt()).isEqualTo(START_AT);
        assertThat(result.targetDistanceMeters()).isEqualTo(5_000);
    }

    @Test
    @DisplayName("목표 없는 솔로 방은 목표 거리가 비어 나간다")
    void soloRoomHasNoTargetDistance() {
        // given -> 방의 target_distance가 정본이다. 참가자 쪽은 NOT NULL이라 값이 들어간다
        given(loadUserStatusPort.loadStatus(any(UserId.class))).willReturn(Optional.of(
                new UserStatusRow(126L, RunningRoomType.SOLO,
                        RunningRoomStatus.STARTED, START_AT, null)));
        given(matchCooldownPort.until(any(UserId.class))).willReturn(Optional.empty());

        // when
        GetUserStatusResult result = handler.handle(new GetUserStatusQuery(userId));

        // then
        assertThat(result.status()).isEqualTo(UserRunningStatus.RUNNING);
        assertThat(result.type()).isEqualTo(RunningRoomType.SOLO);
        assertThat(result.targetDistanceMeters()).isNull();
    }

    @Test
    @DisplayName("닫힌 방이 남아 있으면 방 값을 비워 내보낸다")
    void closedRoomClearsRoomFields() {
        // given -> 종료·취소는 deleted_at을 함께 찍으므로 조회에 잡히지 않는 것이 정상이다.
        // 어긋난 데이터를 그대로 실으면 클라가 이미 닫힌 방에 붙으러 간다
        given(loadUserStatusPort.loadStatus(any(UserId.class))).willReturn(Optional.of(
                new UserStatusRow(125L, RunningRoomType.MATCH,
                        RunningRoomStatus.FINISHED, START_AT, 5_000)));
        given(matchCooldownPort.until(any(UserId.class))).willReturn(Optional.empty());

        // when
        GetUserStatusResult result = handler.handle(new GetUserStatusQuery(userId));

        // then
        assertThat(result.status()).isEqualTo(UserRunningStatus.IDLE);
        assertThat(result.runningRoomId()).isNull();
        assertThat(result.type()).isNull();
    }
}
