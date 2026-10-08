package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.ChangeLiveRunningStatusPort;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatusChange;
import com.runiverse.running_service.application.running.port.out.LoadLiveRunningStatusPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LiveRunningStatusRedisAdapter implements ChangeLiveRunningStatusPort, LoadLiveRunningStatusPort {

    // 바꿀 수 있는 이전 상태 목록은 애플리케이션(canChangeFrom)이 정해 넘긴다 — 스크립트는 대조만 한다.
    // 바꾸기 전 값을 돌려준다. 빈 문자열은 아직 상태가 없는 참가자다
    private static final RedisScript<String> CHANGE = RedisScript.of("""
            local current = redis.call('GET', KEYS[1]) or ''
            if current == ARGV[1] then
                -- 같은 값이면 쓰지 않고 수명만 늘린다 — 오래 RUNNING인 채로 뛰어도 키가 먼저 사라지지 않는다
                redis.call('EXPIRE', KEYS[1], ARGV[2])
                return current
            end
            for i = 3, #ARGV do
                if ARGV[i] == current then
                    redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
                    return current
                end
            end
            return current
            """, String.class);
    // 스크립트와 약속한 '상태 없음' 표기 — Lua에는 null을 넘길 수 없다
    private static final String NONE = "";

    private final StringRedisTemplate redisTemplate;
    // 좌표 버퍼와 TTL을 맞춰 러닝 데이터와 같이 사라지게 한다
    private final RunningTrackProperties properties;

    // Redis 오류는 잡지 않는다 — 표시용 값이라 받는 쪽이 로그를 남기고 넘어간다(누적 거리 조회와 같은 방식)
    @Override
    public LiveRunningStatusChange change(Long runningRoomId, UserId userId, LiveRunningStatus status) {
        String previous = redisTemplate.execute(
                CHANGE, List.of(statusKey(runningRoomId, userId)), changeArgs(status).toArray());
        return LiveRunningStatusChange.of(toStatus(previous), status);
    }

    @Override
    public Optional<LiveRunningStatus> load(Long runningRoomId, UserId userId) {
        return Optional.ofNullable(toStatus(
                redisTemplate.opsForValue().get(statusKey(runningRoomId, userId))));
    }

    // [바꿀 상태, TTL(초), 허용되는 이전 상태...] 순이다
    private List<String> changeArgs(LiveRunningStatus status) {
        List<String> args = new ArrayList<>();
        args.add(status.name());
        args.add(String.valueOf(properties.ttl().toSeconds()));
        if (status.canChangeFrom(null)) {
            args.add(NONE);
        }
        for (LiveRunningStatus previous : LiveRunningStatus.values()) {
            if (status.canChangeFrom(previous)) {
                args.add(previous.name());
            }
        }
        return args;
    }

    private static LiveRunningStatus toStatus(String raw) {
        return raw == null || raw.isEmpty() ? null : LiveRunningStatus.valueOf(raw);
    }

    // 누적 거리(:dist)와 같은 접두어 아래 둔다 — 한 참가자의 러닝 데이터가 한곳에 모인다
    private String statusKey(Long runningRoomId, UserId userId) {
        return RedisKey.RUNNING_TRACK.of(
                String.valueOf(runningRoomId), userId.value().toString(), "status");
    }
}
