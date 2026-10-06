package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.AcquireSendCooldownPort;
import com.runiverse.running_service.application.auth.port.out.CheckDailySendLimitPort;
import com.runiverse.running_service.application.auth.port.out.ConsumeVerificationAttemptPort;
import com.runiverse.running_service.application.auth.port.out.DeleteVerificationCodePort;
import com.runiverse.running_service.application.auth.port.out.ReleaseSendCooldownPort;
import com.runiverse.running_service.application.auth.port.out.SaveVerificationCodePort;
import com.runiverse.running_service.application.auth.port.out.VerificationAttempt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// EmailVerificationRedisAdapter와 같은 규칙으로 동작한다.
// consume은 consume_verification_attempt.lua의 흐름을 그대로 옮긴 것이다
public class InMemoryEmailVerificationStore implements AcquireSendCooldownPort, ReleaseSendCooldownPort,
        CheckDailySendLimitPort, SaveVerificationCodePort, DeleteVerificationCodePort, ConsumeVerificationAttemptPort {

    private final int maxAttempts;
    private final int dailyLimit;
    private final Set<String> cooldowns = new HashSet<>();
    private final Map<String, Integer> dailyCounts = new HashMap<>();
    private final Map<String, Entry> codes = new HashMap<>();

    public InMemoryEmailVerificationStore(int maxAttempts, int dailyLimit) {
        this.maxAttempts = maxAttempts;
        this.dailyLimit = dailyLimit;
    }

    // 해시와 시도 횟수를 한 덩어리로 묶는다. Redis 쪽 Hash 자료구조와 같은 모양이다
    private static final class Entry {

        private final String hashedCode;
        private int attempts;

        private Entry(String hashedCode) {
            this.hashedCode = hashedCode;
        }
    }

    // SET NX와 같다. 이미 잡혀 있으면 실패한다
    @Override
    public boolean tryAcquire(String email) {
        return cooldowns.add(email);
    }

    @Override
    public void release(String email) {
        cooldowns.remove(email);
    }

    // INCR과 같다. 한도를 넘어도 카운트 자체는 올라간다
    @Override
    public boolean tryConsume(String email) {
        return dailyCounts.merge(email, 1, Integer::sum) <= dailyLimit;
    }

    // 재발송이면 이전 코드와 시도 횟수를 덮어쓴다
    @Override
    public void save(String email, String hashedCode) {
        codes.put(email, new Entry(hashedCode));
    }

    @Override
    public void delete(String email) {
        codes.remove(email);
    }

    @Override
    public VerificationAttempt consume(String email) {
        Entry entry = codes.get(email);
        if (entry == null) {
            return new VerificationAttempt(VerificationAttempt.Status.NOT_FOUND, null);
        }

        entry.attempts++;
        // 대조하기 전에 먼저 소비한다. 틀린 코드로 무한히 두드릴 수 없게 하기 위해서다
        if (entry.attempts > maxAttempts) {
            codes.remove(email);
            return new VerificationAttempt(VerificationAttempt.Status.EXHAUSTED, null);
        }
        return new VerificationAttempt(VerificationAttempt.Status.AVAILABLE, entry.hashedCode);
    }

    // 아래는 검증 전용
    public boolean hasCooldown(String email) {
        return cooldowns.contains(email);
    }

    public boolean hasCode(String email) {
        return codes.containsKey(email);
    }

    public String storedHash(String email) {
        Entry entry = codes.get(email);
        return entry == null ? null : entry.hashedCode;
    }

    public int attempts(String email) {
        Entry entry = codes.get(email);
        return entry == null ? 0 : entry.attempts;
    }

    public int dailyCount(String email) {
        return dailyCounts.getOrDefault(email, 0);
    }
}
