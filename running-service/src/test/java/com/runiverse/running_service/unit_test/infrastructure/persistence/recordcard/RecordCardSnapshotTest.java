package com.runiverse.running_service.unit_test.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardHistoryJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardPersistenceAdapter;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardImageJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

// EntityManager를 목으로 대체하므로 옮기는 값만 검증한다 — 테이블·CHECK·jsonb 저장은 E2E가 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 이력·삭제·수정 저장 어댑터 단위 테스트")
class RecordCardSnapshotTest {

    private static final UUID CARD_ID = UUID.fromString("01a1ee0f-4fee-7741-a7b1-7eeb86bb369f");
    private static final UUID USER_ID = UUID.fromString("01a1e874-91e8-73b4-9680-d7f377cbd009");
    private static final UUID IMAGE_A = UUID.fromString("01a1ee0f-4fee-7d58-a7b2-925ef4d37f36");
    private static final UUID IMAGE_B = UUID.fromString("01a1ee0f-5029-7488-9b2d-c7b5d4580997");
    private static final long RECORD_ID = 55L;
    private static final String KEY_A = "record-cards/u/c/a/IMG_1.jpg";
    private static final String KEY_B = "record-cards/u/c/b/IMG_2.jpg";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 20, 9, 0);

    @Mock
    private EntityManager entityManager;
    @Mock
    private TypedQuery<RecordCardImageJpaEntity> imageQuery;
    @Mock
    private TypedQuery<Long> lastIdQuery;

    @InjectMocks
    private RecordCardPersistenceAdapter adapter;

    @Test
    @DisplayName("저장된(고치기 전) 카드 값·사진 전체(순서·음악 포함)·원래 만든 시각을 이력 한 줄로 남긴다")
    void snapshotsStoredCard() {
        // given
        RecordCardJpaEntity cardEntity = storedCard();
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(cardEntity);
        givenImages(List.of(
                RecordCardImageJpaEntity.create(IMAGE_A, cardEntity, KEY_A, MusicStatus.COMPLETED, "audio/a.wav", 0,
                        "노을"),
                RecordCardImageJpaEntity.create(IMAGE_B, cardEntity, KEY_B, MusicStatus.GENERATING, null, 1, null)));

        // when
        adapter.snapshot(new RecordCardId(CARD_ID), RecordCardSnapshotReason.UPDATE);

        // then
        ArgumentCaptor<RecordCardHistoryJpaEntity> captor = ArgumentCaptor.forClass(RecordCardHistoryJpaEntity.class);
        verify(entityManager).persist(captor.capture());
        RecordCardHistoryJpaEntity history = captor.getValue();
        assertThat(history.getRecordCardId()).isEqualTo(CARD_ID);
        assertThat(history.getSnapshotReason()).isEqualTo(RecordCardSnapshotReason.UPDATE);
        assertThat(history.getUserId()).isEqualTo(USER_ID);
        assertThat(history.getRunningRecordId()).isEqualTo(RECORD_ID);
        assertThat(history.getVisibility()).isEqualTo(RecordCardVisibility.FRIENDS);
        assertThat(history.getActivityType()).isEqualTo(RecordCardActivityType.TRAVEL);
        assertThat(history.getMemo()).isEqualTo("한강 한 바퀴");
        assertThat(history.getThumbnailImageKey()).isEqualTo(KEY_B);
        assertThat(history.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(history.getBaseMusicKey()).isEqualTo("audio/base.wav");
        assertThat(history.getImages()).containsExactly(
                image(IMAGE_A, KEY_A, "노을", 0, "COMPLETED", "audio/a.wav"),
                image(IMAGE_B, KEY_B, null, 1, "GENERATING", null));
    }

    @Test
    @DisplayName("잠근 뒤 다시 읽은 카드가 없으면 이력을 남기지 않고 실패한다")
    void failsWithoutCard() {
        // given
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> adapter.snapshot(new RecordCardId(CARD_ID), RecordCardSnapshotReason.DELETE))
                .isInstanceOf(IllegalStateException.class);
        verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("수정 저장은 카드 값을 옮기고, 빠진 사진은 지우고, 남긴 사진은 순서·설명·음악을 옮기고, 새 사진은 넣는다 — 순위는 바꾸지 않는다")
    void updatesCardAndImages() {
        // given — 저장된 사진 A·B, 수정 뒤 B(설명 변경, GENERATING)·새 사진 C. A는 빠졌다
        RecordCardJpaEntity cardEntity = storedCard();
        RecordCardImageJpaEntity storedA = RecordCardImageJpaEntity.create(IMAGE_A, cardEntity, KEY_A,
                MusicStatus.COMPLETED, "audio/a.wav", 0, "노을");
        RecordCardImageJpaEntity storedB = RecordCardImageJpaEntity.create(IMAGE_B, cardEntity, KEY_B,
                MusicStatus.COMPLETED, "audio/b.wav", 1, null);
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(cardEntity);
        givenImages(List.of(storedA, storedB));
        UUID imageC = UUID.fromString("01a1ee0f-6000-7488-9b2d-c7b5d4580999");
        RecordCard edited = RecordCard.restore(CARD_ID)
                .runningRecordId(RECORD_ID)
                .userId(USER_ID)
                .visibility(RecordCardVisibility.PRIVATE)
                .activityType(RecordCardActivityType.RACE)
                .baseMusicStatus(MusicStatus.COMPLETED)
                .baseMusicKey("audio/base.wav")
                .memo("새 회고")
                .displayOrder(9)
                .thumbnailImageKey("record-cards/u/c/c/IMG_3.jpg")
                .images(List.of(
                        RecordCardImage.restore(IMAGE_B, KEY_B, "새 설명", null, MusicStatus.GENERATING, 0),
                        RecordCardImage.restore(imageC, "record-cards/u/c/c/IMG_3.jpg", "결승선", null,
                                MusicStatus.GENERATING, 1)))
                .build();

        // when
        adapter.update(edited);

        // then
        verify(cardEntity).apply(RecordCardVisibility.PRIVATE, RecordCardActivityType.RACE, MusicStatus.COMPLETED,
                "새 회고", 1, "record-cards/u/c/c/IMG_3.jpg", "audio/base.wav");
        verify(entityManager).remove(storedA);
        assertThat(storedB.getSortOrder()).isZero();
        assertThat(storedB.getCaption()).isEqualTo("새 설명");
        assertThat(storedB.getMusicStatus()).isEqualTo(MusicStatus.GENERATING);
        assertThat(storedB.getMusicKey()).isNull();
        ArgumentCaptor<RecordCardImageJpaEntity> added = ArgumentCaptor.forClass(RecordCardImageJpaEntity.class);
        verify(entityManager).persist(added.capture());
        assertThat(added.getValue().getRecordCardImageId()).isEqualTo(imageC);
        assertThat(added.getValue().getSortOrder()).isEqualTo(1);
        assertThat(added.getValue().getMusicStatus()).isEqualTo(MusicStatus.GENERATING);
    }

    @Test
    @DisplayName("삭제는 잠근 뒤 읽은 카드 엔티티를 지운다 — 사진 행은 DB CASCADE가 지운다")
    void deletesCardEntity() {
        // given
        RecordCardJpaEntity cardEntity = mock(RecordCardJpaEntity.class);
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(cardEntity);

        // when
        adapter.delete(new RecordCardId(CARD_ID));

        // then
        verify(entityManager).remove(cardEntity);
    }

    @Test
    @DisplayName("지울 카드가 없으면 아무것도 지우지 않고 실패한다")
    void deleteFailsWithoutCard() {
        // given
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> adapter.delete(new RecordCardId(CARD_ID))).isInstanceOf(IllegalStateException.class);
        verify(entityManager, never()).remove(any());
    }

    @Test
    @DisplayName("카드의 마지막 이력 ID를 읽고, 이력이 없으면(MAX가 null) 비어 있다")
    void loadsLastHistoryId() {
        // given
        given(entityManager.createQuery(contains("MAX(history.recordCardHistoryId)"), eq(Long.class)))
                .willReturn(lastIdQuery);
        given(lastIdQuery.setParameter("recordCardId", CARD_ID)).willReturn(lastIdQuery);
        given(lastIdQuery.getSingleResult()).willReturn(12L, (Long) null);

        // when & then
        assertThat(adapter.loadLastId(new RecordCardId(CARD_ID))).contains(12L);
        assertThat(adapter.loadLastId(new RecordCardId(CARD_ID))).isEmpty();
    }

    // 저장된 카드 엔티티 — 값은 목으로 준다(실제 엔티티의 apply 호출만 확인한다)
    private static RecordCardJpaEntity storedCard() {
        RunningRecordJpaEntity record = mock(RunningRecordJpaEntity.class);
        lenient().when(record.getRunningRecordId()).thenReturn(RECORD_ID);
        RecordCardJpaEntity card = mock(RecordCardJpaEntity.class);
        lenient().when(card.getRecordCardId()).thenReturn(CARD_ID);
        lenient().when(card.getUserId()).thenReturn(USER_ID);
        lenient().when(card.getRunningRecord()).thenReturn(record);
        lenient().when(card.getVisibility()).thenReturn(RecordCardVisibility.FRIENDS);
        lenient().when(card.getActivityType()).thenReturn(RecordCardActivityType.TRAVEL);
        lenient().when(card.getMemo()).thenReturn("한강 한 바퀴");
        lenient().when(card.getThumbnailImageKey()).thenReturn(KEY_B);
        lenient().when(card.getBaseMusicStatus()).thenReturn(MusicStatus.COMPLETED);
        lenient().when(card.getBaseMusicKey()).thenReturn("audio/base.wav");
        lenient().when(card.getDisplayOrder()).thenReturn(1);
        lenient().when(card.getCreatedAt()).thenReturn(CREATED_AT);
        return card;
    }

    private void givenImages(List<RecordCardImageJpaEntity> images) {
        given(entityManager.createQuery(anyString(), eq(RecordCardImageJpaEntity.class))).willReturn(imageQuery);
        given(imageQuery.setParameter(anyString(), any())).willReturn(imageQuery);
        given(imageQuery.getResultList()).willReturn(images);
    }

    // null 값을 담아야 해서 Map.of 대신 HashMap을 쓴다
    private static Map<String, Object> image(UUID id, String key, String caption, int sortOrder, String status,
                                             String musicKey) {
        Map<String, Object> map = new HashMap<>();
        map.put("recordCardImageId", id.toString());
        map.put("imageKey", key);
        map.put("caption", caption);
        map.put("sortOrder", sortOrder);
        map.put("musicStatus", status);
        map.put("musicKey", musicKey);
        return map;
    }
}
