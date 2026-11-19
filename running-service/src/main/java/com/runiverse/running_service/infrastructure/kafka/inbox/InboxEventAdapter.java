package com.runiverse.running_service.infrastructure.kafka.inbox;

import com.runiverse.running_service.application.common.port.out.RecordInboxEventPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 받은 이벤트를 inbox에 남긴다 — 이미 있으면(같은 토픽·이벤트 키) 아무것도 쓰지 않고 false.
// 중복을 예외로 받지 않는다 — PK 충돌 예외가 나면 그 트랜잭션을 더 쓸 수 없다.
@Component
@RequiredArgsConstructor
public class InboxEventAdapter implements RecordInboxEventPort {

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO inbox_events (topic, event_id, aggregate_id, payload, created_at)
            VALUES (:topic, :eventId, :aggregateId, CAST(:payload AS jsonb), :now)
            ON CONFLICT (topic, event_id) DO NOTHING
            """;

    private final EntityManager entityManager;

    @Override
    public boolean recordIfAbsent(String topic, String eventId, String aggregateId, String payload) {
        // 저장 시각은 JVM 타임존 기준이다 — SQL의 now()는 DB 세션 타임존이라 다른 행과 어긋난다
        int inserted = entityManager.createNativeQuery(INSERT_IF_ABSENT)
                .setParameter("topic", topic)
                .setParameter("eventId", eventId)
                .setParameter("aggregateId", aggregateId)
                .setParameter("payload", payload)
                .setParameter("now", LocalDateTime.now())
                .executeUpdate();
        return inserted == 1;
    }
}
