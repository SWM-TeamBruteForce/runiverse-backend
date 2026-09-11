package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.command.combo.RunningComboEvaluation;
import com.runiverse.running_service.application.running.command.combo.RunningComboEvaluator;
import com.runiverse.running_service.application.running.command.combo.RunningComboProperties;
import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboRelation;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 콤보 판정 규칙 전부가 여기 모여 있다. now를 인자로 받는 순수 계산이라
// 시계도 Redis도 끼지 않고 규칙만 직접 검증한다
@DisplayName("러닝 콤보 판정 단위 테스트")
class RunningComboEvaluatorTest {

    // UUID 순서가 관계의 first/second를 정하므로 A < B < C로 고정한다 —
    // 랜덤 UUID를 쓰면 gapMeters 부호가 실행마다 뒤집힌다.
    // UserId가 UUIDv7만 받으므로 버전 자리는 7, 변형 자리는 8로 두고 끝자리만 다르게 한다
    private static final UserId A = new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000a1"));
    private static final UserId B = new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000b2"));
    private static final UserId C = new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000c3"));

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:10Z");
    // 실제 application.properties와 같은 값으로 맞춘다
    private static final RunningComboProperties PROPERTIES = new RunningComboProperties(
            30, 30, Duration.ofSeconds(19), Duration.ofSeconds(10), 1);

    @Test
    @DisplayName("처음 겹치면 그 순간이 시작 시각이고 콤보는 1부터다")
    void evaluate_startsComboAtOne() {
        // given -> 저장된 관계가 없고 두 사람이 20m 차이로 달린다
        List<RunningComboSnapshot> snapshots = List.of(
                snapshot(A, 3_400, NOW, 0),
                snapshot(B, 3_420, NOW, 0));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then
        RunningComboPair pair = pairBetween(evaluation, A, B);
        assertThat(pair.startedAt()).isEqualTo(NOW);
        assertThat(pair.maxComboCount()).isEqualTo(1);
        assertThat(pair.missLeft()).isEqualTo(1);

        RunningComboRelation relation = relationBetween(evaluation, A, B);
        assertThat(relation.comboCount()).isEqualTo(1);
        assertThat(relation.maxComboCount()).isEqualTo(1);
        // second(B)가 앞서 있으면 양수다 — 받는 쪽이 B면 부호를 뒤집어 쓴다
        assertThat(relation.gapMeters()).isEqualTo(20);
        assertThat(evaluation.update().recipients()).containsExactlyInAnyOrder(A.value(), B.value());
    }

    @Test
    @DisplayName("second가 뒤처져 있으면 gapMeters가 음수다")
    void evaluate_signsGapByPairOrder() {
        // given -> A가 앞서고 B가 20m 뒤에 있다
        List<RunningComboSnapshot> snapshots = List.of(
                snapshot(A, 3_420, NOW, 0),
                snapshot(B, 3_400, NOW, 0));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then
        assertThat(relationBetween(evaluation, A, B).gapMeters()).isEqualTo(-20);
    }

    @Test
    @DisplayName("유지 횟수는 저장값이 아니라 시작 시각에서 계산한다")
    void evaluate_countsComboFromStartedAt() {
        // given -> 40초 전에 시작한 콤보. 1회 길이가 10초이므로 5가 나와야 한다
        List<RunningComboPair> stored = List.of(
                new RunningComboPair(A, B, NOW.minusSeconds(40), 2, 1));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, overlapping(), stored, NOW, PROPERTIES);

        // then
        assertThat(relationBetween(evaluation, A, B).comboCount()).isEqualTo(5);
        // 겹친 순간이라 최고 콤보도 함께 오른다
        assertThat(pairBetween(evaluation, A, B).maxComboCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("기준 시각이 다른 누적 거리는 마지막 속도로 밀어 맞춘 뒤 비교한다")
    void evaluate_correctsStaleDistanceBeforeComparing() {
        // given -> 보정 전 거리차는 20m라 붙을 값이지만,
        // B의 값이 5초 묵었고 3.2m/s로 달리는 중이라 16m를 밀면 36m가 되어 창을 벗어난다
        List<RunningComboSnapshot> snapshots = List.of(
                snapshot(A, 3_400, NOW, 0),
                snapshot(B, 3_420, NOW.minusSeconds(5), 3.2));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).inCombo()).isFalse();
        assertThat(evaluation.update().relations()).isEmpty();
    }

    @Test
    @DisplayName("신선도 문턱을 넘긴 참가자는 거리가 가까워도 비교에서 뺀다")
    void evaluate_excludesStalePeer() {
        // given -> 거리차는 10m지만 B의 마지막 수신이 20초 전이다(문턱 19초).
        // 연결이 끊긴 사람은 마지막 거리에 멈춰 서 있어 유령 콤보가 붙는다
        List<RunningComboSnapshot> snapshots = List.of(
                snapshot(A, 3_400, NOW, 0),
                snapshot(B, 3_410, NOW.minusSeconds(20), 0));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).inCombo()).isFalse();
        assertThat(evaluation.update().relations()).isEmpty();
    }

    @Test
    @DisplayName("한 번 빗나가도 봐주기가 남아 있으면 콤보가 이어진다")
    void evaluate_spendsMissAllowanceInsteadOfBreaking() {
        // given -> 50m 벌어졌지만 봐주기가 1 남아 있다
        List<RunningComboPair> stored = List.of(
                new RunningComboPair(A, B, NOW.minusSeconds(40), 8, 1));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, apart(), stored, NOW, PROPERTIES);

        // then
        RunningComboPair pair = pairBetween(evaluation, A, B);
        assertThat(pair.startedAt()).isEqualTo(NOW.minusSeconds(40));
        assertThat(pair.missLeft()).isZero();
        // 끊기지 않았으므로 통에도 그대로 실린다
        assertThat(relationBetween(evaluation, A, B).comboCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("봐주기가 없는 상태에서 또 빗나가면 끊기고 시작 시각이 지워진다")
    void evaluate_breaksComboWhenAllowanceExhausted() {
        // given -> 봐주기를 이미 다 쓴 관계가 또 빗나간다
        List<RunningComboPair> stored = List.of(
                new RunningComboPair(A, B, NOW.minusSeconds(40), 8, 0));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, apart(), stored, NOW, PROPERTIES);

        // then
        RunningComboPair pair = pairBetween(evaluation, A, B);
        assertThat(pair.startedAt()).isNull();
        assertThat(pair.missLeft()).isEqualTo(1);   // 다음 콤보를 위해 되돌아온다
        assertThat(pair.maxComboCount()).isEqualTo(8);   // 최고 콤보는 끊겨도 남는다
        // 끊긴 관계가 목록에서 빠지는 것이 곧 끊김 통지다
        assertThat(evaluation.update().relations()).isEmpty();
        // 그래도 상대는 받아야 한다 — 안 보내면 그쪽 화면에 끊긴 콤보가 남는다
        assertThat(evaluation.update().recipients()).containsExactlyInAnyOrder(A.value(), B.value());
    }

    @Test
    @DisplayName("봐주는 구간에서는 최고 콤보가 오르지 않는다")
    void evaluate_keepsMaxComboWhileMissing() {
        // given -> 100초 전에 시작해 현재 콤보는 11까지 셀 수 있지만 이번 판정은 빗나갔다.
        // 봐주는 구간에도 시각은 흐르므로 여기서 갱신하면
        // 실제로 함께 있지 않았던 구간이 기록에 섞인다
        List<RunningComboPair> stored = List.of(
                new RunningComboPair(A, B, NOW.minusSeconds(100), 3, 1));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, apart(), stored, NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).maxComboCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("끊겼다 다시 겹치면 콤보는 1부터지만 최고 콤보는 살아 있다")
    void evaluate_restartsComboKeepingMax() {
        // given -> 시작 시각이 지워진 관계
        List<RunningComboPair> stored = List.of(new RunningComboPair(A, B, null, 8, 1));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, overlapping(), stored, NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).startedAt()).isEqualTo(NOW);
        RunningComboRelation relation = relationBetween(evaluation, A, B);
        assertThat(relation.comboCount()).isEqualTo(1);
        assertThat(relation.maxComboCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("무관한 참가자끼리의 콤보는 건드리지 않고 통에 담기만 한다")
    void evaluate_leavesUnrelatedPairUntouched() {
        // given -> A가 보냈고 B-C는 180m 떨어져 빗나갈 거리다.
        // 그래도 A의 배치가 남의 콤보를 깎아서는 안 된다
        List<RunningComboSnapshot> snapshots = List.of(
                snapshot(A, 3_400, NOW, 0),
                snapshot(B, 3_420, NOW, 0),
                snapshot(C, 3_600, NOW, 0));
        List<RunningComboPair> stored = List.of(
                new RunningComboPair(A, B, NOW.minusSeconds(40), 5, 1),
                new RunningComboPair(B, C, NOW.minusSeconds(20), 4, 0));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then -> 저장 대상은 A가 낀 관계 둘뿐이다
        assertThat(evaluation.pairs()).hasSize(2);
        assertThat(evaluation.pairs()).allMatch(pair -> pair.contains(A));
        // B-C는 봐주기가 0이라 B나 C가 보냈으면 끊겼을 상태인데 그대로 살아 있다
        assertThat(relationBetween(evaluation, B, C).comboCount()).isEqualTo(3);
        // 받는 사람은 A와 얽힌 참가자뿐 — C는 A와 아무 관계가 아니다
        assertThat(evaluation.update().recipients()).containsExactlyInAnyOrder(A.value(), B.value());
    }

    @Test
    @DisplayName("방금 끊긴 상대도 받는 사람에 넣는다")
    void evaluate_notifiesJustBrokenPeer() {
        // given -> A-C가 이번 판정에서 끊기고 A-B는 계속 겹친다
        List<RunningComboSnapshot> snapshots = List.of(
                snapshot(A, 3_400, NOW, 0),
                snapshot(B, 3_420, NOW, 0),
                snapshot(C, 3_600, NOW, 0));
        List<RunningComboPair> stored = List.of(
                new RunningComboPair(A, B, NOW.minusSeconds(40), 5, 1),
                new RunningComboPair(A, C, NOW.minusSeconds(60), 7, 0));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, C).startedAt()).isNull();
        assertThat(evaluation.update().recipients())
                .containsExactlyInAnyOrder(A.value(), B.value(), C.value());
        // 끊긴 A-C는 통에서 빠지고 살아 있는 A-B만 남는다
        assertThat(evaluation.update().relations()).hasSize(1);
    }

    @Test
    @DisplayName("아무와도 겹치지 않으면 보낸 사람만 빈 목록을 받는다")
    void evaluate_returnsEmptyRelationsWhenNobodyOverlaps() {
        // given
        List<RunningComboSnapshot> snapshots = List.of(
                snapshot(A, 3_400, NOW, 0),
                snapshot(B, 3_600, NOW, 0));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then
        assertThat(evaluation.update().relations()).isEmpty();
        assertThat(evaluation.update().recipients()).containsExactly(A.value());
    }

    // 20m 차이 — 붙는 거리(30m) 안이다
    private static List<RunningComboSnapshot> overlapping() {
        return List.of(snapshot(A, 3_400, NOW, 0), snapshot(B, 3_420, NOW, 0));
    }

    // 50m 차이 — 창 밖이라 빗나간다
    private static List<RunningComboSnapshot> apart() {
        return List.of(snapshot(A, 3_400, NOW, 0), snapshot(B, 3_450, NOW, 0));
    }

    private static RunningComboSnapshot snapshot(
            UserId userId, double meters, Instant recordedAt, double speed) {
        return new RunningComboSnapshot(userId, meters, recordedAt, speed);
    }

    private static RunningComboPair pairBetween(
            RunningComboEvaluation evaluation, UserId one, UserId other) {
        RunningComboPair key = RunningComboPair.empty(one, other, 0);
        return evaluation.pairs().stream()
                .filter(pair -> pair.first().equals(key.first())
                        && pair.second().equals(key.second()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("저장 대상에 없는 관계: " + one + ", " + other));
    }

    private static RunningComboRelation relationBetween(
            RunningComboEvaluation evaluation, UserId one, UserId other) {
        return evaluation.update().relations().stream()
                .filter(relation -> relation.first().equals(one.value())
                        && relation.second().equals(other.value())
                        || relation.first().equals(other.value())
                        && relation.second().equals(one.value()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("통에 없는 관계: " + one + ", " + other));
    }
}
