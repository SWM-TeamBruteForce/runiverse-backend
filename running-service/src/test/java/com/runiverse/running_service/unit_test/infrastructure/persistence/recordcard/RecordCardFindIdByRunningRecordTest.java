package com.runiverse.running_service.unit_test.infrastructure.persistence.recordcard;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 파라미터 묶기·결과 해석만 검증한다 — JPQL은 E2E가 실제 DB로 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록으로 카드 ID 찾기 어댑터 단위 테스트")
class RecordCardFindIdByRunningRecordTest {

    private static final long RECORD_ID = 501L;
    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<UUID> query;

    @InjectMocks
    private RecordCardPersistenceAdapter adapter;

    @Test
    @DisplayName("카드가 있으면 그 카드 ID를 돌려준다")
    void foundCardId() {
        // given
        givenRows(List.of(CARD_ID));

        // when & then
        assertThat(adapter.findIdByRunningRecord(new RunningRecordId(RECORD_ID))).contains(new RecordCardId(CARD_ID));
        verify(query).setParameter("runningRecordId", RECORD_ID);
    }

    @Test
    @DisplayName("카드가 없으면 비어 있다 — 지운 카드도 행이 없다")
    void noCard() {
        // given
        givenRows(List.of());

        // when & then
        assertThat(adapter.findIdByRunningRecord(new RunningRecordId(RECORD_ID))).isEmpty();
    }

    private void givenRows(List<UUID> rows) {
        given(entityManager.createQuery(anyString(), eq(UUID.class))).willReturn(query);
        given(query.setParameter(anyString(), any())).willReturn(query);
        given(query.getResultList()).willReturn(rows);
    }
}
