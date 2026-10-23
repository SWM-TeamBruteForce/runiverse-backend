package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.SaveRunningDistancePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateMeter;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

import static com.runiverse.running_service.infrastructure.metrics.running.RunningStatePhase.RUNNING;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateStore.REDIS;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget.DISTANCE;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningDistanceRedisAdapter implements LoadRunningDistancePort, SaveRunningDistancePort {

    private final StringRedisTemplate redisTemplate;
    private final RunningTrackProperties properties;
    private final RunningStateMeter stateMeter;
    // 누적거리|마지막순번|일시정지구간|위도|경도|페이스|의심점위도|의심점경도 — 좌표 버퍼와 TTL을 맞춰 같이 사라지게 한다
    private static final String SEPARATOR = "|";
    private static final int FIELD_COUNT = 8;

    @Override
    public RunningDistance loadDistance(Long runningRoomId, UserId userId) {
        // 읽기 실패를 빈 값으로 위장하면 안 된다 — 이어지는 저장이 성공하는 순간
        // 살아 있는 누적이 이번 배치 값으로 덮이고, 순번 스킵 때문에 이후 배치가 되돌리지 못한다.
        // 그대로 던지고 로그는 받는 쪽이 남긴다
        String raw = stateMeter.read(REDIS, DISTANCE, RUNNING, runningRoomId,
                () -> redisTemplate.opsForValue().get(distanceKey(runningRoomId, userId)));
        if (raw == null) {
            return RunningDistance.empty();   // 첫 배치
        }
        // -1을 줘야 끝이 빈 "…|null"에서 마지막 칸이 잘려나가지 않는다
        String[] fields = raw.split("\\" + SEPARATOR, -1);
        if (fields.length != FIELD_COUNT) {
            // 깨진 값은 다시 읽어도 같다 — 거리를 틀리게 세느니 처음부터 센다
            log.warn("[러닝] 누적 거리 복원 건너뜀: 저장 형식 불일치 - roomId={}, userId={}", runningRoomId, userId.value());
            return RunningDistance.empty();
        }
        try {
            return new RunningDistance(
                    Double.parseDouble(fields[0]),
                    Long.parseLong(fields[1]),
                    Integer.parseInt(fields[2]),
                    toDouble(fields[3]),
                    toDouble(fields[4]),
                    toInteger(fields[5]),
                    toDouble(fields[6]),
                    toDouble(fields[7]));
        } catch (NumberFormatException e) {
            // 깨진 값은 다시 읽어도 같다 — 형식 불일치와 같은 폴백으로 처음부터 센다
            log.warn("[러닝] 누적 거리 복원 건너뜀: 저장 값 손상 - roomId={}, userId={}", runningRoomId, userId.value());
            return RunningDistance.empty();
        }
    }

    @Override
    public void saveDistance(Long runningRoomId, UserId userId, RunningDistance distance) {
        String raw = String.format(
                Locale.ROOT, "%.2f|%d|%d|%s|%s|%s|%s|%s",
                distance.meters(),
                distance.lastSequence(),
                distance.lastPauseSegment(),
                nullable(distance.lastLatitude()),
                nullable(distance.lastLongitude()),
                nullable(distance.lastPaceSecondsPerKm()),
                nullable(distance.anchorLatitude()),
                nullable(distance.anchorLongitude()));
        try {
            stateMeter.write(REDIS, DISTANCE, RUNNING, runningRoomId, () -> redisTemplate.opsForValue().set(
                    distanceKey(runningRoomId, userId), raw, properties.ttl()));
        } catch (RuntimeException e) {
            // 저장에 실패하면 다음 배치가 이전 누적에서 이어 간다 — 실패 배치의 곡선은
            // 라이브 표시에서 빠지고(다음 배치가 직선으로 잇는다) 최종 기록이 바로잡는다
            log.error("[러닝] 누적 거리 저장 실패: Redis 오류 - roomId={}, userId={}", runningRoomId, userId.value(), e);
        }
    }

    // 좌표를 한 번도 못 받았으면 마지막 좌표·의심점이 없다
    private static String nullable(Double value) {
        return value == null ? "null" : String.format(Locale.ROOT, "%.5f", value);
    }

    // 단말이 페이스를 못 재면 좌표에 값이 없다
    private static String nullable(Integer value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static Double toDouble(String value) {
        return "null".equals(value) ? null : Double.valueOf(value);
    }

    private static Integer toInteger(String value) {
        return "null".equals(value) ? null : Integer.valueOf(value);
    }

    private String distanceKey(Long runningRoomId, UserId userId) {
        return RedisKey.RUNNING_TRACK.of(
                String.valueOf(runningRoomId), userId.value().toString(), "dist");
    }
}
