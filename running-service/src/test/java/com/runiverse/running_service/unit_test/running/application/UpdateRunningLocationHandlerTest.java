package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.finish.TrackDistance;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationCommand;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationHandler;
import com.runiverse.running_service.application.running.exception.RunningTrackUnavailableException;
import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.PublishRunningProgressPort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.RunningProgress;
import com.runiverse.running_service.application.running.port.out.SaveRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 위치 업데이트 단위 테스트")
public class UpdateRunningLocationHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 125L;
    private static final int TARGET_DISTANCE_METERS = 5_000;
    private static final double BASE_LATITUDE = 37.5665;
    private static final double BASE_LONGITUDE = 126.9780;
    // 좌표 하나당 위도 증분 — 대략 11m 간격이라 사람이 뛰는 속도와 비슷하다
    private static final double LATITUDE_STEP = 0.0001;

    @Mock
    private AppendRunningTrackPort appendRunningTrackPort;

    @Mock
    private LoadRunningDistancePort loadRunningDistancePort;

    @Mock
    private SaveRunningDistancePort saveRunningDistancePort;

    @Mock
    private PublishRunningProgressPort publishRunningProgressPort;

    @InjectMocks
    private UpdateRunningLocationHandler updateRunningLocationHandler;

    @BeforeEach
    void setUp() {
        // 좌표를 한 번도 못 받은 상태가 기본이다 — 첫 배치도 같은 경로로 흐른다.
        // 적재 실패 테스트는 여기까지 오지도 않으므로 lenient로 둔다
        lenient().when(loadRunningDistancePort.loadDistance(anyLong(), any()))
                .thenReturn(RunningDistance.empty());
    }

    private static TrackPoint trackPoint(long sequence) {
        return trackPoint(sequence, 357);
    }

    // 순번이 커질수록 북쪽으로 일정하게 나아간다 — 누적 거리를 예측할 수 있게 한다
    private static TrackPoint trackPoint(long sequence, Integer paceSecondsPerKm) {
        return new TrackPoint(
                sequence,
                BASE_LATITUDE + sequence * LATITUDE_STEP,
                BASE_LONGITUDE,
                38.5,
                4.2,
                2.8,
                181.0,
                174,
                paceSecondsPerKm,
                LocalDateTime.of(2026, 8, 25, 7, 30, (int) sequence));
    }

    private UpdateRunningLocationCommand command(List<TrackPoint> points) {
        return new UpdateRunningLocationCommand(
                USER_ID, ROOM_ID, TARGET_DISTANCE_METERS, points);
    }

    private RunningDistance captureSaved() {
        ArgumentCaptor<RunningDistance> captor = ArgumentCaptor.forClass(RunningDistance.class);
        verify(saveRunningDistancePort).saveDistance(eq(ROOM_ID), eq(new UserId(USER_ID)),
                captor.capture());
        return captor.getValue();
    }

    private RunningProgress capturePublished() {
        ArgumentCaptor<RunningProgress> captor = ArgumentCaptor.forClass(RunningProgress.class);
        verify(publishRunningProgressPort).publish(eq(ROOM_ID), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("받은 좌표를 순서 그대로 트랙에 넘긴다")
    void appendsPointsInOrder() {
        // given
        List<TrackPoint> points = List.of(trackPoint(1L), trackPoint(2L), trackPoint(3L));

        // when
        updateRunningLocationHandler.handle(command(points));

        // then
        verify(appendRunningTrackPort).append(ROOM_ID, new UserId(USER_ID), points);
    }

    @Test
    @DisplayName("좌표가 비어 있어도 트랙 적재를 호출한다")
    void appendsEvenWhenPointsAreEmpty() {
        // given -> 빈 배치를 걸러내는 책임은 핸들러가 아니라 요청 검증에 있다
        // when
        updateRunningLocationHandler.handle(command(List.of()));

        // then
        verify(appendRunningTrackPort).append(eq(ROOM_ID), eq(new UserId(USER_ID)), anyList());
    }

    @Test
    @DisplayName("첫 배치는 배치 안 구간만 누적하고 마지막 좌표를 남긴다")
    void accumulatesWithinFirstBatch() {
        // given -> 직전 좌표가 없으므로 첫 좌표는 거리에 기여하지 않는다
        List<TrackPoint> points = List.of(trackPoint(0L), trackPoint(1L), trackPoint(2L));

        // when
        updateRunningLocationHandler.handle(command(points));

        // then
        double expected = TrackDistance.between(points.get(0), points.get(1))
                + TrackDistance.between(points.get(1), points.get(2));
        RunningDistance saved = captureSaved();
        assertThat(saved.meters()).isEqualTo(expected, org.assertj.core.data.Offset.offset(0.01));
        // 다음 배치가 이 좌표에서 이어붙이므로 마지막 값이 남아야 한다
        assertThat(saved.lastSequence()).isEqualTo(2L);
        assertThat(saved.lastLatitude()).isEqualTo(points.get(2).latitude());
        assertThat(saved.lastLongitude()).isEqualTo(points.get(2).longitude());
    }

    @Test
    @DisplayName("배치와 배치 사이 구간도 이어서 누적한다")
    void accumulatesAcrossBatches() {
        // given -> 직전 배치의 마지막 좌표가 순번 2였다
        TrackPoint previous = trackPoint(2L);
        given(loadRunningDistancePort.loadDistance(anyLong(), any())).willReturn(
                new RunningDistance(100.0, 2L, previous.latitude(), previous.longitude()));
        List<TrackPoint> points = List.of(trackPoint(3L), trackPoint(4L));

        // when
        updateRunningLocationHandler.handle(command(points));

        // then -> 이 구간이 빠지면 10초마다 한 칸씩 거리가 새어나간다
        double expected = 100.0
                + TrackDistance.between(previous, points.get(0))
                + TrackDistance.between(points.get(0), points.get(1));
        assertThat(captureSaved().meters())
                .isEqualTo(expected, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("이미 반영한 순번은 다시 더하지 않는다")
    void skipsAlreadyAccumulatedSequences() {
        // given -> 재연결하면 클라는 로컬 트랙 전체를 순번 0부터 다시 보낸다(api-spec 5-D).
        // 그대로 더하면 거리가 두 배가 된다
        TrackPoint previous = trackPoint(2L);
        RunningDistance stored =
                new RunningDistance(100.0, 2L, previous.latitude(), previous.longitude());
        given(loadRunningDistancePort.loadDistance(anyLong(), any())).willReturn(stored);

        // when -> 0,1,2는 이미 반영된 순번이다
        updateRunningLocationHandler.handle(
                command(List.of(trackPoint(0L), trackPoint(1L), trackPoint(2L))));

        // then
        RunningDistance saved = captureSaved();
        assertThat(saved.meters()).isEqualTo(100.0);
        assertThat(saved.lastSequence()).isEqualTo(2L);
    }

    @Test
    @DisplayName("진행 정보를 방 채널로 발행한다")
    void publishesProgress() {
        // given
        List<TrackPoint> points = List.of(trackPoint(0L), trackPoint(1L));

        // when
        updateRunningLocationHandler.handle(command(points));

        // then
        RunningProgress published = capturePublished();
        assertThat(published.userId()).isEqualTo(USER_ID);
        assertThat(published.targetDistanceMeters()).isEqualTo(TARGET_DISTANCE_METERS);
        assertThat(published.distanceMeters()).isEqualTo(captureSaved().metersRounded());
        // RUNNING_PAUSE/RESUME 구현 전까지 항상 false다(api-spec 5-D)
        assertThat(published.paused()).isFalse();
    }

    @Test
    @DisplayName("페이스는 마지막 좌표의 값을 싣는다")
    void publishesPaceOfLatestPoint() {
        // given -> 배치가 뒤섞여 와도 순번이 가장 큰 좌표가 최신이다
        List<TrackPoint> points = List.of(trackPoint(1L, 340), trackPoint(0L, 400));

        // when
        updateRunningLocationHandler.handle(command(points));

        // then
        assertThat(capturePublished().currentPaceSecondsPerKm()).isEqualTo(340);
    }

    @Test
    @DisplayName("단말이 페이스를 못 재면 null로 싣는다")
    void publishesNullPaceWhenDeviceCannotMeasure() {
        // given -> 0으로 채우면 받는 쪽이 멈춘 것으로 읽는다
        // when
        updateRunningLocationHandler.handle(command(List.of(trackPoint(0L, null))));

        // then
        assertThat(capturePublished().currentPaceSecondsPerKm()).isNull();
    }

    @Test
    @DisplayName("목표 없는 방이면 목표 거리를 null로 싣는다")
    void publishesNullTargetForRoomWithoutGoal() {
        // given -> 솔로 방은 target_distance가 nullable이다(erd.md)
        // when
        updateRunningLocationHandler.handle(
                new UpdateRunningLocationCommand(USER_ID, ROOM_ID, null, List.of(trackPoint(0L))));

        // then
        assertThat(capturePublished().targetDistanceMeters()).isNull();
    }

    @Test
    @DisplayName("좌표 저장이 실패하면 누적도 발행도 하지 않는다")
    void skipsProgressWhenAppendFails() {
        // given -> 저장 안 된 좌표로 남의 화면에 진행을 알리면 안 된다
        willThrow(new RunningTrackUnavailableException())
                .given(appendRunningTrackPort).append(anyLong(), any(), anyList());

        // when & then -> 예외는 그대로 나가야 클라가 ERROR를 받는다(api-spec 5-D)
        assertThatThrownBy(() -> updateRunningLocationHandler.handle(
                command(List.of(trackPoint(0L)))))
                .isInstanceOf(RunningTrackUnavailableException.class);
        verify(loadRunningDistancePort, never()).loadDistance(anyLong(), any());
        verify(saveRunningDistancePort, never()).saveDistance(anyLong(), any(), any());
        verify(publishRunningProgressPort, never()).publish(anyLong(), any());
    }
}
