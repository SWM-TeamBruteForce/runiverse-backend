package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboRelation;
import com.runiverse.running_service.application.running.port.out.RunningComboSegment;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.runiverse.running_service.application.running.command.combo.RunningComboPresence.FINISHED;
import static com.runiverse.running_service.application.running.command.combo.RunningComboPresence.MOVING;
import static com.runiverse.running_service.application.running.command.combo.RunningComboPresence.PAUSED;
import static com.runiverse.running_service.application.running.command.combo.RunningComboPresence.STANDING;
import static com.runiverse.running_service.application.running.command.combo.RunningComboPresence.UNOBSERVED;

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
        Room room = Room.of(snapshots, now, properties.freshness());
        List<RunningComboPair> pairs = new ArrayList<>();
        List<RunningComboSegment> segments = new ArrayList<>();
        List<RunningComboRelation> relations = new ArrayList<>();
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(sender.value());   // 보낸 사람은 결과와 무관하게 받는다
        List<UserId> participants = room.participants();
        for (int i = 0; i < participants.size(); i++) {
            for (int j = i + 1; j < participants.size(); j++) {
                RunningComboPair before = storedOrEmpty(
                        stored, participants.get(i), participants.get(j), properties.missAllowance());
                boolean sent = before.contains(sender);
                RunningComboPair after = sent
                        ? judge(before, room, now, properties)
                        : before;
                if (sent) {
                    // 저장은 상태가 바뀐 관계만 — 남의 관계를 내가 읽은 옛 값으로 덮으면,
                    // 그 사이 다른 인스턴스가 갱신한 콤보가 되돌아간다
                    pairs.add(after);
                    // 이번 판정으로 끊겼으면 이어지던 구간을 닫는다
                    if (before.inCombo() && !after.inCombo()) {
                        segmentOf(before, properties.tick()).ifPresent(segments::add);
                    }
                }
                if (sent && (before.inCombo() || after.inCombo())) {
                    recipients.add(after.partnerOf(sender).value());
                }
                if (after.inCombo()) {
                    relations.add(toRelation(after, room, properties.tick()));
                }
            }
        }
        return new RunningComboEvaluation(pairs, segments, new RunningComboUpdate(recipients, relations));
    }

    // 판정하지 않고 지금 살아 있는 관계만 읽어 돌려준다 — RUNNING_STARTED 스냅샷이 쓴다.
    // evaluate()와 달리 어떤 상태도 바꾸지 않으므로 저장할 pairs가 없다.
    // 상태로 거르지 않는 것이 중요하다: 끊는 판정은 배치가 도착할 때만 내리므로,
    // 여기서 조용한 참가자를 빼면 상대 화면에는 아직 붙어 있는 콤보가 내 화면에서만 사라진다
    public static List<RunningComboRelation> liveRelations(
            List<RunningComboSnapshot> snapshots,
            List<RunningComboPair> stored,
            Instant now,
            RunningComboProperties properties
    ) {
        Room room = Room.of(snapshots, now, properties.freshness());
        return stored.stream()
                .filter(RunningComboPair::inCombo)
                // 좌표를 한 번도 못 받은 참가자는 보정할 누적이 없어 거리차를 못 낸다
                .filter(pair -> room.has(pair.first()) && room.has(pair.second()))
                .map(pair -> toRelation(pair, room, properties.tick()))
                .toList();
    }

    // 위에서부터 먼저 걸리는 것으로 처리한다
    private static RunningComboPair judge(
            RunningComboPair before, Room room, Instant now, RunningComboProperties properties) {
        RunningComboPresence first = room.presence(before.first());
        RunningComboPresence second = room.presence(before.second());
        if (first == FINISHED || second == FINISHED || first == PAUSED || second == PAUSED) {
            // 끝났거나 일시정지면 봐주지 않고 바로 끊는다 — 종료는 다시 붙지 않고,
            // 일시정지는 사용자의 선택이라 다시 달리면 새 구간이다
            return before.inCombo() ? broken(before, properties) : before;
        }
        if (first == STANDING || second == STANDING) {
            // 한쪽이 서 있으면 함께 달린 것이 아니다 — 다른 쪽이 터널에 있어도 기다려 줄 이유가 없다
            return missed(before, properties);
        }
        if (first == UNOBSERVED || second == UNOBSERVED) {
            // 터널·연결 끊김은 사용자 잘못이 아니다 — 쌓지도 깎지도 않고 좌표가 다시 들어오기를 기다린다.
            // 돌아오면 누적이 따라잡은 뒤의 거리 차로 다시 판정한다
            return before;
        }
        // 붙을 때와 유지할 때의 창을 달리 둔다 — 창이 하나면 경계에 걸친 두 사람의 콤보가
        // 판정마다 켜졌다 꺼진다
        int window = before.inCombo() ? properties.exitMeters() : properties.enterMeters();
        if (Math.abs(room.gapMeters(before)) > window) {
            return missed(before, properties);
        }
        // 끊겨 있었다면 지금이 새 시작 시각이고 콤보는 0부터 센다
        Instant startedAt = before.inCombo() ? before.startedAt() : now;
        Instant accruedAt = accruedAt(before, startedAt, room);
        int comboCount = comboCount(startedAt, accruedAt, properties.tick());
        // 최고 콤보는 겹쳤다고 판정된 순간에만, 쌓인 시각으로 갱신한다 — 봐주는 구간이나
        // 서 있던 시간까지 굳히면 실제로 함께 달리지 않은 구간이 섞인다
        return new RunningComboPair(before.first(), before.second(), startedAt, accruedAt,
                Math.max(before.maxComboCount(), comboCount), properties.missAllowance());
    }

    // 쌓인 시각 — 두 참가자의 의심점 갱신 시각 중 이른 쪽. 둘 다 움직였다고 확인된 마지막 순간이다.
    // 판정 시각으로 쌓으면 서 있음이 되기까지의 문턱 동안 신호 대기 시간이 함께 뛴 시간으로 들어간다.
    // 시작 시각보다, 이미 쌓인 시각보다 앞서지 않는다 — 화면에 뜬 숫자가 줄어들면 안 된다
    private static Instant accruedAt(RunningComboPair before, Instant startedAt, Room room) {
        // 둘 다 달리는 중이라 갱신 시각이 있다
        Instant firstMoved = room.movedAt(before.first());
        Instant secondMoved = room.movedAt(before.second());
        Instant bothMoved = firstMoved.isBefore(secondMoved) ? firstMoved : secondMoved;
        Instant floor = before.inCombo() && before.accruedAt() != null
                && before.accruedAt().isAfter(startedAt)
                ? before.accruedAt()
                : startedAt;
        return bothMoved.isAfter(floor) ? bothMoved : floor;
    }

    // 상대 데이터가 낡았거나 GPS가 튄 한 번의 판정으로 오래 쌓인 콤보가 사라지면 안 된다.
    // 봐주는 동안은 겹친 것이 아니다 — 쌓인 시각을 그대로 둔다
    private static RunningComboPair missed(RunningComboPair before, RunningComboProperties properties) {
        if (!before.inCombo()) {
            return before;   // 이미 끊긴 관계 — 더 깎을 것이 없다
        }
        if (before.missLeft() > 0) {
            return new RunningComboPair(before.first(), before.second(), before.startedAt(),
                    before.accruedAt(), before.maxComboCount(), before.missLeft() - 1);
        }
        return broken(before, properties);
    }

    // 끊기면 시작 시각과 쌓인 시각을 지운다 — 남겨두면 끊겨 있던 구간까지 함께 뛴 시간으로 세어진다.
    // 최고 콤보는 끊겨도 남는다
    private static RunningComboPair broken(RunningComboPair before, RunningComboProperties properties) {
        return new RunningComboPair(before.first(), before.second(), null, null,
                before.maxComboCount(), properties.missAllowance());
    }

    // 끊긴 관계를 구간 하나로 닫는다. 끝은 끊긴 시각이 아니라 쌓인 시각이다.
    // 유지 횟수가 0이면 붙자마자 끊겨 함께 달린 구간이 없다 — 남기지 않는다.
    // 방이 닫힐 때 이어지던 관계를 닫는 데도 같은 규칙을 쓴다
    public static Optional<RunningComboSegment> segmentOf(RunningComboPair before, Duration tick) {
        if (before.accruedAt() == null) {
            return Optional.empty();   // 쌓인 시각을 싣기 전에 저장된 관계 — 끝을 알 수 없다
        }
        int comboCount = comboCount(before.startedAt(), before.accruedAt(), tick);
        if (comboCount < 1) {
            return Optional.empty();
        }
        return Optional.of(new RunningComboSegment(before.first(), before.second(),
                before.startedAt(), before.accruedAt(), comboCount));
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

    private static RunningComboRelation toRelation(RunningComboPair pair, Room room, Duration tick) {
        return new RunningComboRelation(
                pair.first().value(),
                pair.second().value(),
                (int) Math.round(room.gapMeters(pair)),
                // 쌓인 시각을 싣기 전 관계는 다음 겹침 판정까지 0으로 보인다
                pair.accruedAt() == null ? 0 : comboCount(pair.startedAt(), pair.accruedAt(), tick),
                pair.maxComboCount());
    }

    // 유지 횟수는 저장하지 않고 시작 시각부터 쌓인 시각까지를 1회 길이로 나눈 몫으로 센다 —
    // 누가 몇 번 보내든 같은 답이 나온다.
    // 붙는 순간은 0이다: 출발선에 나란히 선 것만으로 1을 주면 아직 함께 달리지도 않은 구간을 세게 된다
    private static int comboCount(Instant startedAt, Instant accruedAt, Duration tick) {
        long elapsedMillis = Math.max(0, Duration.between(startedAt, accruedAt).toMillis());
        return (int) (elapsedMillis / tick.toMillis());
    }

    // 판정 시각에 본 방 — 참가자마다 상태와 보정한 누적 거리를 한 번만 계산해 둔다
    private record Room(
            Map<UserId, RunningComboSnapshot> snapshots,
            Map<UserId, RunningComboPresence> presences,
            Map<UserId, Double> corrected
    ) {

        static Room of(List<RunningComboSnapshot> snapshots, Instant now, Duration freshness) {
            Map<UserId, RunningComboSnapshot> byUser = new LinkedHashMap<>();
            Map<UserId, RunningComboPresence> presences = new LinkedHashMap<>();
            Map<UserId, Double> corrected = new LinkedHashMap<>();
            for (RunningComboSnapshot snapshot : snapshots) {
                RunningComboPresence presence = RunningComboPresence.of(snapshot, now, freshness);
                byUser.put(snapshot.userId(), snapshot);
                presences.put(snapshot.userId(), presence);
                corrected.put(snapshot.userId(), correctedMeters(snapshot, presence, now));
            }
            return new Room(byUser, presences, corrected);
        }

        List<UserId> participants() {
            return snapshots.keySet().stream().toList();
        }

        boolean has(UserId userId) {
            return snapshots.containsKey(userId);
        }

        RunningComboPresence presence(UserId userId) {
            return presences.get(userId);
        }

        Instant movedAt(UserId userId) {
            return snapshots.get(userId).movedAt();
        }

        double gapMeters(RunningComboPair pair) {
            return corrected.get(pair.second()) - corrected.get(pair.first());
        }

        // 기준 시각이 다른 누적 거리는 각자의 마지막 속도로 판정 시각까지 밀어 맞춘다 —
        // 보정하지 않으면 배치 위상차만으로 생기는 오차가 판정 창과 맞먹는다.
        // 달리는 중인 참가자만 민다: 서 있거나 관측이 없는 사람을 밀면 멈춘 사람이 계속 앞으로 간다
        private static double correctedMeters(
                RunningComboSnapshot snapshot, RunningComboPresence presence, Instant now) {
            if (presence != MOVING) {
                return snapshot.meters();
            }
            long elapsedMillis = Math.max(0, Duration.between(snapshot.recordedAt(), now).toMillis());
            return snapshot.meters() + snapshot.speedMetersPerSecond() * elapsedMillis / 1000.0;
        }
    }
}
