package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.LockRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.UpdateRecordCardDraftPort;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Redis 초안과 확정 잠금 — 만료는 흉내 내지 않고 받은 유효 시간만 기록한다
public class InMemoryRecordCardDraftStore implements SaveRecordCardDraftPort, LoadRecordCardDraftPort,
        LockRecordCardDraftPort, UpdateRecordCardDraftPort {

    private final Map<UUID, RecordCardDraft> drafts = new HashMap<>();
    private final Map<UUID, Duration> ttls = new HashMap<>();
    private final Map<UUID, String> locks = new HashMap<>();
    private int tokenSequence = 0;

    @Override
    public void save(RecordCardDraft draft, Duration ttl) {
        drafts.put(draft.recordCardDraftId(), draft);
        ttls.put(draft.recordCardDraftId(), ttl);
    }

    @Override
    public Optional<RecordCardDraft> load(UUID recordCardDraftId) {
        return Optional.ofNullable(drafts.get(recordCardDraftId));
    }

    @Override
    public Optional<String> tryLock(UUID recordCardDraftId, Duration ttl) {
        if (locks.containsKey(recordCardDraftId)) {
            return Optional.empty();
        }
        String token = "token-" + (++tokenSequence);
        locks.put(recordCardDraftId, token);
        return Optional.of(token);
    }

    @Override
    public void unlock(UUID recordCardDraftId, String token) {
        locks.remove(recordCardDraftId, token);
    }

    // 있을 때만 덮어쓴다 — 유효 시간은 그대로다(여기서는 ttls를 건드리지 않는다)
    @Override
    public boolean update(RecordCardDraft draft) {
        if (!drafts.containsKey(draft.recordCardDraftId())) {
            return false;
        }
        drafts.put(draft.recordCardDraftId(), draft);
        return true;
    }

    // 아래는 준비·검증 전용
    public Optional<RecordCardDraft> find(UUID recordCardDraftId) {
        return load(recordCardDraftId);
    }

    public Duration ttlOf(UUID recordCardDraftId) {
        return ttls.get(recordCardDraftId);
    }

    public boolean isEmpty() {
        return drafts.isEmpty();
    }

    public boolean isLocked(UUID recordCardDraftId) {
        return locks.containsKey(recordCardDraftId);
    }

    public void holdLock(UUID recordCardDraftId) {
        locks.put(recordCardDraftId, "someone-else");
    }

    public void expire(UUID recordCardDraftId) {
        drafts.remove(recordCardDraftId);
    }
}
