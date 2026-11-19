package com.runiverse.running_service.infrastructure.kafka.outbox;

import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

// 다른 서비스로 보낼 이벤트 — 보내게 만든 저장과 같은 트랜잭션에서 한 줄 쓴다. 앱은 Kafka에 보내지 않는다:
// Debezium이 WAL에서 INSERT를 읽어 topic으로 보낸다(id → 헤더 id, aggregate_id → key, payload → value).
// 한 번 쓰고 고치지 않는다. 행은 남긴다 — 발행 상태 컬럼은 두지 않는다(Debezium은 이 테이블에 다시 쓰지 않는다)
@Getter
@Entity
@Table(name = "outbox_events")
@Check(name = "ck_outbox_event_aggregate_type", constraints = "aggregate_type in ('RECORD_CARD')")
// 엔티티는 모든 환경에서 같은 코드라 세 환경을 허용한다 — 운영 DDL은 prod만으로 좁힌다
@Check(name = "ck_outbox_event_topic",
        constraints = "topic ~ '^(dev|test|prod)\\.record-card\\.music\\.requested$'")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventJpaEntity extends BaseCreatedAtEntity {

    // DB가 매기지 않는다 — 어댑터가 정한 UUIDv7. 받는 쪽 inbox가 같은 이벤트를 거르는 키다
    @Id
    @Column(name = "outbox_event_id", nullable = false, updatable = false)
    private UUID outboxEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private AggregateType aggregateType;

    // Kafka key — 같은 대상(카드)의 이벤트가 같은 파티션으로 가 순서가 지켜진다. 대상마다 ID 타입이 달라 문자열이다
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 64)
    private String aggregateId;

    // 환경 접두어가 붙은 토픽 이름(test.record-card.music.requested) — Debezium이 그대로 토픽으로 쓴다
    @Column(name = "topic", nullable = false, updatable = false, length = 100)
    private String topic;

    // Kafka 메시지 본문 — 키 이름(snake_case)은 받는 쪽과 맞춘 약속이라 바꾸지 않는다. 값이 없어도 키를 빼지 않는다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    public enum AggregateType {
        RECORD_CARD
    }

    private OutboxEventJpaEntity(UUID outboxEventId, AggregateType aggregateType, String aggregateId, String topic,
                                 Map<String, Object> payload) {
        this.outboxEventId = outboxEventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.payload = payload;
    }

    public static OutboxEventJpaEntity create(UUID outboxEventId, AggregateType aggregateType, String aggregateId,
                                              String topic, Map<String, Object> payload) {
        return new OutboxEventJpaEntity(outboxEventId, aggregateType, aggregateId, topic, payload);
    }
}
