package com.runiverse.running_service.unit_test.infrastructure.persistence.recordcard;

import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.MusicKey;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardImageJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardPersistenceAdapter;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 잠그는 순서·매핑·바꾸는 컬럼만 검증한다 —
// JPQL과 FOR UPDATE가 실제 DB에서 도는지는 E2E·test 서버가 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 음악 완료용 잠금·저장 어댑터 단위 테스트")
class RecordCardMusicUpdateTest {

    private static final UUID CARD_ID = UUID.fromString("01a1ee0f-4fee-7741-a7b1-7eeb86bb369f");
    private static final UUID USER_ID = UUID.fromString("01a1e874-91e8-73b4-9680-d7f377cbd009");
    private static final UUID IMAGE_A = UUID.fromString("01a1ee0f-4fee-7d58-a7b2-925ef4d37f36");
    private static final UUID IMAGE_B = UUID.fromString("01a1ee0f-5029-7488-9b2d-c7b5d4580997");
    private static final long RECORD_ID = 55L;
    private static final String IMAGE_KEY_A = "record-cards/u/c/a/IMG_1.jpg";
    private static final String IMAGE_KEY_B = "record-cards/u/c/b/IMG_2.jpg";

    @Mock
    private EntityManager entityManager;
    @Mock
    private TypedQuery<Long> recordIdQuery;
    @Mock
    private TypedQuery<Long> lockQuery;
    @Mock
    private TypedQuery<RecordCardImageJpaEntity> imageQuery;

    @InjectMocks
    private RecordCardPersistenceAdapter adapter;

    @Test
    @DisplayName("카드가 없으면 기록을 잠그지 않고 비어 있다")
    void emptyWhenNoCard() {
        // given
        givenRecordIdOfCard(List.of());

        // when
        Optional<RecordCard> loaded = adapter.loadForUpdate(new RecordCardId(CARD_ID));

        // then
        assertThat(loaded).isEmpty();
        verify(entityManager, never()).createQuery(contains("FROM RunningRecordJpaEntity"), eq(Long.class));
        verify(entityManager, never()).find(eq(RecordCardJpaEntity.class), any());
    }

    @Test
    @DisplayName("기록 행을 FOR UPDATE로 먼저 잠근 뒤 카드와 사진을 읽어 도메인으로 옮긴다")
    void locksRecordThenLoads() {
        // given
        RecordCardJpaEntity card = cardEntity(MusicStatus.GENERATING, null);
        givenRecordIdOfCard(List.of(RECORD_ID));
        givenLock();
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(card);
        givenImages(List.of(
                imageEntity(IMAGE_A, card, IMAGE_KEY_A, MusicStatus.GENERATING, null, 0, "노을"),
                imageEntity(IMAGE_B, card, IMAGE_KEY_B, MusicStatus.COMPLETED, "audio/b.wav", 1, null)));

        // when
        RecordCard loaded = adapter.loadForUpdate(new RecordCardId(CARD_ID)).orElseThrow();

        // then
        InOrder order = inOrder(lockQuery, entityManager);
        order.verify(lockQuery).setLockMode(LockModeType.PESSIMISTIC_WRITE);
        order.verify(lockQuery).getResultList();
        order.verify(entityManager).find(RecordCardJpaEntity.class, CARD_ID);
        verify(lockQuery).setParameter("runningRecordId", RECORD_ID);

        assertThat(loaded.getRecordCardId().value()).isEqualTo(CARD_ID);
        assertThat(loaded.getRunningRecordId().value()).isEqualTo(RECORD_ID);
        assertThat(loaded.getBaseMusicStatus()).isEqualTo(MusicStatus.GENERATING);
        assertThat(loaded.getImages()).extracting(image -> image.getRecordCardImageId().value())
                .containsExactly(IMAGE_A, IMAGE_B);
        RecordCardImage second = loaded.getImages().get(1);
        assertThat(second.getMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(second.getMusicKey()).contains(new MusicKey("audio/b.wav"));
    }

    @Test
    @DisplayName("잠그는 사이 카드가 지워졌으면 비어 있다")
    void emptyWhenDeletedWhileLocking() {
        // given
        givenRecordIdOfCard(List.of(RECORD_ID));
        givenLock();
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(null);

        // when & then
        assertThat(adapter.loadForUpdate(new RecordCardId(CARD_ID))).isEmpty();
    }

    @Test
    @DisplayName("음악 상태·key만 옮기고 글·공개 범위·순위·썸네일·사진 순서·설명은 그대로 둔다")
    void savesOnlyMusic() {
        // given
        RecordCardJpaEntity card = cardEntity(MusicStatus.GENERATING, null);
        RecordCardImageJpaEntity imageA = imageEntity(IMAGE_A, card, IMAGE_KEY_A, MusicStatus.GENERATING, null, 0,
                "노을");
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(card);
        givenImages(List.of(imageA));
        RecordCard changed = RecordCard.restore(CARD_ID)
                .runningRecordId(RECORD_ID)
                .userId(USER_ID)
                // 도메인 쪽 다른 값이 달라도 음악 말고는 쓰지 않는다
                .visibility(RecordCardVisibility.PUBLIC)
                .activityType(RecordCardActivityType.RACE)
                .baseMusicStatus(MusicStatus.COMPLETED)
                .baseMusicKey("audio/base.wav")
                .memo("다른 글")
                .displayOrder(9)
                .thumbnailImageKey(IMAGE_KEY_A)
                .images(List.of(RecordCardImage.restore(IMAGE_A, IMAGE_KEY_A, "다른 설명", "audio/a.wav",
                        MusicStatus.COMPLETED, 0)))
                .build();

        // when
        adapter.saveMusic(changed);

        // then
        assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(card.getBaseMusicKey()).isEqualTo("audio/base.wav");
        assertThat(card.getVisibility()).isEqualTo(RecordCardVisibility.PRIVATE);
        assertThat(card.getActivityType()).isEqualTo(RecordCardActivityType.DAILY);
        assertThat(card.getMemo()).isEqualTo("한강 한 바퀴");
        assertThat(card.getDisplayOrder()).isEqualTo(1);
        assertThat(card.getThumbnailImageKey()).isEqualTo(IMAGE_KEY_A);
        assertThat(imageA.getMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(imageA.getMusicKey()).isEqualTo("audio/a.wav");
        assertThat(imageA.getCaption()).isEqualTo("노을");
        assertThat(imageA.getSortOrder()).isZero();
    }

    private void givenRecordIdOfCard(List<Long> result) {
        given(entityManager.createQuery(contains("card.runningRecord.runningRecordId"), eq(Long.class)))
                .willReturn(recordIdQuery);
        given(recordIdQuery.setParameter(anyString(), any())).willReturn(recordIdQuery);
        given(recordIdQuery.getResultList()).willReturn(result);
    }

    private void givenLock() {
        given(entityManager.createQuery(contains("FROM RunningRecordJpaEntity"), eq(Long.class)))
                .willReturn(lockQuery);
        given(lockQuery.setParameter(anyString(), any())).willReturn(lockQuery);
        given(lockQuery.setLockMode(any())).willReturn(lockQuery);
        given(lockQuery.getResultList()).willReturn(List.of(RECORD_ID));
    }

    private void givenImages(List<RecordCardImageJpaEntity> images) {
        given(entityManager.createQuery(anyString(), eq(RecordCardImageJpaEntity.class))).willReturn(imageQuery);
        given(imageQuery.setParameter(anyString(), any())).willReturn(imageQuery);
        given(imageQuery.getResultList()).willReturn(images);
    }

    private static RecordCardJpaEntity cardEntity(MusicStatus baseStatus, String baseKey) {
        RunningRecordJpaEntity record = mock(RunningRecordJpaEntity.class);
        return RecordCardJpaEntity.create(CARD_ID, record, USER_ID, RecordCardVisibility.PRIVATE,
                RecordCardActivityType.DAILY, baseStatus, "한강 한 바퀴", 1, IMAGE_KEY_A, baseKey);
    }

    private static RecordCardImageJpaEntity imageEntity(UUID imageId, RecordCardJpaEntity card, String imageKey,
                                                        MusicStatus status, String musicKey, int sortOrder,
                                                        String caption) {
        return RecordCardImageJpaEntity.create(imageId, card, imageKey, status, musicKey, sortOrder, caption);
    }
}
