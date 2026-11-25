package com.runiverse.running_service.unit_test.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardDetail;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardImageJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardPersistenceAdapter;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 읽는 순서·매핑만 검증한다 — JPQL이 실제 DB에서 도는지는 E2E가 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 상세 조회 어댑터 단위 테스트")
class RecordCardDetailLoadTest {

    private static final UUID CARD_ID = UUID.fromString("01a1ee0f-4fee-7741-a7b1-7eeb86bb369f");
    private static final UUID USER_ID = UUID.fromString("01a1e874-91e8-73b4-9680-d7f377cbd009");
    private static final UUID IMAGE_A = UUID.fromString("01a1ee0f-4fee-7d58-a7b2-925ef4d37f36");
    private static final UUID IMAGE_B = UUID.fromString("01a1ee0f-5029-7488-9b2d-c7b5d4580997");
    private static final UUID COMPANION_ID = UUID.fromString("01a1e874-91e8-73b4-9680-d7f377cbd00a");
    private static final long RECORD_ID = 55L;
    private static final String IMAGE_KEY_A = "record-cards/u/c/a/IMG_1.jpg";
    private static final String IMAGE_KEY_B = "record-cards/u/c/b/IMG_2.jpg";
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 7, 25, 19, 0, 30);

    @Mock
    private EntityManager entityManager;
    @Mock
    private TypedQuery<RecordCardJpaEntity> cardQuery;
    @Mock
    private TypedQuery<RecordCardImageJpaEntity> imageQuery;
    @Mock
    private TypedQuery<String> segmentQuery;
    @Mock
    private TypedQuery<UUID> companionQuery;

    @InjectMocks
    private RecordCardPersistenceAdapter adapter;

    @Test
    @DisplayName("카드가 없으면 비어 있고 사진·구간을 읽지 않는다")
    void emptyWhenNoCard() {
        // given
        givenCards(List.of());

        // when & then
        assertThat(adapter.loadDetail(new RecordCardId(CARD_ID))).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(RecordCardImageJpaEntity.class));
        verify(entityManager, never()).createQuery(anyString(), eq(String.class));
    }

    @Test
    @DisplayName("카드·사진(순서대로)·기록 수치·구간 경로를 읽기 모델로 옮긴다")
    void mapsDetail() {
        // given
        RunningRecordJpaEntity record = mock(RunningRecordJpaEntity.class);
        given(record.getRunningRecordId()).willReturn(RECORD_ID);
        given(record.getStartAt()).willReturn(STARTED_AT);
        given(record.getTotalDistance()).willReturn(5020);
        given(record.getTotalDuration()).willReturn(1800);
        given(record.getAvgPace()).willReturn(359);
        RecordCardJpaEntity card = RecordCardJpaEntity.create(CARD_ID, record, USER_ID, RecordCardVisibility.FRIENDS,
                RecordCardActivityType.TRAVEL, MusicStatus.COMPLETED, "한강 한 바퀴", 1, IMAGE_KEY_B, "audio/base.wav");
        givenCards(List.of(card));
        givenImages(List.of(
                RecordCardImageJpaEntity.create(IMAGE_A, card, IMAGE_KEY_A, MusicStatus.GENERATING, null, 0, "노을"),
                RecordCardImageJpaEntity.create(IMAGE_B, card, IMAGE_KEY_B, MusicStatus.COMPLETED, "audio/b.wav", 1,
                        null)));
        givenSegments(List.of("_p~iF~ps|U", "_ulLnnqC"));
        givenCompanions(List.of(COMPANION_ID));

        // when
        RecordCardDetail detail = adapter.loadDetail(new RecordCardId(CARD_ID)).orElseThrow();

        // then
        assertThat(detail.recordCardId()).isEqualTo(CARD_ID);
        assertThat(detail.userId()).isEqualTo(USER_ID);
        assertThat(detail.visibility()).isEqualTo(RecordCardVisibility.FRIENDS);
        assertThat(detail.activityType()).isEqualTo(RecordCardActivityType.TRAVEL);
        assertThat(detail.memo()).isEqualTo("한강 한 바퀴");
        assertThat(detail.thumbnailImageKey()).isEqualTo(IMAGE_KEY_B);
        assertThat(detail.baseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(detail.baseMusicKey()).isEqualTo("audio/base.wav");
        assertThat(detail.images()).containsExactly(
                new RecordCardDetail.Image(IMAGE_A, IMAGE_KEY_A, "노을", MusicStatus.GENERATING, null),
                new RecordCardDetail.Image(IMAGE_B, IMAGE_KEY_B, null, MusicStatus.COMPLETED, "audio/b.wav"));
        assertThat(detail.record()).isEqualTo(new RecordCardDetail.Record(
                RECORD_ID, STARTED_AT, 5020, 1800, 359, List.of("_p~iF~ps|U", "_ulLnnqC")));
        verify(cardQuery).setParameter("recordCardId", CARD_ID);
        verify(segmentQuery).setParameter("runningRecordId", RECORD_ID);
        // 같이 뛴 사람 — 그 방에서 출발한 참가자 중 작성자를 뺀다
        assertThat(detail.companionUserIds()).containsExactly(COMPANION_ID);
        verify(companionQuery).setParameter("authorId", USER_ID);
    }

    private void givenCards(List<RecordCardJpaEntity> cards) {
        given(entityManager.createQuery(contains("JOIN FETCH card.runningRecord"), eq(RecordCardJpaEntity.class)))
                .willReturn(cardQuery);
        given(cardQuery.setParameter(anyString(), any())).willReturn(cardQuery);
        given(cardQuery.getResultList()).willReturn(cards);
    }

    private void givenImages(List<RecordCardImageJpaEntity> images) {
        given(entityManager.createQuery(anyString(), eq(RecordCardImageJpaEntity.class))).willReturn(imageQuery);
        given(imageQuery.setParameter(anyString(), any())).willReturn(imageQuery);
        given(imageQuery.getResultList()).willReturn(images);
    }

    private void givenCompanions(List<UUID> userIds) {
        given(entityManager.createQuery(contains("FROM RunningRoomSessionJpaEntity"), eq(UUID.class)))
                .willReturn(companionQuery);
        given(companionQuery.setParameter(anyString(), any())).willReturn(companionQuery);
        given(companionQuery.getResultList()).willReturn(userIds);
    }

    private void givenSegments(List<String> polylines) {
        given(entityManager.createQuery(contains("FROM RunningSegmentJpaEntity"), eq(String.class)))
                .willReturn(segmentQuery);
        given(segmentQuery.setParameter(anyString(), any())).willReturn(segmentQuery);
        given(segmentQuery.getResultList()).willReturn(polylines);
    }
}
