package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboRelation;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RunningComboEvaluator {

    private RunningComboEvaluator() {
    }

    // 방 전체를 훑어 관계마다 판정한다. 상태를 바꾸는 것은 배치를 보낸 참가자가 낀 관계뿐이고,
    // 나머지 관계는 저장된 값을 읽어 통에 담기만 한다 —
    // 무관한 참가자의 배치가 남의 콤보를 끊어서는 안 된다.
    // snapshots에는 이번 배치를 반영한 sender의 값이 이미 들어 있어야 한다
    public static RunningComboEvaluation evaluate(
            UserId sender,
            List<RunningComboSnapshot> snapshots,
            List<RunningComboPair> stored,
            Instant now,
            RunningComboProperties properties
    ) {
        // 보정은 전원에게, 신선도는 판정에만 건다 — 오래 조용한 참가자도
        // 화면에 남은 관계의 거리차를 채워야 해서 값 자체는 필요하다
        Map<UserId, Double> corrected = new LinkedHashMap<>();
        Set<UserId> fresh = new LinkedHashSet<>();
        for (RunningComboSnapshot snapshot : snapshots) {
            corrected.put(snapshot.userId(), correctedMeters(snapshot, now));
            if (isFresh(snapshot, now, properties.freshness())) {
                fresh.add(snapshot.userId());
            }
        }
        List<RunningComboPair> pairs = new ArrayList<>();
        List<RunningComboRelation> relations = new ArrayList<>();
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(sender.value());   // 보낸 사람은 결과와 무관하게 받는다
        List<UserId> participants = corrected.keySet().stream().toList();
        for (int i = 0; i < participants.size(); i++) {
            for (int j = i + 1; j < participants.size(); j++) {
                RunningComboPair before = storedOrEmpty(
                        stored, participants.get(i), participants.get(j), properties.missAllowance());
                boolean sent = before.contains(sender);
                RunningComboPair after = sent
                        ? judge(before, corrected, fresh, now, properties)
                        : before;
                if (sent) {
                    // 저장은 상태가 바뀐 관계만 — 남의 관계를 내가 읽은 옛 값으로 덮으면,
                    // 그 사이 다른 인스턴스가 갱신한 콤보가 되돌아간다
                    pairs.add(after);
                }
                if (sent && (before.inCombo() || after.inCombo())) {
                    recipients.add(after.partnerOf(sender).value());
                }
                if (after.inCombo()) {
                    relations.add(toRelation(after, corrected, now, properties.tick()));
                }
            }
        }
        return new RunningComboEvaluation(pairs, new RunningComboUpdate(recipients, relations));
    }

    private static RunningComboPair judge(
            RunningComboPair before,
            Map<UserId, Double> corrected,
            Set<UserId> fresh,
            Instant now,
            RunningComboProperties properties) {
        // 붙을 때와 유지할 때의 창을 달리 둔다 — 창이 하나면 경계에 걸친 두 사람의 콤보가
        // 판정마다 켜졌다 꺼진다
        int window = before.inCombo() ? properties.exitMeters() : properties.enterMeters();
        boolean overlapped = fresh.contains(before.first())
                && fresh.contains(before.second())
                && Math.abs(gapMeters(before, corrected)) <= window;
        if (overlapped) {
            // 끊겨 있었다면 지금이 새 시작 시각이고 콤보는 1부터다
            Instant startedAt = before.inCombo() ? before.startedAt() : now;
            int comboCount = comboCount(startedAt, now, properties.tick());
            // 최고 콤보는 겹쳤다고 판정된 순간에만 갱신한다 — 봐주는 구간에도 시각은 흐르므로
            // 끊기는 순간의 값으로 굳히면 실제로 함께 있지 않았던 구간이 섞인다
            return new RunningComboPair(before.first(), before.second(), startedAt,
                    Math.max(before.maxComboCount(), comboCount), properties.missAllowance());
        }
        if (!before.inCombo()) {
            return before;   // 이미 끊긴 관계 — 더 깎을 것이 없다
        }
        if (before.missLeft() > 0) {
            // 상대 데이터가 낡았거나 GPS가 튄 한 번의 판정으로 오래 쌓인 콤보가 사라지면 안 된다
            return new RunningComboPair(before.first(), before.second(), before.startedAt(),
                    before.maxComboCount(), before.missLeft() - 1);
        }
        // 끊기면 시작 시각을 지운다 — 남겨두면 끊겨 있던 구간까지 함께 뛴 시간으로 세어진다.
        // 최고 콤보는 끊겨도 남는다
        return new RunningComboPair(before.first(), before.second(), null,
                before.maxComboCount(), properties.missAllowance());
    }

    // 저장된 것이 없으면 아직 한 번도 겹치지 않은 관계다 — 첫 판정도 같은 경로로 흐른다
    private static RunningComboPair storedOrEmpty(
            List<RunningComboPair> stored, UserId one, UserId other, int missAllowance) {
        RunningComboPair empty = RunningComboPair.empty(one, other, missAllowance);
        return stored.stream()
                .filter(pair -> pair.first().equals(empty.first())
                        && pair.second().equals(empty.second()))
                .findFirst()
                .orElse(empty);
    }

    // 기준 시각이 다른 누적 거리는 각자의 마지막 속도로 판정 시각까지 밀어 맞춘다 —
    // 보정하지 않으면 배치 위상차만으로 생기는 오차가 판정 창과 맞먹는다
    private static double correctedMeters(RunningComboSnapshot snapshot, Instant now) {
        long elapsedMillis = Math.max(0, Duration.between(snapshot.recordedAt(), now).toMillis());
        return snapshot.meters() + snapshot.speedMetersPerSecond() * elapsedMillis / 1000.0;
    }

    private static boolean isFresh(RunningComboSnapshot snapshot, Instant now, Duration freshness) {
        return Duration.between(snapshot.recordedAt(), now).compareTo(freshness) <= 0;
    }

    private static double gapMeters(RunningComboPair pair, Map<UserId, Double> corrected) {
        return corrected.get(pair.second()) - corrected.get(pair.first());
    }

    private static RunningComboRelation toRelation(
            RunningComboPair pair, Map<UserId, Double> corrected, Instant now, Duration tick) {
        return new RunningComboRelation(
                pair.first().value(),
                pair.second().value(),
                (int) Math.round(gapMeters(pair, corrected)),
                comboCount(pair.startedAt(), now, tick),
                pair.maxComboCount());
    }

    // 유지 횟수는 저장하지 않고 시작 이후 경과 시간을 1회 길이로 나눈 몫에 1을 더해 센다 —
    // 누가 몇 번 보내든 같은 답이 나온다
    private static int comboCount(Instant startedAt, Instant now, Duration tick) {
        long elapsedMillis = Math.max(0, Duration.between(startedAt, now).toMillis());
        return (int) (elapsedMillis / tick.toMillis()) + 1;
    }
}
