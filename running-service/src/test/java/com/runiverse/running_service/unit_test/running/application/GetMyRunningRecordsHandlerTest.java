package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.port.out.LoadMyRunningRecordsPort;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

// 핸들러가 하는 일은 날짜를 시각 범위로 바꾸는 것과 행을 결과로 옮기는 것 둘이다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록 목록 조회 단위 테스트")
class GetMyRunningRecordsHandlerTest {

    // UserId VO가 UUIDv7만 받는다
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UserId ME = new UserId(USER_ID);
    private static final LocalDate FROM = LocalDate.of(2026, 9, 27);
    private static final LocalDate TO = LocalDate.of(2026, 10, 3);
    private static final LocalDateTime FROM_START = LocalDateTime.of(2026, 9, 27, 0, 0);
    private static final LocalDateTime AFTER_TO_START = LocalDateTime.of(2026, 10, 4, 0, 0);

    @Mock
    private LoadMyRunningRecordsPort loadMyRunningRecordsPort;

    private GetMyRunningRecordsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetMyRunningRecordsHandler(loadMyRunningRecordsPort);
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
                        5020, 1800, 359, 42, "u{~vFvyys@fS]pT_@")));

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
        assertThat(record.routePolyline()).isEqualTo("u{~vFvyys@fS]pT_@");
    }

    @Test
    @DisplayName("고도가 null인 행은 null 그대로 나간다")
    void 고도_null은_그대로_나간다() {
        // given
        given(loadMyRunningRecordsPort.loadByPeriod(ME, FROM_START, AFTER_TO_START))
                .willReturn(List.of(new RunningRecordRow(
                        501L, 125L, RunningRoomType.SOLO, 1L, LocalDateTime.of(2026, 9, 28, 19, 0),
                        5020, 1800, 359, null, "u{~vF")));

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

    private static RunningRecordRow row(long runningRecordId, LocalDateTime startedAt) {
        return new RunningRecordRow(
                runningRecordId, 125L, RunningRoomType.SOLO, 1L, startedAt,
                5020, 1800, 359, 42, "u{~vF");
    }
}
