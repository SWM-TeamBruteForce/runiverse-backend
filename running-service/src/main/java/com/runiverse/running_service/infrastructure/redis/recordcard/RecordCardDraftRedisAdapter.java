package com.runiverse.running_service.infrastructure.redis.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.LockRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.UpdateRecordCardDraftPort;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 기록 카드 초안 — 확정(9-2) 전 작성 내용과 그 확정의 잠금. 유효 시간이 지나면 Redis가 지운다
@Component
@RequiredArgsConstructor
public class RecordCardDraftRedisAdapter implements SaveRecordCardDraftPort, LoadRecordCardDraftPort,
        LockRecordCardDraftPort, UpdateRecordCardDraftPort {

    private static final String DRAFT = "draft";
    private static final String LOCK = "lock";

    // 내 토큰일 때만 지운다 — GET과 DEL을 따로 보내면 그 사이에 다른 요청이 새로 잡은 잠금을 지울 수 있다
    private static final RedisScript<Long> UNLOCK = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    // 초안 모델을 JSON으로 그대로 둔다 — 값이 30분만 살아서 모델이 바뀌어도 옮길 옛 데이터가 없다.
    // 실패하면 그대로 던진다 — 저장하지 못한 초안으로 업로드 URL을 주면 앱은 확정할 수 없는 사진을 올린다
    @Override
    public void save(RecordCardDraft draft, Duration ttl) {
        redisTemplate.opsForValue().set(key(draft.recordCardDraftId()), jsonMapper.writeValueAsString(draft), ttl);
    }

    @Override
    public Optional<RecordCardDraft> load(UUID recordCardDraftId) {
        String json = redisTemplate.opsForValue().get(key(recordCardDraftId));
        return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, RecordCardDraft.class));
    }

    // SET NX PX — 키가 없을 때만 쓴다. 명령 하나라 그 자체로 원자적이다
    @Override
    public Optional<String> tryLock(UUID recordCardDraftId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey(recordCardDraftId), token, ttl);
        return Boolean.TRUE.equals(locked) ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void unlock(UUID recordCardDraftId, String token) {
        redisTemplate.execute(UNLOCK, List.of(lockKey(recordCardDraftId)), token);
    }

    // SET XX KEEPTTL — 있을 때만 덮어쓰고 남은 유효 시간은 그대로 둔다(Redis 6 이상).
    // 그냥 SET하면 TTL이 지워져 초안이 영원히 남는다
    @Override
    public boolean update(RecordCardDraft draft) {
        byte[] key = key(draft.recordCardDraftId()).getBytes(StandardCharsets.UTF_8);
        byte[] value = jsonMapper.writeValueAsString(draft).getBytes(StandardCharsets.UTF_8);
        Boolean updated = redisTemplate.execute((RedisCallback<Boolean>) connection ->
                connection.stringCommands().set(key, value, Expiration.keepTtl(), SetOption.ifPresent()));
        return Boolean.TRUE.equals(updated);
    }

    private String key(UUID recordCardDraftId) {
        return RedisKey.RECORD_CARD.of(DRAFT, recordCardDraftId.toString());
    }

    // record-card:draft:{id}:lock — 초안 key와 따로 둔다. 초안 안에 "처리 중"을 두면 죽은 서버의 표시가 30분 남는다
    private String lockKey(UUID recordCardDraftId) {
        return RedisKey.RECORD_CARD.of(DRAFT, recordCardDraftId.toString(), LOCK);
    }
}
