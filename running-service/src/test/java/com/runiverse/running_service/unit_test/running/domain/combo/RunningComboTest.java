package com.runiverse.running_service.unit_test.running.domain.combo;

import com.runiverse.running_service.domain.running.combo.RunningCombo;
import com.runiverse.running_service.domain.running.combo.exception.ComboRouteRequiredException;
import com.runiverse.running_service.domain.running.combo.exception.ComboRouteSizeMismatchException;
import com.runiverse.running_service.domain.running.combo.exception.ComboUsersNotOrderedException;
import com.runiverse.running_service.domain.running.combo.exception.InvalidComboCountException;
import com.runiverse.running_service.domain.running.combo.exception.InvalidComboSegmentNumberException;
import com.runiverse.running_service.domain.running.combo.vo.ComboPoint;
import com.runiverse.running_service.domain.running.metric.exception.InvalidRunningPeriodException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("러닝 콤보 도메인 단위 테스트")
class RunningComboTest {

    // UUIDv7 끝자리만 다르게 해 FIRST < SECOND로 고정한다
    private static final UUID FIRST = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000a1");
    private static final UUID SECOND = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000b2");
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 13, 7, 0, 20);
    private static final LocalDateTime END = START.plusSeconds(20);

    // 2콤보짜리 정상 구간 — 각 테스트는 여기서 한 군데만 어긋뜨린다
    private static RunningCombo.RunningComboBuilder validCombo() {
        return RunningCombo.archive()
                .runningRoomId(12L)
                .firstUserId(FIRST)
                .secondUserId(SECOND)
                .segmentNumber(1)
                .comboCount(2)
                .startAt(START)
                .endAt(END)
                .firstRoute(points(2))
                .secondRoute(points(2));
    }

    private static List<ComboPoint> points(int count) {
        List<ComboPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new ComboPoint(37.5665 + i * 0.0001, 126.9780));
        }
        return points;
    }

    @Test
    @DisplayName("정상 구간은 값 그대로 만들어진다")
    void createsValidCombo() {
        // when
        RunningCombo combo = validCombo().build();

        // then
        assertThat(combo.getRunningRoomId().value()).isEqualTo(12L);
        assertThat(combo.getFirstUserId().value()).isEqualTo(FIRST);
        assertThat(combo.getSecondUserId().value()).isEqualTo(SECOND);
        assertThat(combo.getSegmentNumber().value()).isEqualTo(1);
        assertThat(combo.getComboCount().value()).isEqualTo(2);
        assertThat(combo.getPeriod().startAt()).isEqualTo(START);
        assertThat(combo.getPeriod().endAt()).isEqualTo(END);
        assertThat(combo.getFirstRoute()).hasValueSatisfying(route -> assertThat(route.size()).isEqualTo(2));
        assertThat(combo.getSecondRoute()).hasValueSatisfying(route -> assertThat(route.size()).isEqualTo(2));
    }

    @Test
    @DisplayName("기록이 없는 참가자 쪽은 달성 위치가 비어도 된다")
    void allowsMissingRouteForParticipantWithoutRecord() {
        // when -> 5m만 뛰고 그만둔 참가자는 기록이 없어 찍을 경로가 없다
        RunningCombo combo = validCombo().secondRoute(null).build();

        // then
        assertThat(combo.getFirstRoute()).isPresent();
        assertThat(combo.getSecondRoute()).isEmpty();
    }

    @Test
    @DisplayName("쌍이 뒤집혀 있으면 거부한다 — 같은 콤보가 두 행으로 갈린다")
    void rejectsReversedPair() {
        // when & then -> 경로까지 함께 바꿔야 해서 조용히 정렬하지 않는다
        assertThatThrownBy(() -> validCombo().firstUserId(SECOND).secondUserId(FIRST).build())
                .isInstanceOf(ComboUsersNotOrderedException.class);
    }

    @Test
    @DisplayName("같은 사람끼리의 쌍은 거부한다")
    void rejectsSelfPair() {
        // when & then
        assertThatThrownBy(() -> validCombo().secondUserId(FIRST).build())
                .isInstanceOf(ComboUsersNotOrderedException.class);
    }

    @Test
    @DisplayName("구간 번호가 1보다 작으면 거부한다")
    void rejectsSegmentNumberBelowOne() {
        // when & then
        assertThatThrownBy(() -> validCombo().segmentNumber(0).build())
                .isInstanceOf(InvalidComboSegmentNumberException.class);
    }

    @Test
    @DisplayName("유지 횟수가 0인 구간은 거부한다 — 붙자마자 끊겨 함께 달린 구간이 없다")
    void rejectsZeroComboCount() {
        // when & then
        assertThatThrownBy(() -> validCombo().comboCount(0).firstRoute(null).secondRoute(null).build())
                .isInstanceOf(InvalidComboCountException.class);
    }

    @Test
    @DisplayName("끝 시각이 시작 시각보다 뒤가 아니면 거부한다")
    void rejectsNonPositivePeriod() {
        // when & then
        assertThatThrownBy(() -> validCombo().endAt(START).build())
                .isInstanceOf(InvalidRunningPeriodException.class);
    }

    @Test
    @DisplayName("달성 위치 수가 유지 횟수와 다르면 거부한다 — n콤보 표시가 엉뚱한 자리를 가리킨다")
    void rejectsRouteSizeMismatch() {
        // when & then
        assertThatThrownBy(() -> validCombo().firstRoute(points(3)).build())
                .isInstanceOf(ComboRouteSizeMismatchException.class);
    }

    @Test
    @DisplayName("빈 달성 위치 목록은 거부한다 — 기록이 없으면 목록이 아니라 null이다")
    void rejectsEmptyRoute() {
        // when & then
        assertThatThrownBy(() -> validCombo().secondRoute(List.of()).build())
                .isInstanceOf(ComboRouteRequiredException.class);
    }

    @Test
    @DisplayName("넘긴 목록을 나중에 바꿔도 달성 위치는 바뀌지 않는다")
    void copiesRouteDefensively() {
        // given
        List<ComboPoint> points = points(2);
        RunningCombo combo = validCombo().firstRoute(points).build();

        // when
        points.add(new ComboPoint(0, 0));

        // then
        assertThat(combo.getFirstRoute()).hasValueSatisfying(route -> assertThat(route.size()).isEqualTo(2));
    }
}
