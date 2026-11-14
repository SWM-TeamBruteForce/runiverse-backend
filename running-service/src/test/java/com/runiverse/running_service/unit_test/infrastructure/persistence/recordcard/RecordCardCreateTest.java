package com.runiverse.running_service.unit_test.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.CreateRecordCardPort.CreateResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageDraft;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardImageJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardPersistenceAdapter;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 매핑·flush·제약 이름 해석만 검증한다 —
// 실제 DB가 이 제약 이름을 돌려주는지와 지연 UNIQUE가 flush에서 걸리지 않는지는 E2E가 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 저장 어댑터 단위 테스트")
class RecordCardCreateTest {

    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
    private static final UUID IMAGE_ID_1 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");
    private static final UUID IMAGE_ID_2 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544013e");
    private static final long RECORD_ID = 501L;

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Integer> maxQuery;

    @InjectMocks
    private RecordCardPersistenceAdapter adapter;

    @Test
    @DisplayName("가장 큰 순위를 사용자 ID로 읽고, 카드가 없어 MAX가 null이면 비어 있다")
    void loadMax() {
        // given
        given(entityManager.createQuery(anyString(), eq(Integer.class))).willReturn(maxQuery);
        given(maxQuery.setParameter(anyString(), any())).willReturn(maxQuery);
        given(maxQuery.getSingleResult()).willReturn(3, (Integer) null);

        // when & then
        assertThat(adapter.loadMax(new UserId(USER_ID))).contains(new DisplayOrder(3));
        assertThat(adapter.loadMax(new UserId(USER_ID))).isEmpty();
        verify(maxQuery, times(2)).setParameter("userId", USER_ID);
    }

    @Test
    @DisplayName("카드와 사진을 정해진 ID 그대로 넣고, 그 자리에서 flush해 CREATED를 돌려준다")
    void createPersistsCardAndImages() {
        // given
        RunningRecordJpaEntity recordProxy = mock(RunningRecordJpaEntity.class);
        given(entityManager.getReference(RunningRecordJpaEntity.class, RECORD_ID)).willReturn(recordProxy);

        // when
        CreateResult result = adapter.create(card());

        // then
        assertThat(result).isEqualTo(CreateResult.CREATED);
        ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
        verify(entityManager, times(3)).persist(persisted.capture());
        verify(entityManager).flush();

        RecordCardJpaEntity cardEntity = (RecordCardJpaEntity) persisted.getAllValues().get(0);
        assertThat(cardEntity.getRecordCardId()).isEqualTo(CARD_ID);
        assertThat(cardEntity.getRunningRecord()).isSameAs(recordProxy);
        assertThat(cardEntity.getUserId()).isEqualTo(USER_ID);
        assertThat(cardEntity.getVisibility()).isEqualTo(RecordCardVisibility.PUBLIC);
        assertThat(cardEntity.getActivityType()).isEqualTo(RecordCardActivityType.TRAVEL);
        assertThat(cardEntity.getBaseMusicStatus()).isEqualTo(MusicStatus.GENERATING);
        assertThat(cardEntity.getBaseMusicKey()).isNull();
        assertThat(cardEntity.getDisplayOrder()).isEqualTo(4);
        assertThat(cardEntity.getThumbnailImageKey()).isEqualTo("k/2/b.png");

        assertThat(persisted.getAllValues().subList(1, 3))
                .map(RecordCardImageJpaEntity.class::cast)
                .extracting(RecordCardImageJpaEntity::getRecordCardImageId, RecordCardImageJpaEntity::getImageKey,
                        RecordCardImageJpaEntity::getSortOrder, RecordCardImageJpaEntity::getCaption,
                        RecordCardImageJpaEntity::getMusicStatus, RecordCardImageJpaEntity::getMusicKey)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(IMAGE_ID_1, "k/1/a.jpg", 0, "노을",
                                MusicStatus.GENERATING, null),
                        org.assertj.core.groups.Tuple.tuple(IMAGE_ID_2, "k/2/b.png", 1, null,
                                MusicStatus.GENERATING, null));
        assertThat(persisted.getAllValues().subList(1, 3))
                .map(RecordCardImageJpaEntity.class::cast)
                .allSatisfy(image -> assertThat(image.getRecordCard()).isSameAs(cardEntity));
    }

    @Test
    @DisplayName("순위 UNIQUE에 걸리면 DISPLAY_ORDER_TAKEN — 다른 기록으로 동시에 만들어 순위가 겹쳤다")
    void displayOrderTaken() {
        // given
        willThrow(violation("uk_record_card_user_display_order")).given(entityManager).flush();

        // when & then
        assertThat(adapter.create(card())).isEqualTo(CreateResult.DISPLAY_ORDER_TAKEN);
    }

    @Test
    @DisplayName("기록 UNIQUE에 걸리면 RECORD_ALREADY_HAS_CARD — 잠금이 빠졌을 때의 마지막 안전장치")
    void recordAlreadyHasCard() {
        // given
        willThrow(violation("uk_record_card_running_record")).given(entityManager).flush();

        // when & then
        assertThat(adapter.create(card())).isEqualTo(CreateResult.RECORD_ALREADY_HAS_CARD);
    }

    @Test
    @DisplayName("다른 제약 위반은 결과로 바꾸지 않고 그대로 던진다 — 버그라 500이어야 한다")
    void unknownConstraintPropagates() {
        // given
        willThrow(violation("ck_record_card_memo_length")).given(entityManager).flush();

        // when & then
        assertThatThrownBy(() -> adapter.create(card())).isInstanceOf(ConstraintViolationException.class);
    }

    private static ConstraintViolationException violation(String constraintName) {
        return new ConstraintViolationException("violation", new SQLException(), constraintName);
    }

    private static RecordCard card() {
        return RecordCard.create(CARD_ID, RECORD_ID, USER_ID, RecordCardVisibility.PUBLIC,
                RecordCardActivityType.TRAVEL, "바람이 좋았다", new DisplayOrder(4),
                List.of(new ImageDraft(IMAGE_ID_1, "k/1/a.jpg", "노을"), new ImageDraft(IMAGE_ID_2, "k/2/b.png", null)),
                1);
    }
}
