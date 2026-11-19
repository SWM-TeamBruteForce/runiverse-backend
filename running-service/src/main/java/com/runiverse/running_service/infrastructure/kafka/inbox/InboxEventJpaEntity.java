package com.runiverse.running_service.infrastructure.kafka.inbox;

import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;

// 다른 서비스에서 받아 처리한 이벤트 — 처리와 같은 트랜잭션에서 한 줄 쓴다. 같은 이벤트가 다시 오면 PK로 걸러 두 번 처리하지 않는다.
// 쓰기는 엔티티 저장이 아니라 INSERT … ON CONFLICT DO NOTHING이다(InboxEventAdapter) — 중복을 예외 없이 판정하려고.
// 한 번 쓰고 고치지 않는다. 행은 남긴다.
@Getter
@Entity
@Table(name = "inbox_events")
@IdClass(InboxEventJpaEntity.Pk.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEventJpaEntity extends BaseCreatedAtEntity {

    // 받은 토픽 — 토픽마다 이벤트 키를 정하는 쪽이 달라 키가 겹칠 수 있어 PK에 넣는다
    @Id
    @Column(name = "topic", nullable = false, updatable = false, length = 100)
    private String topic;

    // 이벤트 키 — Kafka 헤더 id(완료 이벤트는 inference_id). 보내는 쪽이 정하는 값이라 문자열이다
    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 64)
    private String eventId;

    // 이벤트 대상의 ID — Kafka key와 같다(기록 카드면 record_card_id)
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 64)
    private String aggregateId;

    // 받은 본문 그대로 — 무엇을 받았는지 DB에서 본다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {

        private String topic;
        private String eventId;
    }
}
