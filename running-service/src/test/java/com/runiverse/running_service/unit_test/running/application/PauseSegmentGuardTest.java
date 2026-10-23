package com.runiverse.running_service.unit_test.running.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.location.PauseSegmentGuard;
import com.runiverse.running_service.application.running.exception.PauseSegmentDecreasedException;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("일시정지 구간 번호 검증 단위 테스트")
class PauseSegmentGuardTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 19, 7, 0);
    private static final Long ROOM_ID = 125L;
    // UserId VO가 UUIDv7만 받는다
    private static final UserId USER = new UserId(UuidCreator.getTimeOrderedEpoch());

    private final LogCapture logs = LogCapture.of(PauseSegmentGuard.class);

    @AfterEach
    void tearDown() {
        logs.stop();
    }

    // 검증은 순번과 일시정지 구간 번호만 본다
    private static TrackPoint point(long sequence, int pauseSegment) {
        return new TrackPoint(sequence, 37.5, 127.0, null, 5.0, null, null, null, null,
                START.plusSeconds(sequence), pauseSegment);
    }

    // 순번 lastSequence까지 반영했고 그때 번호가 lastPauseSegment였다
    private static RunningDistance stored(long lastSequence, int lastPauseSegment) {
        return new RunningDistance(100.0, lastSequence, lastPauseSegment, 37.5, 127.0, null, null, null);
    }

    @Test
    @DisplayName("번호가 그대로이거나 오르면 통과한다")
    void acceptsNonDecreasingSegments() {
        assertThatCode(() -> PauseSegmentGuard.validate(ROOM_ID, USER, RunningDistance.empty(),
                List.of(point(0, 0), point(1, 0), point(2, 1), point(3, 3))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("배치 안에서 순번 순으로 번호가 줄면 거부한다")
    void rejectsDecreaseWithinBatch() {
        assertThatThrownBy(() -> PauseSegmentGuard.validate(ROOM_ID, USER, RunningDistance.empty(),
                List.of(point(0, 1), point(1, 0))))
                .isInstanceOf(PauseSegmentDecreasedException.class);
    }

    @Test
    @DisplayName("배치가 뒤섞여 와도 순번 순으로 놓고 판정한다")
    void ordersBySequenceBeforeChecking() {
        // given -> 보낸 순서로는 1 → 0으로 줄지만 순번 순으로는 0 → 1이다
        List<TrackPoint> shuffled = List.of(point(1, 1), point(0, 0));

        // when & then -> 보낸 순서로 판정하면 멀쩡한 배치를 버린다
        assertThatCode(() -> PauseSegmentGuard.validate(ROOM_ID, USER, RunningDistance.empty(), shuffled))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("마지막으로 반영한 좌표 뒤 순번의 번호가 그 좌표보다 작으면 거부한다")
    void rejectsDecreaseFromStoredPoint() {
        // when & then -> 순번 5에서 번호 2였는데 순번 6이 1이다
        assertThatThrownBy(() -> PauseSegmentGuard.validate(ROOM_ID, USER, stored(5, 2), List.of(point(6, 1))))
                .isInstanceOf(PauseSegmentDecreasedException.class);
    }

    @Test
    @DisplayName("마지막으로 반영한 좌표와 번호가 같으면 통과한다")
    void acceptsSameSegmentAsStoredPoint() {
        assertThatCode(() -> PauseSegmentGuard.validate(ROOM_ID, USER, stored(5, 2), List.of(point(6, 2), point(7, 3))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 반영한 순번은 마지막 번호와 비교하지 않는다 -> 재연결 재전송분이다")
    void ignoresStoredComparisonForResentPoints() {
        // given -> 순번 5에서 번호 2였다. 재연결하면 순번 0부터 처음 값 그대로 다시 온다
        List<TrackPoint> resent = List.of(point(0, 0), point(3, 1), point(5, 2), point(6, 2));

        // when & then
        assertThatCode(() -> PauseSegmentGuard.validate(ROOM_ID, USER, stored(5, 2), resent))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("재전송분이 섞여도 배치 안에서 줄면 거부한다")
    void rejectsDecreaseAmongResentPoints() {
        // given -> 재전송분 순번 3이 번호 3인데 다음 순번 4가 2다
        List<TrackPoint> broken = List.of(point(3, 3), point(4, 2));

        // when & then
        assertThatThrownBy(() -> PauseSegmentGuard.validate(ROOM_ID, USER, stored(5, 2), broken))
                .isInstanceOf(PauseSegmentDecreasedException.class);
    }

    @Test
    @DisplayName("첫 배치는 0부터 시작하지 않아도 줄지만 않으면 통과한다")
    void acceptsFirstBatchStartingAboveZero() {
        // given -> 첫 배치를 받기 전에 일시정지를 눌렀다
        assertThatCode(() -> PauseSegmentGuard.validate(ROOM_ID, USER, RunningDistance.empty(), List.of(point(0, 1))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("배치 안에서 줄어 거부하면 원인과 순번을 INFO로 남긴다 -> 운영에서 거부된 러닝을 찾는다")
    void logsDecreaseWithinBatch() {
        // when
        assertThatThrownBy(() -> PauseSegmentGuard.validate(ROOM_ID, USER, RunningDistance.empty(),
                List.of(point(0, 1), point(1, 0))))
                .isInstanceOf(PauseSegmentDecreasedException.class);

        // then -> 사용자 상태가 깨진 것이라 ERROR가 아니다
        assertThat(logs.messages(Level.INFO)).containsExactly(
                "[러닝] 트랙 저장 실패: 배치 안에서 일시정지 구간 번호 감소 - roomId=125, userId=" + USER.value()
                        + ", sequence=1, pauseSegment=0, previousPauseSegment=1");
        assertThat(logs.messages(Level.ERROR)).isEmpty();
    }

    @Test
    @DisplayName("저장된 번호보다 작아 거부하면 서버가 기억하는 값까지 INFO로 남긴다")
    void logsDecreaseFromStoredPoint() {
        // when -> 앱을 재설치해 번호가 0으로 돌아갔다
        assertThatThrownBy(() -> PauseSegmentGuard.validate(ROOM_ID, USER, stored(119, 2),
                List.of(point(120, 0))))
                .isInstanceOf(PauseSegmentDecreasedException.class);

        // then
        assertThat(logs.messages(Level.INFO)).containsExactly(
                "[러닝] 트랙 저장 실패: 저장된 일시정지 구간 번호보다 작음 - roomId=125, userId=" + USER.value()
                        + ", sequence=120, pauseSegment=0, lastSequence=119, lastPauseSegment=2");
    }

    @Test
    @DisplayName("통과하면 아무것도 남기지 않는다")
    void logsNothingWhenAccepted() {
        // when
        PauseSegmentGuard.validate(ROOM_ID, USER, stored(5, 2), List.of(point(6, 2)));

        // then
        assertThat(logs.messages(Level.INFO)).isEmpty();
    }
}
