package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.command.combo.RunningComboEvaluation;
import com.runiverse.running_service.application.running.command.combo.RunningComboEvaluator;
import com.runiverse.running_service.application.running.command.combo.RunningComboProperties;
import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboRelation;
import com.runiverse.running_service.application.running.port.out.RunningComboSegment;
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
    // 실제 application.properties와 같은 값으로 맞춘다 — 신선도 문턱 19초, 1회 10초, 봐주기 1
    private static final RunningComboProperties PROPERTIES = new RunningComboProperties(
            30, 30, Duration.ofSeconds(19), Duration.ofSeconds(10), 1);
    // 신선도 문턱(19초)을 넘긴 시각
    private static final Instant LONG_AGO = NOW.minusSeconds(30);

    @Test
    @DisplayName("처음 겹치면 그 순간이 시작 시각이고 콤보는 0부터다")
    void evaluate_startsComboAtZero() {
        // given -> 저장된 관계가 없고 두 사람이 20m 차이로 달린다
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_400), moving(B, 3_420));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then -> 붙는 순간은 0이다
        RunningComboPair pair = pairBetween(evaluation, A, B);
        assertThat(pair.startedAt()).isEqualTo(NOW);
        assertThat(pair.accruedAt()).isEqualTo(NOW);
        assertThat(pair.maxComboCount()).isZero();
        assertThat(pair.missLeft()).isEqualTo(1);

        RunningComboRelation relation = relationBetween(evaluation, A, B);
        assertThat(relation.comboCount()).isZero();
        assertThat(relation.maxComboCount()).isZero();
        // second(B)가 앞서 있으면 양수다 — 받는 쪽이 B면 부호를 뒤집어 쓴다
        assertThat(relation.gapMeters()).isEqualTo(20);
        assertThat(evaluation.update().recipients()).containsExactlyInAnyOrder(A.value(), B.value());
        assertThat(evaluation.segments()).isEmpty();
    }

    @Test
    @DisplayName("second가 뒤처져 있으면 gapMeters가 음수다")
    void evaluate_signsGapByPairOrder() {
        // given -> A가 앞서고 B가 20m 뒤에 있다
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_420), moving(B, 3_400));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then
        assertThat(relationBetween(evaluation, A, B).gapMeters()).isEqualTo(-20);
    }

    @Test
    @DisplayName("유지 횟수는 저장값이 아니라 시작 시각부터 쌓인 시각까지에서 계산한다")
    void evaluate_countsComboFromStartedAtToAccruedAt() {
        // given -> 40초 전에 시작한 콤보. 둘 다 지금 막 움직였으니 쌓인 시각이 지금이 되어 4가 나와야 한다
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(40), NOW.minusSeconds(10), 2, 1));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, overlapping(), stored, NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).accruedAt()).isEqualTo(NOW);
        assertThat(relationBetween(evaluation, A, B).comboCount()).isEqualTo(4);
        // 겹친 순간이라 최고 콤보도 함께 오른다
        assertThat(pairBetween(evaluation, A, B).maxComboCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("기준 시각이 다른 누적 거리는 마지막 속도로 밀어 맞춘 뒤 비교한다")
    void evaluate_correctsStaleDistanceBeforeComparing() {
        // given -> 보정 전 거리차는 20m라 붙을 값이지만,
        // B의 값이 5초 묵었고 3.2m/s로 달리는 중이라 16m를 밀면 36m가 되어 창을 벗어난다
        List<RunningComboSnapshot> snapshots = List.of(
                moving(A, 3_400),
                movingAt(B, 3_420, NOW.minusSeconds(5), 3.2));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).inCombo()).isFalse();
        assertThat(evaluation.update().relations()).isEmpty();
    }

    // ── 참가자 상태: 함께 달린 것만 센다 ──

    @Test
    @DisplayName("출발선처럼 둘 다 의심점이 옮겨진 적 없으면 거리가 같아도 붙지 않는다")
    void evaluate_doesNotStartComboAtStartLine() {
        // given -> 둘 다 0m에 서 있다. 좌표는 오지만 의심점이 한 번도 옮겨지지 않았다
        List<RunningComboSnapshot> snapshots = List.of(standing(A, 0), standing(B, 0));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then -> 서 있는 두 사람에게 콤보가 붙으면 안 뛰었는데 쌓인다
        assertThat(pairBetween(evaluation, A, B).inCombo()).isFalse();
        assertThat(evaluation.update().relations()).isEmpty();
    }

    @Test
    @DisplayName("서 있는 사람을 지나가는 사람에게는 콤보가 붙지 않는다")
    void evaluate_doesNotStartComboWithStandingPeer() {
        // given -> B는 신호 대기 중이고 A가 B의 누적 거리를 지나간다
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_405), standing(B, 3_400));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).inCombo()).isFalse();
    }

    @Test
    @DisplayName("이어지던 콤보에서 한쪽이 서 있으면 빗나감이다 — 봐주기를 쓰고 쌓이지 않는다")
    void evaluate_missesWhenPeerStands() {
        // given -> 거리는 붙어 있지만 B가 신호 대기 중이다
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_405), standing(B, 3_400));
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(60), NOW.minusSeconds(20), 4, 1));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then
        RunningComboPair pair = pairBetween(evaluation, A, B);
        assertThat(pair.inCombo()).isTrue();
        assertThat(pair.missLeft()).isZero();
        assertThat(pair.accruedAt()).isEqualTo(NOW.minusSeconds(20));
        assertThat(pair.maxComboCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("한쪽이 일시정지면 봐주기가 남아 있어도 바로 끊고 구간을 닫는다")
    void evaluate_breaksImmediatelyWhenPeerPaused() {
        // given -> 봐주기 1이 남아 있지만 B가 일시정지 버튼을 눌렀다
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_405), paused(B, 3_400));
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(60), NOW.minusSeconds(20), 4, 1));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then -> 일시정지는 사용자의 선택이다. 다시 달리면 새 구간이다
        assertThat(pairBetween(evaluation, A, B).inCombo()).isFalse();
        assertThat(evaluation.segments()).containsExactly(
                new RunningComboSegment(A, B, NOW.minusSeconds(60), NOW.minusSeconds(20), 4));
    }

    @Test
    @DisplayName("한쪽이 끝났으면 늦은 좌표로 시각이 신선해도 바로 끊고 구간을 닫는다")
    void evaluate_breaksImmediatelyWhenPeerFinished() {
        // given -> B는 끝냈는데 늦은 배치가 방금 들어왔다. 거리 차도 창 안이다
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_405), finished(B, 3_400));
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(60), NOW.minusSeconds(20), 4, 1));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then -> 끝난 사람과는 더 함께 달릴 수 없다
        assertThat(pairBetween(evaluation, A, B).inCombo()).isFalse();
        assertThat(evaluation.segments()).containsExactly(
                new RunningComboSegment(A, B, NOW.minusSeconds(60), NOW.minusSeconds(20), 4));
    }

    @Test
    @DisplayName("끝난 사람과는 거리 차가 창 안이어도 다시 붙지 않는다")
    void evaluate_neverRejoinsFinishedPeer() {
        // given -> 이미 끊긴 관계. B의 늦은 배치가 들어와 시각만 보면 달리는 중이다
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_405), finished(B, 3_400));
        List<RunningComboPair> stored = List.of(RunningComboPair.empty(A, B, 1));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then -> 일시정지와 달리 좌표가 와도 풀리지 않는다
        assertThat(pairBetween(evaluation, A, B).inCombo()).isFalse();
        assertThat(evaluation.update().relations()).isEmpty();
    }

    @Test
    @DisplayName("한쪽이 관측 없음이면 보류한다 — 쌓지도 깎지도 않고 화면에도 그대로 남긴다")
    void evaluate_holdsWhenPeerUnobserved() {
        // given -> B가 터널에 들어가 좌표가 30초째 안 온다. 거리 차는 이미 창 밖이다
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_500), unobserved(B, 3_400));
        RunningComboPair before = pair(NOW.minusSeconds(60), NOW.minusSeconds(30), 3, 1);

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(before), NOW, PROPERTIES);

        // then -> 사용자 잘못이 아니다. 좌표가 다시 들어오면 그때의 거리 차로 판정한다
        assertThat(pairBetween(evaluation, A, B)).isEqualTo(before);
        assertThat(evaluation.segments()).isEmpty();
        assertThat(relationBetween(evaluation, A, B).comboCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("관측 없음이 끝나 다시 겹치면 보류한 시간까지 이어서 센다")
    void evaluate_resumesAfterUnobservedWithHeldTime() {
        // given -> B가 터널을 나와 둘 다 다시 움직인다. 30초 전에 마지막으로 쌓였다
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(60), NOW.minusSeconds(30), 3, 1));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, overlapping(), stored, NOW, PROPERTIES);

        // then -> 거리 차가 창 안이면 그동안 같은 페이스로 달렸다는 뜻이다
        RunningComboPair pair = pairBetween(evaluation, A, B);
        assertThat(pair.startedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(relationBetween(evaluation, A, B).comboCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("서 있음을 관측 없음보다 먼저 본다")
    void evaluate_standingWinsOverUnobserved() {
        // given -> A는 신호 대기, B는 터널. 보류로 보면 A가 아무리 오래 서 있어도 끊기지 않는다
        List<RunningComboSnapshot> snapshots = List.of(standing(A, 3_400), unobserved(B, 3_400));
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(60), NOW.minusSeconds(20), 4, 1));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).missLeft()).isZero();
    }

    @Test
    @DisplayName("서 있는 참가자는 마지막 속도로 밀지 않는다")
    void evaluate_doesNotExtrapolateStandingPeer() {
        // given -> B는 마지막 속도가 3.2m/s지만 5초 전부터 서 있다(관측은 계속된다).
        // 밀면 36m로 창 밖, 밀지 않으면 20m다
        RunningComboSnapshot standingB = new RunningComboSnapshot(
                B, 3_420, NOW.minusSeconds(5), 3.2, LONG_AGO, NOW.minusSeconds(5), false, false);
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_400), standingB);
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(60), NOW.minusSeconds(20), 4, 1));

        // when -> 판정하지 않는 읽기 경로로 거리 차만 본다
        List<RunningComboRelation> relations =
                RunningComboEvaluator.liveRelations(snapshots, stored, NOW, PROPERTIES);

        // then -> 멈춘 사람이 계속 앞으로 가는 것으로 계산되면 안 된다
        assertThat(relations.getFirst().gapMeters()).isEqualTo(20);
    }

    // ── 쌓인 시각: 둘 다 움직였다고 확인된 마지막 순간 ──

    @Test
    @DisplayName("쌓인 시각은 두 참가자의 의심점 갱신 시각 중 이른 쪽이다")
    void evaluate_accruesUpToEarlierMovement() {
        // given -> A는 지금, B는 5초 전에 의심점이 옮겨졌다
        List<RunningComboSnapshot> snapshots = List.of(
                moving(A, 3_400),
                new RunningComboSnapshot(B, 3_420, NOW, 0, NOW.minusSeconds(5), NOW, false, false));
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(40), NOW.minusSeconds(10), 2, 1));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then -> 판정 시각으로 쌓으면 서 있음이 되기까지의 문턱 동안 신호 대기 시간이 들어간다
        assertThat(pairBetween(evaluation, A, B).accruedAt()).isEqualTo(NOW.minusSeconds(5));
        assertThat(relationBetween(evaluation, A, B).comboCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("둘 다 신호 대기로 멈추면 문턱 안이라도 쌓이지 않는다")
    void evaluate_doesNotAccrueWhileBothStopWithinFreshness() {
        // given -> 둘 다 10초 전에 의심점이 마지막으로 옮겨졌다. 아직 문턱(19초) 안이라 달리는 중이다
        Instant stoppedAt = NOW.minusSeconds(10);
        List<RunningComboSnapshot> snapshots = List.of(
                new RunningComboSnapshot(A, 3_400, NOW, 0, stoppedAt, NOW, false, false),
                new RunningComboSnapshot(B, 3_420, NOW, 0, stoppedAt, NOW, false, false));
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(70), stoppedAt, 6, 1));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then -> 겹침 판정이 나도 쌓인 시각은 멈춘 순간에 머문다
        assertThat(pairBetween(evaluation, A, B).accruedAt()).isEqualTo(stoppedAt);
        assertThat(relationBetween(evaluation, A, B).comboCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("쌓인 시각은 이미 쌓인 시각보다 앞서지 않는다 — 화면 숫자가 줄면 안 된다")
    void evaluate_neverMovesAccruedAtBackward() {
        // given -> 이미 2초 전까지 쌓였는데, 둘의 갱신 시각은 그보다 이른 5초 전이다
        Instant moved = NOW.minusSeconds(5);
        List<RunningComboSnapshot> snapshots = List.of(
                new RunningComboSnapshot(A, 3_400, NOW, 0, moved, NOW, false, false),
                new RunningComboSnapshot(B, 3_420, NOW, 0, moved, NOW, false, false));
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(40), NOW.minusSeconds(2), 3, 1));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).accruedAt()).isEqualTo(NOW.minusSeconds(2));
    }

    @Test
    @DisplayName("처음 붙을 때 쌓인 시각은 시작 시각보다 앞서지 않는다")
    void evaluate_startsAccruedAtFromStartedAt() {
        // given -> 둘 다 5초 전에 마지막으로 움직였고 지금 처음 겹친다
        Instant moved = NOW.minusSeconds(5);
        List<RunningComboSnapshot> snapshots = List.of(
                new RunningComboSnapshot(A, 3_400, NOW, 0, moved, NOW, false, false),
                new RunningComboSnapshot(B, 3_420, NOW, 0, moved, NOW, false, false));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then -> 시작보다 앞선 끝은 running_combos의 end_at > start_at을 어긴다
        RunningComboPair pair = pairBetween(evaluation, A, B);
        assertThat(pair.startedAt()).isEqualTo(NOW);
        assertThat(pair.accruedAt()).isEqualTo(NOW);
    }

    // ── 봐주기·끊김·구간 ──

    @Test
    @DisplayName("한 번 빗나가도 봐주기가 남아 있으면 콤보가 이어지고 쌓인 시각은 그대로다")
    void evaluate_spendsMissAllowanceInsteadOfBreaking() {
        // given -> 50m 벌어졌지만 봐주기가 1 남아 있다
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(40), NOW.minusSeconds(10), 8, 1));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, apart(), stored, NOW, PROPERTIES);

        // then
        RunningComboPair pair = pairBetween(evaluation, A, B);
        assertThat(pair.startedAt()).isEqualTo(NOW.minusSeconds(40));
        assertThat(pair.accruedAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(pair.missLeft()).isZero();
        // 끊기지 않았으므로 통에도 실린다. 봐주는 동안은 쌓인 시각까지만 센다
        assertThat(relationBetween(evaluation, A, B).comboCount()).isEqualTo(3);
        assertThat(evaluation.segments()).isEmpty();
    }

    @Test
    @DisplayName("봐주기가 없는 상태에서 또 빗나가면 끊기고 쌓인 시각에서 구간이 닫힌다")
    void evaluate_breaksComboWhenAllowanceExhausted() {
        // given -> 봐주기를 이미 다 쓴 관계가 또 빗나간다
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(40), NOW.minusSeconds(10), 8, 0));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, apart(), stored, NOW, PROPERTIES);

        // then
        RunningComboPair pair = pairBetween(evaluation, A, B);
        assertThat(pair.startedAt()).isNull();
        assertThat(pair.accruedAt()).isNull();
        assertThat(pair.missLeft()).isEqualTo(1);   // 다음 콤보를 위해 되돌아온다
        assertThat(pair.maxComboCount()).isEqualTo(8);   // 최고 콤보는 끊겨도 남는다
        // 끝은 끊긴 시각(지금)이 아니라 쌓인 시각이다 — 봐주는 동안은 함께 있지 않았다
        assertThat(evaluation.segments()).containsExactly(
                new RunningComboSegment(A, B, NOW.minusSeconds(40), NOW.minusSeconds(10), 3));
        // 끊긴 관계가 목록에서 빠지는 것이 곧 끊김 통지다
        assertThat(evaluation.update().relations()).isEmpty();
        // 그래도 상대는 받아야 한다 — 안 보내면 그쪽 화면에 끊긴 콤보가 남는다
        assertThat(evaluation.update().recipients()).containsExactlyInAnyOrder(A.value(), B.value());
    }

    @Test
    @DisplayName("유지 횟수가 0인 구간은 끊겨도 남기지 않는다")
    void evaluate_dropsZeroCountSegment() {
        // given -> 붙은 지 5초 만에 끊긴다
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(5), NOW.minusSeconds(5), 0, 0));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, apart(), stored, NOW, PROPERTIES);

        // then -> 함께 달린 구간이 없다
        assertThat(pairBetween(evaluation, A, B).inCombo()).isFalse();
        assertThat(evaluation.segments()).isEmpty();
    }

    @Test
    @DisplayName("쌓인 시각을 싣기 전에 저장된 관계가 끊기면 끝을 몰라 구간을 남기지 않는다")
    void evaluate_dropsSegmentOfLegacyPair() {
        // given -> 옛 포맷에서 읽은 관계 — 쌓인 시각이 없다
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(40), null, 8, 0));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, apart(), stored, NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).inCombo()).isFalse();
        assertThat(evaluation.segments()).isEmpty();
    }

    @Test
    @DisplayName("봐주는 구간에서는 최고 콤보가 오르지 않는다")
    void evaluate_keepsMaxComboWhileMissing() {
        // given -> 100초 전에 시작했지만 이번 판정은 빗나갔다
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(100), NOW.minusSeconds(70), 3, 1));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, apart(), stored, NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).maxComboCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("끊겼다 다시 겹치면 콤보는 0부터지만 최고 콤보는 살아 있다")
    void evaluate_restartsComboKeepingMax() {
        // given -> 시작 시각이 지워진 관계
        List<RunningComboPair> stored = List.of(pair(null, null, 8, 1));

        // when
        RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                A, overlapping(), stored, NOW, PROPERTIES);

        // then
        assertThat(pairBetween(evaluation, A, B).startedAt()).isEqualTo(NOW);
        RunningComboRelation relation = relationBetween(evaluation, A, B);
        assertThat(relation.comboCount()).isZero();
        assertThat(relation.maxComboCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("무관한 참가자끼리의 콤보는 건드리지 않고 통에 담기만 한다")
    void evaluate_leavesUnrelatedPairUntouched() {
        // given -> A가 보냈고 B-C는 180m 떨어져 빗나갈 거리다.
        // 그래도 A의 배치가 남의 콤보를 깎아서는 안 된다
        List<RunningComboSnapshot> snapshots = List.of(
                moving(A, 3_400), moving(B, 3_420), moving(C, 3_600));
        List<RunningComboPair> stored = List.of(
                new RunningComboPair(A, B, NOW.minusSeconds(40), NOW.minusSeconds(10), 5, 1),
                new RunningComboPair(B, C, NOW.minusSeconds(20), NOW, 4, 0));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, stored, NOW, PROPERTIES);

        // then -> 저장 대상은 A가 낀 관계 둘뿐이다
        assertThat(evaluation.pairs()).hasSize(2);
        assertThat(evaluation.pairs()).allMatch(pair -> pair.contains(A));
        // B-C는 봐주기가 0이라 B나 C가 보냈으면 끊겼을 상태인데 그대로 살아 있다
        assertThat(relationBetween(evaluation, B, C).comboCount()).isEqualTo(2);
        // 받는 사람은 A와 얽힌 참가자뿐 — C는 A와 아무 관계가 아니다
        assertThat(evaluation.update().recipients()).containsExactlyInAnyOrder(A.value(), B.value());
    }

    @Test
    @DisplayName("방금 끊긴 상대도 받는 사람에 넣는다")
    void evaluate_notifiesJustBrokenPeer() {
        // given -> A-C가 이번 판정에서 끊기고 A-B는 계속 겹친다
        List<RunningComboSnapshot> snapshots = List.of(
                moving(A, 3_400), moving(B, 3_420), moving(C, 3_600));
        List<RunningComboPair> stored = List.of(
                new RunningComboPair(A, B, NOW.minusSeconds(40), NOW.minusSeconds(10), 5, 1),
                new RunningComboPair(A, C, NOW.minusSeconds(60), NOW.minusSeconds(20), 7, 0));

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
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_400), moving(B, 3_600));

        // when
        RunningComboEvaluation evaluation =
                RunningComboEvaluator.evaluate(A, snapshots, List.of(), NOW, PROPERTIES);

        // then
        assertThat(evaluation.update().relations()).isEmpty();
        assertThat(evaluation.update().recipients()).containsExactly(A.value());
    }

    // ── liveRelations: RUNNING_STARTED 스냅샷이 쓰는 읽기 전용 경로 ──
    // 재연결한 클라는 다음 배치가 올 때까지 콤보 화면을 복구할 방법이 이것뿐이다

    @Test
    @DisplayName("살아 있는 관계만 읽어 통에 담는다")
    void liveRelations_takesOnlyLivingPairs() {
        // given -> A-B는 이어지고 있고, A-C는 끊겨 startedAt이 없다
        List<RunningComboSnapshot> snapshots = List.of(
                moving(A, 3_400), moving(B, 3_420), moving(C, 4_000));
        List<RunningComboPair> stored = List.of(
                new RunningComboPair(A, B, NOW.minusSeconds(110), NOW, 30, 1),
                new RunningComboPair(A, C, null, null, 7, 1));

        // when
        List<RunningComboRelation> relations =
                RunningComboEvaluator.liveRelations(snapshots, stored, NOW, PROPERTIES);

        // then -> 끊긴 관계가 빠지는 것이 곧 끊김 통지다
        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).first()).isEqualTo(A.value());
        assertThat(relations.get(0).second()).isEqualTo(B.value());
        // 시작부터 쌓인 시각까지 110초 = 10초짜리 11칸
        assertThat(relations.get(0).comboCount()).isEqualTo(11);
        assertThat(relations.get(0).maxComboCount()).isEqualTo(30);
        assertThat(relations.get(0).gapMeters()).isEqualTo(20);
    }

    @Test
    @DisplayName("아무 상태도 바꾸지 않아 관측 없음으로 콤보를 끊지 않는다")
    void liveRelations_doesNotBreakUnobservedCombo() {
        // given -> B가 신선도(19초)를 한참 넘겨 조용하다
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_400), unobserved(B, 3_420));
        List<RunningComboPair> stored =
                List.of(new RunningComboPair(A, B, NOW.minusSeconds(110), NOW.minusSeconds(30), 30, 1));

        // when
        List<RunningComboRelation> relations =
                RunningComboEvaluator.liveRelations(snapshots, stored, NOW, PROPERTIES);

        // then -> 끊는 판정은 배치가 도착할 때만 내린다.
        // 여기서 빼면 상대 화면에는 붙어 있는 콤보가 내 화면에서만 사라진다
        assertThat(relations).hasSize(1);
    }

    @Test
    @DisplayName("좌표를 한 번도 못 받은 참가자가 낀 관계는 거리차를 못 내 뺀다")
    void liveRelations_skipsPairWithoutSnapshot() {
        // given -> C의 스냅샷이 없다 — 보정할 누적이 없어 gapMeters를 만들 수 없다
        List<RunningComboSnapshot> snapshots = List.of(moving(A, 3_400), moving(B, 3_420));
        List<RunningComboPair> stored = List.of(
                new RunningComboPair(A, B, NOW.minusSeconds(10), NOW, 2, 1),
                new RunningComboPair(A, C, NOW.minusSeconds(10), NOW, 2, 1));

        // when
        List<RunningComboRelation> relations =
                RunningComboEvaluator.liveRelations(snapshots, stored, NOW, PROPERTIES);

        // then -> 빠뜨리는 편이 0m로 지어내는 것보다 낫다
        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).second()).isEqualTo(B.value());
    }

    @Test
    @DisplayName("저장된 관계가 없으면 빈 목록이다")
    void liveRelations_returnsEmptyWithoutStoredPairs() {
        // given & when -> 아직 아무와도 겹치지 않은 러닝의 첫 진입
        List<RunningComboRelation> relations = RunningComboEvaluator.liveRelations(
                overlapping(), List.of(), NOW, PROPERTIES);

        // then -> 판정하지 않으므로 여기서 새 콤보가 생기지는 않는다
        assertThat(relations).isEmpty();
    }

    @Test
    @DisplayName("쌓인 시각을 싣기 전 관계는 다음 겹침 판정까지 0으로 보인다")
    void liveRelations_showsZeroForLegacyPair() {
        // given
        List<RunningComboPair> stored = List.of(pair(NOW.minusSeconds(40), null, 4, 1));

        // when
        List<RunningComboRelation> relations =
                RunningComboEvaluator.liveRelations(overlapping(), stored, NOW, PROPERTIES);

        // then -> 지금 시각으로 세면 서 있던 시간까지 들어간다
        assertThat(relations.getFirst().comboCount()).isZero();
        assertThat(relations.getFirst().maxComboCount()).isEqualTo(4);
    }

    // 20m 차이 — 붙는 거리(30m) 안이다
    private static List<RunningComboSnapshot> overlapping() {
        return List.of(moving(A, 3_400), moving(B, 3_420));
    }

    // 50m 차이 — 창 밖이라 빗나간다
    private static List<RunningComboSnapshot> apart() {
        return List.of(moving(A, 3_400), moving(B, 3_450));
    }

    private static RunningComboPair pair(Instant startedAt, Instant accruedAt, int max, int missLeft) {
        return new RunningComboPair(A, B, startedAt, accruedAt, max, missLeft);
    }

    // 지금 막 관측했고 의심점도 지금 옮겨졌다 — 달리는 중
    private static RunningComboSnapshot moving(UserId userId, double meters) {
        return movingAt(userId, meters, NOW, 0);
    }

    private static RunningComboSnapshot movingAt(
            UserId userId, double meters, Instant recordedAt, double speed) {
        return new RunningComboSnapshot(
                userId, meters, recordedAt, speed, recordedAt, recordedAt, false, false);
    }

    // 관측은 지금도 들어오는데 의심점이 문턱보다 오래 그대로다 — 신호 대기
    private static RunningComboSnapshot standing(UserId userId, double meters) {
        return new RunningComboSnapshot(userId, meters, NOW, 0, LONG_AGO, NOW, false, false);
    }

    // 좌표가 문턱보다 오래 안 왔다 — 터널·연결 끊김
    private static RunningComboSnapshot unobserved(UserId userId, double meters) {
        return new RunningComboSnapshot(userId, meters, LONG_AGO, 0, LONG_AGO, LONG_AGO, false, false);
    }

    // 일시정지 표시가 있다 — 마지막 값은 달리던 중이었다
    private static RunningComboSnapshot paused(UserId userId, double meters) {
        return new RunningComboSnapshot(userId, meters, NOW.minusSeconds(3), 0,
                NOW.minusSeconds(3), NOW.minusSeconds(3), true, false);
    }

    // 종료 표시가 있다 — 늦은 좌표가 방금 들어와 시각만 보면 달리는 중이다
    private static RunningComboSnapshot finished(UserId userId, double meters) {
        return new RunningComboSnapshot(userId, meters, NOW, 0, NOW, NOW, false, true);
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
