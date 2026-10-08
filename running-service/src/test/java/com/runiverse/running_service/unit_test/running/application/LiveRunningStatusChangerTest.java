package com.runiverse.running_service.unit_test.running.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.common.LiveRunningStatusChanger;
import com.runiverse.running_service.application.running.port.out.ChangeLiveRunningStatusPort;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatusChange;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.PublishRunningProgressPort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.RunningProgress;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.FINISHED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.PAUSED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 화면 참가자 상태 변경 단위 테스트")
class LiveRunningStatusChangerTest {

    private static final long ROOM_ID = 125L;
    private static final int TARGET_DISTANCE_METERS = 5_000;
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UserId USER = new UserId(USER_ID);

    @Mock
    private ChangeLiveRunningStatusPort changeLiveRunningStatusPort;

    @Mock
    private LoadRunningDistancePort loadRunningDistancePort;

    @Mock
    private PublishRunningProgressPort publishRunningProgressPort;

    @InjectMocks
    private LiveRunningStatusChanger changer;

    private LogCapture log;

    @BeforeEach
    void setUp() {
        log = LogCapture.of(LiveRunningStatusChanger.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    private void givenChange(LiveRunningStatus previous, LiveRunningStatus requested) {
        given(changeLiveRunningStatusPort.change(ROOM_ID, USER, requested))
                .willReturn(LiveRunningStatusChange.of(previous, requested));
    }

    private RunningProgress capturePublished() {
        ArgumentCaptor<RunningProgress> captor = ArgumentCaptor.forClass(RunningProgress.class);
        verify(publishRunningProgressPort).publish(eq(ROOM_ID), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("상태가 바뀌면 저장된 거리·페이스와 새 상태로 방에 알린다")
    void publishesWhenChanged() {
        // given
        givenChange(RUNNING, PAUSED);
        given(loadRunningDistancePort.loadDistance(ROOM_ID, USER))
                .willReturn(new RunningDistance(1_980.4, 120L, 35.17955, 129.07564, 352));

        // when
        changer.change(ROOM_ID, USER, TARGET_DISTANCE_METERS, PAUSED);

        // then -> 좌표 배치와 같은 모양이라 클라는 이 메시지 하나로 참가자 최신값을 덮는다
        RunningProgress published = capturePublished();
        assertThat(published.userId()).isEqualTo(USER_ID);
        assertThat(published.distanceMeters()).isEqualTo(1_980);
        assertThat(published.targetDistanceMeters()).isEqualTo(TARGET_DISTANCE_METERS);
        assertThat(published.currentPaceSecondsPerKm()).isEqualTo(352);
        assertThat(published.status()).isEqualTo(PAUSED);
    }

    @Test
    @DisplayName("좌표를 한 번도 못 받은 참가자도 거리 0으로 알린다")
    void publishesZeroDistanceBeforeFirstBatch() {
        // given -> 첫 진입 START는 아직 좌표가 없다. 상대 화면에는 입장 자체를 알려야 한다
        givenChange(null, RUNNING);
        given(loadRunningDistancePort.loadDistance(ROOM_ID, USER)).willReturn(RunningDistance.empty());

        // when
        changer.change(ROOM_ID, USER, TARGET_DISTANCE_METERS, RUNNING);

        // then
        RunningProgress published = capturePublished();
        assertThat(published.distanceMeters()).isZero();
        assertThat(published.currentPaceSecondsPerKm()).isNull();
        assertThat(published.status()).isEqualTo(RUNNING);
    }

    @Test
    @DisplayName("목표 없는 솔로 방은 목표 거리를 null로 싣는다")
    void publishesNullTargetForSoloRoom() {
        // given
        givenChange(RUNNING, PAUSED);
        given(loadRunningDistancePort.loadDistance(ROOM_ID, USER)).willReturn(RunningDistance.empty());

        // when
        changer.change(ROOM_ID, USER, null, PAUSED);

        // then
        assertThat(capturePublished().targetDistanceMeters()).isNull();
    }

    @Test
    @DisplayName("상태가 그대로면 거리를 읽지도 알리지도 않는다")
    void skipsWhenUnchanged() {
        // given -> 같은 PAUSE가 여러 번 와도 상대 화면은 한 번만 바뀐다
        givenChange(PAUSED, PAUSED);

        // when
        changer.change(ROOM_ID, USER, TARGET_DISTANCE_METERS, PAUSED);

        // then
        verifyNoInteractions(loadRunningDistancePort, publishRunningProgressPort);
    }

    @Test
    @DisplayName("FINISHED였으면 끊김을 알리지 않는다")
    void skipsDisconnectAfterFinish() {
        // given -> 종료 ack를 받고 클라가 닫은 연결이 '끊김'으로 퍼지면 안 된다
        givenChange(FINISHED, LiveRunningStatus.DISCONNECTED);

        // when
        changer.change(ROOM_ID, USER, TARGET_DISTANCE_METERS, LiveRunningStatus.DISCONNECTED);

        // then
        verifyNoInteractions(loadRunningDistancePort, publishRunningProgressPort);
    }

    @Test
    @DisplayName("상태 변경이 실패해도 던지지 않고 원인 예외를 담아 ERROR로 남긴다")
    void swallowsChangeFailure() {
        // given -> 표시용 값이라 시작·종료·연결 정리를 막으면 안 된다
        given(changeLiveRunningStatusPort.change(anyLong(), any(), any()))
                .willThrow(new RuntimeException("redis down"));

        // when & then
        assertThatCode(() -> changer.change(ROOM_ID, USER, TARGET_DISTANCE_METERS, PAUSED))
                .doesNotThrowAnyException();
        verify(publishRunningProgressPort, never()).publish(anyLong(), any());
        assertThat(log.messages(Level.ERROR)).containsExactly(
                "[러닝] 참가자 상태 변경 실패: 처리하지 못한 예외 - roomId=" + ROOM_ID
                        + ", userId=" + USER_ID + ", status=PAUSED");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }

    @Test
    @DisplayName("거리 조회가 실패해도 던지지 않고 ERROR로 남긴다")
    void swallowsDistanceLoadFailure() {
        // given -> 상태는 이미 바뀌었다. 알림만 빠지고 다음 배치나 스냅샷이 바로잡는다
        givenChange(RUNNING, PAUSED);
        given(loadRunningDistancePort.loadDistance(anyLong(), any()))
                .willThrow(new RuntimeException("redis down"));

        // when & then
        assertThatCode(() -> changer.change(ROOM_ID, USER, TARGET_DISTANCE_METERS, PAUSED))
                .doesNotThrowAnyException();
        verify(publishRunningProgressPort, never()).publish(anyLong(), any());
        assertThat(log.messages(Level.ERROR)).hasSize(1);
    }
}
