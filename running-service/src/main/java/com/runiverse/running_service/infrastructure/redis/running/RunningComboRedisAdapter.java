package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.LoadRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSnapshotPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningComboRedisAdapter implements
        LoadRunningComboSnapshotsPort,
        SaveRunningComboSnapshotPort,
        LoadRunningComboPairsPort,
        SaveRunningComboPairsPort {

    private final StringRedisTemplate redisTemplate;
    // 콤보 상태는 좌표 버퍼와 같은 러닝 하나의 수명을 산다 — 러닝이 끝나면 함께 사라진다
    private final RunningTrackProperties properties;
    // 스냅샷은 누적거리|기준시각|속도, 관계는 시작시각|최고콤보|남은봐주기 — 방마다 해시 하나다
    private static final String SEPARATOR = "|";
    private static final String SEPARATOR_PATTERN = "\\|";
    private static final String NULL = "null";
    private static final int FIELD_COUNT = 3;
    private static final int PAIR_USER_COUNT = 2;

    @Override
    public List<RunningComboSnapshot> loadSnapshots(Long runningRoomId) {
        return load(snapshotKey(runningRoomId), "스냅샷", this::toSnapshot);
    }

    @Override
    public void saveSnapshot(Long runningRoomId, RunningComboSnapshot snapshot) {
        String value = String.format(Locale.ROOT, "%.2f|%d|%.3f",
                snapshot.meters(),
                snapshot.recordedAt().toEpochMilli(),
                snapshot.speedMetersPerSecond());
        save(snapshotKey(runningRoomId), Map.of(snapshot.userId().value().toString(), value),
                "스냅샷", runningRoomId);
    }

    @Override
    public List<RunningComboPair> loadPairs(Long runningRoomId) {
        return load(pairKey(runningRoomId), "관계", this::toPair);
    }

    @Override
    public void savePairs(Long runningRoomId, List<RunningComboPair> pairs) {
        if (pairs.isEmpty()) {
            return;   // 아직 아무와도 겹치지 않은 참가자 — 저장할 상태가 없다
        }
        save(pairKey(runningRoomId),
                pairs.stream().collect(Collectors.toMap(
                        RunningComboRedisAdapter::pairField, RunningComboRedisAdapter::pairValue)),
                "관계", runningRoomId);
    }

    private <T> List<T> load(
            String key, String label, Function<Map.Entry<String, String>, Optional<T>> mapper) {
        Map<String, String> entries;
        try {
            entries = redisTemplate.<String, String>opsForHash().entries(key);
        } catch (RuntimeException e) {
            // 읽기 실패를 빈 목록으로 위장하면 안 된다 — 비교 상대가 사라져
            // 살아 있던 콤보와 최고 기록이 이번 배치의 저장으로 지워진다
            log.warn("러닝 콤보 {} 조회 실패 — key={}", label, key, e);
            throw e;
        }
        return entries.entrySet().stream()
                .map(mapper)
                .flatMap(Optional::stream)
                .toList();
    }

    private void save(String key, Map<String, String> entries, String label, Long runningRoomId) {
        try {
            redisTemplate.<String, String>opsForHash().putAll(key, entries);
            // HSET은 TTL을 건드리지 않는다 — 쓸 때마다 다시 걸지 않으면
            // 처음 만든 시각으로부터 만료돼 러닝 도중에 방 전체가 통째로 사라진다
            redisTemplate.expire(key, properties.ttl());
        } catch (RuntimeException e) {
            // 저장에 실패하면 다음 배치가 이전 상태에서 이어 판정한다
            log.warn("러닝 콤보 {} 저장 실패 — roomId={}", label, runningRoomId, e);
        }
    }

    private Optional<RunningComboSnapshot> toSnapshot(Map.Entry<String, String> entry) {
        String[] fields = entry.getValue().split(SEPARATOR_PATTERN);
        if (fields.length != FIELD_COUNT) {
            log.warn("러닝 콤보 스냅샷 형식 불일치 — field={}", entry.getKey());
            return Optional.empty();
        }
        try {
            return Optional.of(new RunningComboSnapshot(
                    new UserId(UUID.fromString(entry.getKey())),
                    Double.parseDouble(fields[0]),
                    Instant.ofEpochMilli(Long.parseLong(fields[1])),
                    Double.parseDouble(fields[2])));
        } catch (IllegalArgumentException e) {
            // 깨진 값은 다시 읽어도 같다 — 이 참가자만 비교에서 빼고 다음 배치가 제 값으로 덮는다
            log.warn("러닝 콤보 스냅샷 값 손상 — field={}", entry.getKey());
            return Optional.empty();
        }
    }

    private Optional<RunningComboPair> toPair(Map.Entry<String, String> entry) {
        String[] users = entry.getKey().split(SEPARATOR_PATTERN);
        String[] fields = entry.getValue().split(SEPARATOR_PATTERN);
        if (users.length != PAIR_USER_COUNT || fields.length != FIELD_COUNT) {
            log.warn("러닝 콤보 관계 형식 불일치 — field={}", entry.getKey());
            return Optional.empty();
        }
        try {
            return Optional.of(new RunningComboPair(
                    new UserId(UUID.fromString(users[0])),
                    new UserId(UUID.fromString(users[1])),
                    toInstant(fields[0]),
                    Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2])));
        } catch (IllegalArgumentException e) {
            // 이 관계만 처음부터 다시 센다 — 거리를 틀리게 세느니 1부터가 낫다
            log.warn("러닝 콤보 관계 값 손상 — field={}", entry.getKey());
            return Optional.empty();
        }
    }

    private static String pairField(RunningComboPair pair) {
        // 정렬은 RunningComboPair가 보장한다 — A-B와 B-A가 같은 칸을 가리킨다
        return pair.first().value() + SEPARATOR + pair.second().value();
    }

    private static String pairValue(RunningComboPair pair) {
        return nullable(pair.startedAt()) + SEPARATOR
                + pair.maxComboCount() + SEPARATOR
                + pair.missLeft();
    }

    // 끊긴 관계는 시작 시각이 없다. 그래도 지우지 않고 남긴다 — 최고 콤보를 들고 있어야 한다
    private static String nullable(Instant startedAt) {
        return startedAt == null ? NULL : String.valueOf(startedAt.toEpochMilli());
    }

    private static Instant toInstant(String value) {
        return NULL.equals(value) ? null : Instant.ofEpochMilli(Long.parseLong(value));
    }

    private String snapshotKey(Long runningRoomId) {
        return RedisKey.RUNNING_COMBO.of(String.valueOf(runningRoomId), "dist");
    }

    private String pairKey(Long runningRoomId) {
        return RedisKey.RUNNING_COMBO.of(String.valueOf(runningRoomId), "pair");
    }
}
