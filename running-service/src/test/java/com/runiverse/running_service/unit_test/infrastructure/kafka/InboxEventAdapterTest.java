package com.runiverse.running_service.unit_test.infrastructure.kafka;

import com.runiverse.running_service.infrastructure.kafka.inbox.InboxEventAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("inbox 이벤트 어댑터 단위 테스트")
class InboxEventAdapterTest {

    private static final String TOPIC = "test.record-card.music.generated";
    private static final String EVENT_ID = "1c298034-bf6f-4f06-a983-dc3b59822e74";
    private static final String CARD_ID = "01a1ee0f-4fee-7741-a7b1-7eeb86bb369f";
    private static final String PAYLOAD = "{\"inference_id\":\"1c298034-bf6f-4f06-a983-dc3b59822e74\"}";

    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    private InboxEventAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InboxEventAdapter(entityManager);
        given(entityManager.createNativeQuery(anyString())).willReturn(query);
        given(query.setParameter(anyString(), any())).willReturn(query);
    }

    @Test
    @DisplayName("한 줄이 들어가면 처음 받은 이벤트다")
    void firstEvent() {
        // given
        given(query.executeUpdate()).willReturn(1);

        // when
        boolean recorded = adapter.recordIfAbsent(TOPIC, EVENT_ID, CARD_ID, PAYLOAD);

        // then
        assertThat(recorded).isTrue();
    }

    @Test
    @DisplayName("충돌해 아무것도 들어가지 않으면 이미 처리한 이벤트다 — 예외가 아니라 false다")
    void duplicateEvent() {
        // given
        given(query.executeUpdate()).willReturn(0);

        // when
        boolean recorded = adapter.recordIfAbsent(TOPIC, EVENT_ID, CARD_ID, PAYLOAD);

        // then
        assertThat(recorded).isFalse();
    }

    @Test
    @DisplayName("토픽·이벤트 키가 PK인 ON CONFLICT DO NOTHING으로 본문을 jsonb로 넣는다")
    void insertsIfAbsent() {
        // given
        given(query.executeUpdate()).willReturn(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        // when
        adapter.recordIfAbsent(TOPIC, EVENT_ID, CARD_ID, PAYLOAD);

        // then
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
                .contains("INSERT INTO inbox_events (topic, event_id, aggregate_id, payload, created_at)")
                .contains("CAST(:payload AS jsonb)")
                .contains("ON CONFLICT (topic, event_id) DO NOTHING");
        verify(query).setParameter("topic", TOPIC);
        verify(query).setParameter("eventId", EVENT_ID);
        verify(query).setParameter("aggregateId", CARD_ID);
        verify(query).setParameter("payload", PAYLOAD);
        verify(query).setParameter(eq("now"), any(LocalDateTime.class));
    }
}
