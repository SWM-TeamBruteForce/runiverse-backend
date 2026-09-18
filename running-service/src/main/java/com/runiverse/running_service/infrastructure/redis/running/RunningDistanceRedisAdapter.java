package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.SaveRunningDistancePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningDistanceRedisAdapter implements LoadRunningDistancePort, SaveRunningDistancePort {

    private final StringRedisTemplate redisTemplate;
    private final RunningTrackProperties properties;
    // 누적거리|마지막순번|위도|경도|페이스 — 좌표 버퍼와 TTL을 맞춰 같이 사라지게 한다
    private static final String SEPARATOR = "|";
    private static final int FIELD_COUNT = 5;
    // 페이스를 싣기 전 포맷. 배포 순간 뛰고 있던 러닝의 값이 TTL 안에 남아 있어,
    // 이걸 형식 불일치로 버리면 그 사람들의 누적 거리가 통째로 0으로 되돌아간다
    private static final int LEGACY_FIELD_COUNT = 4;

    @Override
    public RunningDistance loadDistance(Long runningRoomId, UserId userId) {
        String raw;
        try {
            raw = redisTemplate.opsForValue().get(distanceKey(runningRoomId, userId));
        } catch (RuntimeException e) {
            // 읽기 실패를 빈 값으로 위장하면 안 된다 — 이어지는 저장이 성공하는 순간
            // 살아 있는 누적이 이번 배치 값으로 덮이고, 순번 스킵 때문에 이후 배치가 되돌리지 못한다
            log.warn("러닝 누적 거리 조회 실패 — roomId={}, userId={}", runningRoomId, userId, e);
            throw e;
        }
        if (raw == null) {
            return RunningDistance.empty();   // 첫 배치
        }
        // -1을 줘야 끝이 빈 "…|null"에서 마지막 칸이 잘려나가지 않는다
        String[] fields = raw.split("\\" + SEPARATOR, -1);
        if (fields.length != FIELD_COUNT && fields.length != LEGACY_FIELD_COUNT) {
            // 포맷이 바뀐 옛 값이 TTL 안에 남아 있을 수 있다 — 거리를 틀리게 세느니 처음부터 센다
            log.warn("러닝 누적 거리 형식 불일치 — roomId={}, userId={}", runningRoomId, userId);
            return RunningDistance.empty();
        }
        try {
            return new RunningDistance(
                    Double.parseDouble(fields[0]),
                    Long.parseLong(fields[1]),
                    toDouble(fields[2]),
                    toDouble(fields[3]),
                    // 옛 포맷에는 페이스 칸이 없다 — 다음 배치가 채울 때까지 null이다
                    fields.length == FIELD_COUNT ? toInteger(fields[4]) : null);
        } catch (NumberFormatException e) {
            // 깨진 값은 다시 읽어도 같다 — 형식 불일치와 같은 폴백으로 처음부터 센다
            log.warn("러닝 누적 거리 값 손상 — roomId={}, userId={}", runningRoomId, userId);
            return RunningDistance.empty();
        }
    }

    @Override
    public void saveDistance(Long runningRoomId, UserId userId, RunningDistance distance) {
        String raw = String.format(
                Locale.ROOT, "%.2f|%d|%s|%s|%s",
                distance.meters(),
                distance.lastSequence(),
                nullable(distance.lastLatitude()),
                nullable(distance.lastLongitude()),
                nullable(distance.lastPaceSecondsPerKm()));
        try {
            redisTemplate.opsForValue().set(
                    distanceKey(runningRoomId, userId), raw, properties.ttl());
        } catch (RuntimeException e) {
            // 저장에 실패하면 다음 배치가 이전 누적에서 이어 간다 — 실패 배치의 곡선은
            // 라이브 표시에서 빠지고(다음 배치가 직선으로 잇는다) 최종 기록이 바로잡는다
            log.warn("러닝 누적 거리 저장 실패 — roomId={}, userId={}", runningRoomId, userId, e);
        }
    }

    // 좌표를 한 번도 못 받았으면 마지막 좌표가 없다
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
