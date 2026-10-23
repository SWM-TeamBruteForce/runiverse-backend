package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.command.combo.RunningComboProperties;
import com.runiverse.running_service.application.running.common.BoundaryPoint;
import com.runiverse.running_service.application.running.common.PolylineEncoder;
import com.runiverse.running_service.application.running.common.RunningComboArchive;
import com.runiverse.running_service.application.running.common.RunningComboWriter;
import com.runiverse.running_service.application.running.port.out.CreateRunningCombosPort;
import com.runiverse.running_service.application.running.port.out.LoadRecordedRoutesPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboHistoryPort;
import com.runiverse.running_service.application.running.port.out.RecordedRoute;
import com.runiverse.running_service.application.running.port.out.RecordedSplitTime;
import com.runiverse.running_service.application.running.port.out.RunningComboHistory;
import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboSegment;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.combo.RunningCombo;
import com.runiverse.running_service.domain.running.combo.vo.ComboPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 콤보 저장 단위 테스트")
class RunningComboWriterTest {

    private static final long ROOM_ID = 12L;
    // UUID 순서가 쌍의 first/second를 정한다 — A < B < C
    private static final UserId A = new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000a1"));
    private static final UserId B = new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000b2"));
    private static final UserId C = new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000c3"));
    private static final Instant T0 = Instant.parse("2026-10-13T07:00:00Z");
    // 1회 10초 — 실제 설정과 같다
    private static final RunningComboProperties PROPERTIES = new RunningComboProperties(
            30, 30, Duration.ofSeconds(19), Duration.ofSeconds(10), 1);
    // A는 3초마다 경계점을 지나 200초 동안 달렸다
    private static final int SECONDS_PER_POINT = 3;
    private static final int POINT_COUNT = 67;

    @Mock
    private LoadRunningComboHistoryPort loadRunningComboHistoryPort;

    @Mock
    private LoadRecordedRoutesPort loadRecordedRoutesPort;

    @Mock
    private CreateRunningCombosPort createRunningCombosPort;

    private RunningComboWriter writer;

    @BeforeEach
    void setUp() {
        writer = new RunningComboWriter(
                loadRunningComboHistoryPort, loadRecordedRoutesPort, createRunningCombosPort, PROPERTIES);
    }

    private static Instant at(long seconds) {
        return T0.plusSeconds(seconds);
    }

    private static LocalDateTime local(long seconds) {
        return LocalDateTime.ofInstant(at(seconds), ZoneId.systemDefault());
    }

    private static double latitudeOf(int index) {
        return 37.5 + index * 0.0001;
    }

    // A의 기록 경로 — p_i를 i × 3초에 지났다
    private static RecordedRoute routeOfA() {
        List<BoundaryPoint> points = new ArrayList<>();
        List<RecordedSplitTime> splits = new ArrayList<>();
        for (int i = 0; i < POINT_COUNT; i++) {
            points.add(new BoundaryPoint(i * 10, latitudeOf(i), 127.0,
                    local((long) i * SECONDS_PER_POINT), i * SECONDS_PER_POINT, i));
            if (i > 0) {
                splits.add(new RecordedSplitTime(1, i - 1, i,
                        local((long) (i - 1) * SECONDS_PER_POINT), local((long) i * SECONDS_PER_POINT)));
            }
        }
        return new RecordedRoute(A.value(), List.of(PolylineEncoder.encode(points)), splits);
    }

    private List<RunningCombo> captureCreated() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RunningCombo>> captor = ArgumentCaptor.forClass(List.class);
        verify(createRunningCombosPort).createAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("아무와도 함께 달리지 않은 방은 경로도 읽지 않고 아무것도 넣지 않는다")
    void skipsEmptyRoom() {
        // given
        given(loadRunningComboHistoryPort.loadHistory(ROOM_ID))
                .willReturn(new RunningComboHistory(List.of(), List.of()));

        // when
        RunningComboArchive archive = writer.write(ROOM_ID);

        // then
        assertThat(archive).isEqualTo(RunningComboArchive.empty());
        verify(loadRecordedRoutesPort, never()).loadRoutes(anyLong());
        verify(createRunningCombosPort, never()).createAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("끊긴 구간과 이어지던 관계를 함께 넣고, 쌍마다 시작 시각 순으로 번호를 매긴다")
    void numbersSegmentsByStartAt() {
        // given -> 끊긴 구간이 더 늦게 시작했다. 이어지던 관계는 쌓인 시각(70초)에서 닫는다
        RunningComboSegment closed = new RunningComboSegment(A, B, at(110), at(180), 7);
        RunningComboPair open = new RunningComboPair(A, B, at(20), at(70), 7, 1);
        given(loadRunningComboHistoryPort.loadHistory(ROOM_ID))
                .willReturn(new RunningComboHistory(List.of(closed), List.of(open)));
        given(loadRecordedRoutesPort.loadRoutes(ROOM_ID)).willReturn(List.of(routeOfA()));

        // when
        RunningComboArchive archive = writer.write(ROOM_ID);

        // then
        assertThat(archive).isEqualTo(new RunningComboArchive(1, 1, 2));
        List<RunningCombo> created = captureCreated();
        assertThat(created).extracting(combo -> combo.getSegmentNumber().value()).containsExactlyInAnyOrder(1, 2);
        RunningCombo first = created.stream()
                .filter(combo -> combo.getSegmentNumber().value() == 1).findFirst().orElseThrow();
        assertThat(first.getPeriod().startAt()).isEqualTo(local(20));
        assertThat(first.getPeriod().endAt()).isEqualTo(local(70));
        assertThat(first.getComboCount().value()).isEqualTo(5);
        assertThat(first.getFirstUserId()).isEqualTo(A);
        assertThat(first.getSecondUserId()).isEqualTo(B);
    }

    @Test
    @DisplayName("끊긴 구간을 저장한 뒤 관계를 못 지운 채 닫히면 같은 구간을 한 번만 넣는다")
    void writesSegmentOnceWhenPairWasNotCleared() {
        // given -> 끊긴 구간은 저장됐는데 관계 저장이 실패해 같은 관계가 이어지던 채로 남았다
        RunningComboSegment closed = new RunningComboSegment(A, B, at(20), at(70), 5);
        RunningComboPair open = new RunningComboPair(A, B, at(20), at(70), 5, 1);
        given(loadRunningComboHistoryPort.loadHistory(ROOM_ID))
                .willReturn(new RunningComboHistory(List.of(closed), List.of(open)));
        given(loadRecordedRoutesPort.loadRoutes(ROOM_ID)).willReturn(List.of(routeOfA()));

        // when
        RunningComboArchive archive = writer.write(ROOM_ID);

        // then
        assertThat(archive).isEqualTo(new RunningComboArchive(1, 1, 1));
        List<RunningCombo> created = captureCreated();
        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getSegmentNumber().value()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 쌍·같은 시작 시각의 구간이 겹치면 늦게 끝난 구간을 남긴다")
    void keepsLaterEndingSegmentForSameStart() {
        // given -> 관계를 못 지운 뒤 다시 붙어 같은 관계가 이어 쌓였다
        RunningComboSegment closed = new RunningComboSegment(A, B, at(20), at(70), 5);
        RunningComboPair open = new RunningComboPair(A, B, at(20), at(100), 8, 1);
        given(loadRunningComboHistoryPort.loadHistory(ROOM_ID))
                .willReturn(new RunningComboHistory(List.of(closed), List.of(open)));
        given(loadRecordedRoutesPort.loadRoutes(ROOM_ID)).willReturn(List.of(routeOfA()));

        // when
        writer.write(ROOM_ID);

        // then
        RunningCombo combo = captureCreated().getFirst();
        assertThat(combo.getPeriod().endAt()).isEqualTo(local(100));
        assertThat(combo.getComboCount().value()).isEqualTo(8);
    }

    @Test
    @DisplayName("i콤보 위치는 시작 + i × 1회 길이에 가장 가까운 기록 경계점이다")
    void picksAchievementPointsFromRecordedRoute() {
        // given -> 20초에 시작해 5콤보. 달성 시각은 30·40·50·60·70초다
        given(loadRunningComboHistoryPort.loadHistory(ROOM_ID)).willReturn(new RunningComboHistory(
                List.of(new RunningComboSegment(A, B, at(20), at(70), 5)), List.of()));
        given(loadRecordedRoutesPort.loadRoutes(ROOM_ID)).willReturn(List.of(routeOfA()));

        // when
        writer.write(ROOM_ID);

        // then -> 30초=p10, 40초는 p13(39초)이 p14(42초)보다 가깝다, 50초=p17(51초), 60초=p20, 70초=p23(69초)
        List<ComboPoint> points = captureCreated().getFirst().getFirstRoute().orElseThrow().points();
        assertThat(points).extracting(ComboPoint::latitude).containsExactly(
                closeTo(10), closeTo(13), closeTo(17), closeTo(20), closeTo(23));
    }

    @Test
    @DisplayName("기록이 없는 참가자 쪽은 달성 위치를 비운다")
    void leavesRouteEmptyForParticipantWithoutRecord() {
        // given -> B는 기록이 없다
        given(loadRunningComboHistoryPort.loadHistory(ROOM_ID)).willReturn(new RunningComboHistory(
                List.of(new RunningComboSegment(A, B, at(20), at(40), 2)), List.of()));
        given(loadRecordedRoutesPort.loadRoutes(ROOM_ID)).willReturn(List.of(routeOfA()));

        // when
        writer.write(ROOM_ID);

        // then
        RunningCombo combo = captureCreated().getFirst();
        assertThat(combo.getFirstRoute()).isPresent();
        assertThat(combo.getSecondRoute()).isEmpty();
    }

    @Test
    @DisplayName("이어지던 관계 중 유지 횟수가 0이거나 쌓인 시각이 없는 것은 남기지 않는다")
    void dropsOpenPairsWithoutCompletedCombo() {
        // given -> A-C는 붙은 지 5초, B-C는 쌓인 시각을 싣기 전 관계다
        given(loadRunningComboHistoryPort.loadHistory(ROOM_ID)).willReturn(new RunningComboHistory(
                List.of(new RunningComboSegment(A, B, at(20), at(40), 2)),
                List.of(new RunningComboPair(A, C, at(100), at(105), 0, 1),
                        new RunningComboPair(B, C, at(100), null, 3, 1))));
        given(loadRecordedRoutesPort.loadRoutes(ROOM_ID)).willReturn(List.of(routeOfA()));

        // when
        RunningComboArchive archive = writer.write(ROOM_ID);

        // then
        assertThat(archive).isEqualTo(new RunningComboArchive(1, 0, 1));
        assertThat(captureCreated()).hasSize(1);
    }

    private static double closeTo(int index) {
        return Math.round(latitudeOf(index) * 1e5) / 1e5;   // polyline 정밀도 5로 되돌아온다
    }
}
