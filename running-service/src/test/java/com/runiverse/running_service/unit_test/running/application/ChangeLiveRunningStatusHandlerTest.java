package com.runiverse.running_service.unit_test.running.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.status.ChangeLiveRunningStatusCommand;
import com.runiverse.running_service.application.running.command.status.ChangeLiveRunningStatusHandler;
import com.runiverse.running_service.application.running.common.LiveRunningStatusChanger;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.MarkRunningComboPausedPort;
import com.runiverse.running_service.application.running.port.out.MarkRunningTrackPausedPort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 화면 참가자 상태 변경 유스케이스 단위 테스트")
class ChangeLiveRunningStatusHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();

    // 판정·발행·실패 처리는 LiveRunningStatusChangerTest가 본다 — 여기서는 그대로 넘기는지만 본다
    @Mock
    private LiveRunningStatusChanger liveRunningStatusChanger;

    @Mock
    private MarkRunningComboPausedPort markRunningComboPausedPort;

    @Mock
    private LoadRunningDistancePort loadRunningDistancePort;

    @Mock
    private MarkRunningTrackPausedPort markRunningTrackPausedPort;

    @InjectMocks
    private ChangeLiveRunningStatusHandler handler;

    private LogCapture log;

    @BeforeEach
    void setUp() {
        log = LogCapture.of(ChangeLiveRunningStatusHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("방·참가자·목표 거리·상태를 그대로 넘긴다")
    void delegatesToChanger() {
        // when
        handler.handle(new ChangeLiveRunningStatusCommand(
                USER_ID, ROOM_ID, 5_000, LiveRunningStatus.PAUSED));

        // then
        verify(liveRunningStatusChanger).change(
                ROOM_ID, new UserId(USER_ID), 5_000, LiveRunningStatus.PAUSED);
    }

    @Test
    @DisplayName("목표 없는 솔로 방이면 목표 거리를 null로 넘긴다")
    void delegatesNullTarget() {
        // when
        handler.handle(new ChangeLiveRunningStatusCommand(
                USER_ID, ROOM_ID, null, LiveRunningStatus.RUNNING));

        // then
        verify(liveRunningStatusChanger).change(
                ROOM_ID, new UserId(USER_ID), null, LiveRunningStatus.RUNNING);
    }

    @Test
    @DisplayName("일시정지면 콤보가 바로 끊기도록 일시정지 표시를 남긴다")
    void marksComboPausedOnPause() {
        // when
        handler.handle(new ChangeLiveRunningStatusCommand(
                USER_ID, ROOM_ID, 5_000, LiveRunningStatus.PAUSED));

        // then
        verify(markRunningComboPausedPort).markPaused(ROOM_ID, new UserId(USER_ID));
    }

    @Test
    @DisplayName("다시 달리기(RESUME)는 일시정지 표시를 건드리지 않는다")
    void doesNotTouchPauseMarkOnResume() {
        // when
        handler.handle(new ChangeLiveRunningStatusCommand(
                USER_ID, ROOM_ID, 5_000, LiveRunningStatus.RUNNING));

        // then -> 다시 달리는 중이 되는 것은 좌표로만이다. 표시는 관측이 들어올 때 판정이 지운다
        verify(markRunningComboPausedPort, never()).markPaused(anyLong(), any());
        verify(markRunningComboPausedPort, never()).clearPaused(anyLong(), any());
    }

    @Test
    @DisplayName("일시정지 표시를 못 남겨도 던지지 않고 ERROR로 남긴다")
    void swallowsPauseMarkFailure() {
        // given -> 좌표를 받기 전이라 트랙 표시는 남기지 않는다. 콤보 표시 실패만 본다
        given(loadRunningDistancePort.loadDistance(ROOM_ID, new UserId(USER_ID)))
                .willReturn(RunningDistance.empty());
        willThrow(new IllegalStateException("redis down"))
                .given(markRunningComboPausedPort).markPaused(ROOM_ID, new UserId(USER_ID));

        // when -> 콤보는 곁가지다. 표시가 없으면 좌표가 멈춰 관측 없음(보류)으로 남는다
        assertThatCode(() -> handler.handle(new ChangeLiveRunningStatusCommand(
                USER_ID, ROOM_ID, 5_000, LiveRunningStatus.PAUSED)))
                .doesNotThrowAnyException();

        // then
        assertThat(log.messages(Level.ERROR)).containsExactly(
                "[러닝] 콤보 일시정지 표시 실패: 처리하지 못한 예외 - roomId=" + ROOM_ID + ", userId=" + USER_ID);
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }
}
