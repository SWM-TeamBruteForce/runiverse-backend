package com.runiverse.running_service.unit_test.running.presentation;

import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsResult;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.presentation.running.response.RunningRecordsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("기록 목록 응답 변환 단위 테스트")
public class RunningRecordsResponseTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 7, 25, 19, 0, 30);

    @Test
    @DisplayName("기록의 값이 자리 그대로 옮겨진다")
    void 값이_자리_그대로_옮겨진다() {
        // given -> 거리·시간·페이스가 전부 int라 자리가 바뀌어도 컴파일이 통과한다 — 값을 다 다르게 둔다
        GetMyRunningRecordsResult result = new GetMyRunningRecordsResult(List.of(
                new GetMyRunningRecordsResult.RunningRecord(
                        501L, 125L, RunningRoomType.MATCH, 3L, STARTED_AT,
                        5020, 1800, 359, 42, "u{~vFvyys@fS]pT_@")));

        // when
        RunningRecordsResponse response = RunningRecordsResponse.from(result);

        // then
        RunningRecordsResponse.RunningRecordResponse record = response.runningRecords().get(0);
        assertThat(record.runningRecordId()).isEqualTo(501L);
        assertThat(record.runningRoomId()).isEqualTo(125L);
        assertThat(record.type()).isEqualTo(RunningRoomType.MATCH);
        assertThat(record.playerCount()).isEqualTo(3L);
        assertThat(record.startedAt()).isEqualTo(STARTED_AT);
        assertThat(record.totalDistanceMeters()).isEqualTo(5020);
        assertThat(record.totalDurationSeconds()).isEqualTo(1800);
        assertThat(record.averagePaceSecondsPerKm()).isEqualTo(359);
        assertThat(record.totalElevationGainMeters()).isEqualTo(42);
        assertThat(record.routePolyline()).isEqualTo("u{~vFvyys@fS]pT_@");
    }

    @Test
    @DisplayName("고도가 null이면 null 그대로 나간다 -> 0으로 바꾸지 않는다")
    void 고도_null은_그대로_나간다() {
        // given
        GetMyRunningRecordsResult result = new GetMyRunningRecordsResult(List.of(
                new GetMyRunningRecordsResult.RunningRecord(
                        501L, 125L, RunningRoomType.SOLO, 1L, STARTED_AT,
                        5020, 1800, 359, null, "u{~vF")));

        // when
        RunningRecordsResponse response = RunningRecordsResponse.from(result);

        // then
        assertThat(response.runningRecords().get(0).totalElevationGainMeters()).isNull();
    }

    @Test
    @DisplayName("기록이 없으면 빈 배열이다 -> null로 내리지 않는다")
    void 기록이_없으면_빈_배열이다() {
        // when
        RunningRecordsResponse response = RunningRecordsResponse.from(new GetMyRunningRecordsResult(List.of()));

        // then
        assertThat(response.runningRecords()).isEmpty();
    }
}
