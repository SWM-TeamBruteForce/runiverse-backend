package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardViewUrlPort;
import com.runiverse.running_service.application.running.common.PolylineEncoder;
import com.runiverse.running_service.application.running.port.out.LoadMyRunningRecordsPort;
import com.runiverse.running_service.application.running.port.out.LoadSegmentPolylinesPort;
import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.application.running.port.out.RunningRecordRow;
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsHandler;
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsQuery;
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 핸들러가 하는 일은 날짜를 시각 범위로 바꾸는 것, 행을 결과로 옮기는 것, 붙은 카드를 요약(썸네일·선 아트)하는 것이다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록 목록 조회 단위 테스트")
class GetMyRunningRecordsHandlerTest {

    // UserId VO가 UUIDv7만 받는다
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UserId ME = new UserId(USER_ID);
    private static final UUID CARD_A = UuidCreator.getTimeOrderedEpoch();
    private static final UUID CARD_B = UuidCreator.getTimeOrderedEpoch();
    private static final UUID CARD_C = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDate FROM = LocalDate.of(2026, 9, 27);
    private static final LocalDate TO = LocalDate.of(2026, 10, 3);
    private static final LocalDateTime FROM_START = LocalDateTime.of(2026, 9, 27, 0, 0);
    private static final LocalDateTime AFTER_TO_START = LocalDateTime.of(2026, 10, 4, 0, 0);

    @Mock
    private LoadMyRunningRecordsPort loadMyRunningRecordsPort;
    @Mock
    private LoadSegmentPolylinesPort loadSegmentPolylinesPort;
    @Mock
    private GenerateRecordCardViewUrlPort generateRecordCardViewUrlPort;

    private GetMyRunningRecordsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetMyRunningRecordsHandler(loadMyRunningRecordsPort, loadSegmentPolylinesPort,
                generateRecordCardViewUrlPort);
    }

    @Test
    @DisplayName("시작일 0시부터 종료일 다음 날 0시 미만으로 조회한다")
    void 종료일_하루가_통째로_들어간다() {
        // given -> 범위가 조금만 어긋나도 이 스텁은 맞지 않아 빈 결과가 된다
        given(loadMyRunningRecordsPort.loadByPeriod(ME, FROM_START, AFTER_TO_START))
                .willReturn(List.of(row(501L, LocalDateTime.of(2026, 10, 3, 23, 59, 59))));

        // when
        GetMyRunningRecordsResult result = handler.handle(new GetMyRunningRecordsQuery(USER_ID, FROM, TO));

        // then
        assertThat(result.runningRecords()).hasSize(1);
    }

    @Test
    @DisplayName("하루 조회는 그날 0시부터 다음 날 0시 미만이다")
    void 하루_조회도_하루_전체다() {
        // given
        LocalDate day = LocalDate.of(2026, 9, 1);
        given(loadMyRunningRecordsPort.loadByPeriod(
                ME, LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 2, 0, 0)))
                .willReturn(List.of(row(501L, LocalDateTime.of(2026, 9, 1, 6, 30))));

        // when
        GetMyRunningRecordsResult result = handler.handle(new GetMyRunningRecordsQuery(USER_ID, day, day));

        // then
        assertThat(result.runningRecords()).hasSize(1);
    }

    @Test
    @DisplayName("행의 값이 자리 그대로 결과로 옮겨진다")
    void 값이_자리_그대로_옮겨진다() {
        // given -> 거리·시간·페이스가 전부 int라 자리가 바뀌어도 컴파일이 통과한다 — 값을 다 다르게 둔다
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 28, 19, 0, 30);
        given(loadMyRunningRecordsPort.loadByPeriod(ME, FROM_START, AFTER_TO_START))
                .willReturn(List.of(new RunningRecordRow(
                        501L, 125L, RunningRoomType.MATCH, 3L, startedAt,
                        5020, 1800, 359, 42, null, null, null)));

        // when
        GetMyRunningRecordsResult result = handler.handle(new GetMyRunningRecordsQuery(USER_ID, FROM, TO));

        // then
        GetMyRunningRecordsResult.RunningRecord record = result.runningRecords().get(0);
        assertThat(record.runningRecordId()).isEqualTo(501L);
        assertThat(record.runningRoomId()).isEqualTo(125L);
        assertThat(record.type()).isEqualTo(RunningRoomType.MATCH);
        assertThat(record.playerCount()).isEqualTo(3L);
        assertThat(record.startedAt()).isEqualTo(startedAt);
        assertThat(record.totalDistanceMeters()).isEqualTo(5020);
        assertThat(record.totalDurationSeconds()).isEqualTo(1800);
        assertThat(record.averagePaceSecondsPerKm()).isEqualTo(359);
        assertThat(record.totalElevationGainMeters()).isEqualTo(42);
    }

    @Test
    @DisplayName("고도가 null인 행은 null 그대로 나간다")
    void 고도_null은_그대로_나간다() {
        // given
        given(loadMyRunningRecordsPort.loadByPeriod(ME, FROM_START, AFTER_TO_START))
                .willReturn(List.of(new RunningRecordRow(
                        501L, 125L, RunningRoomType.SOLO, 1L, LocalDateTime.of(2026, 9, 28, 19, 0),
                        5020, 1800, 359, null, null, null, null)));

        // when
        GetMyRunningRecordsResult result = handler.handle(new GetMyRunningRecordsQuery(USER_ID, FROM, TO));

        // then
        assertThat(result.runningRecords().get(0).totalElevationGainMeters()).isNull();
    }

    @Test
    @DisplayName("기간에 기록이 없으면 빈 목록이다")
    void 기록이_없으면_빈_목록이다() {
        // given
        given(loadMyRunningRecordsPort.loadByPeriod(ME, FROM_START, AFTER_TO_START))
                .willReturn(List.of());

        // when
        GetMyRunningRecordsResult result = handler.handle(new GetMyRunningRecordsQuery(USER_ID, FROM, TO));

        // then
        assertThat(result.runningRecords()).isEmpty();
    }

    @Test
    @DisplayName("포트가 준 순서를 그대로 유지한다")
    void 순서를_바꾸지_않는다() {
        // given -> 정렬은 쿼리가 한다. 핸들러가 다시 섞으면 안 된다
        given(loadMyRunningRecordsPort.loadByPeriod(ME, FROM_START, AFTER_TO_START))
                .willReturn(List.of(
                        row(501L, LocalDateTime.of(2026, 9, 27, 7, 0)),
                        row(502L, LocalDateTime.of(2026, 9, 29, 7, 0)),
                        row(503L, LocalDateTime.of(2026, 10, 3, 7, 0))));

        // when
        GetMyRunningRecordsResult result = handler.handle(new GetMyRunningRecordsQuery(USER_ID, FROM, TO));

        // then
        assertThat(result.runningRecords())
                .extracting(GetMyRunningRecordsResult.RunningRecord::runningRecordId)
                .containsExactly(501L, 502L, 503L);
    }

    @Test
    @DisplayName("카드가 없는 기록은 recordCard가 null이고, 구간도 URL도 만들지 않는다")
    void 카드가_없으면_null이다() {
        // given
        given(loadMyRunningRecordsPort.loadByPeriod(ME, FROM_START, AFTER_TO_START))
                .willReturn(List.of(row(501L, LocalDateTime.of(2026, 9, 28, 7, 0))));

        // when
        GetMyRunningRecordsResult result = handler.handle(new GetMyRunningRecordsQuery(USER_ID, FROM, TO));

        // then
        assertThat(result.runningRecords().get(0).recordCard()).isNull();
        verifyNoInteractions(loadSegmentPolylinesPort, generateRecordCardViewUrlPort);
    }

    @Test
    @DisplayName("사진 있는 카드는 대표 사진 작은 버전(thumbnail/)의 URL만 싣고 선 아트는 null이다 — 구간을 읽지 않는다")
    void 사진_있는_카드는_썸네일이다() {
        // given
        given(loadMyRunningRecordsPort.loadByPeriod(ME, FROM_START, AFTER_TO_START))
                .willReturn(List.of(cardRow(501L, CARD_A, "record-cards/u/a/thumb.jpg", RecordCardActivityType.RACE)));
        given(generateRecordCardViewUrlPort.imageUrl("record-cards/u/a/thumbnail/thumb.jpg")).willReturn("https://thumb");

        // when
        GetMyRunningRecordsResult result = handler.handle(new GetMyRunningRecordsQuery(USER_ID, FROM, TO));

        // then
        assertThat(result.runningRecords().get(0).recordCard())
                .isEqualTo(new GetMyRunningRecordsResult.RecordCard(CARD_A, RecordCardActivityType.RACE, "https://thumb", null));
        verifyNoInteractions(loadSegmentPolylinesPort);
    }

    @Test
    @DisplayName("사진 없는 카드만 모아 구간을 한 번에 읽고 선 아트(최대 50점)를 싣는다 — 구간이 없는 카드는 빈 선 아트다")
    void 사진_없는_카드는_선_아트다() {
        // given — 501 사진 있는 카드, 502 사진 없는 카드(구간 있음), 503 카드 없음, 504 사진 없는 카드(구간 없음)
        given(loadMyRunningRecordsPort.loadByPeriod(ME, FROM_START, AFTER_TO_START))
                .willReturn(List.of(
                        cardRow(501L, CARD_A, "record-cards/u/a/thumb.jpg"),
                        cardRow(502L, CARD_B, null),
                        row(503L, LocalDateTime.of(2026, 9, 29, 7, 0)),
                        cardRow(504L, CARD_C, null, RecordCardActivityType.TRAVEL)));
        given(generateRecordCardViewUrlPort.imageUrl("record-cards/u/a/thumbnail/thumb.jpg")).willReturn("https://thumb");
        List<RoutePoint> line = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            line.add(new RoutePoint(37.50 + i * 0.0001, 127.00));
        }
        given(loadSegmentPolylinesPort.loadSegmentPolylines(List.of(502L, 504L)))
                .willReturn(Map.of(502L, List.of(PolylineEncoder.encodePoints(line))));

        // when
        List<GetMyRunningRecordsResult.RunningRecord> records =
                handler.handle(new GetMyRunningRecordsQuery(USER_ID, FROM, TO)).runningRecords();

        // then
        assertThat(records.get(0).recordCard().routeArt()).isNull();
        GetMyRunningRecordsResult.RecordCard noPhoto = records.get(1).recordCard();
        assertThat(noPhoto.recordCardId()).isEqualTo(CARD_B);
        assertThat(noPhoto.activityType()).isEqualTo(RecordCardActivityType.DAILY);
        assertThat(noPhoto.thumbnailUrl()).isNull();
        assertThat(noPhoto.routeArt()).singleElement().satisfies(segment -> assertThat(segment).hasSize(50));
        assertThat(records.get(2).recordCard()).isNull();
        assertThat(records.get(3).recordCard())
                .isEqualTo(new GetMyRunningRecordsResult.RecordCard(CARD_C, RecordCardActivityType.TRAVEL, null, List.of()));
        verify(loadSegmentPolylinesPort).loadSegmentPolylines(List.of(502L, 504L));
    }

    private static RunningRecordRow cardRow(long runningRecordId, UUID recordCardId, String thumbnailImageKey) {
        return cardRow(runningRecordId, recordCardId, thumbnailImageKey, RecordCardActivityType.DAILY);
    }

    private static RunningRecordRow cardRow(long runningRecordId, UUID recordCardId, String thumbnailImageKey,
                                            RecordCardActivityType activityType) {
        return new RunningRecordRow(
                runningRecordId, 125L, RunningRoomType.SOLO, 1L, LocalDateTime.of(2026, 9, 28, 7, 0),
                5020, 1800, 359, 42, recordCardId, thumbnailImageKey, activityType);
    }

    private static RunningRecordRow row(long runningRecordId, LocalDateTime startedAt) {
        return new RunningRecordRow(
                runningRecordId, 125L, RunningRoomType.SOLO, 1L, startedAt,
                5020, 1800, 359, 42, null, null, null);
    }
}
