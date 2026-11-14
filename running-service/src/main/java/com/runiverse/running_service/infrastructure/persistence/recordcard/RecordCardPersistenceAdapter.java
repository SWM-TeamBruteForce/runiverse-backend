package com.runiverse.running_service.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.CreateRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadMaxDisplayOrderPort;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicKey;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageCaption;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageKey;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordJpaEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RecordCardPersistenceAdapter implements FindRecordCardIdByRunningRecordPort, LoadMaxDisplayOrderPort,
        CreateRecordCardPort {

    private static final String UK_RUNNING_RECORD = "uk_record_card_running_record";
    private static final String UK_USER_DISPLAY_ORDER = "uk_record_card_user_display_order";

    private final EntityManager entityManager;

    @Override
    public Optional<RecordCardId> findIdByRunningRecord(RunningRecordId runningRecordId) {
        // uk_record_card_running_record(running_record_id)를 탄다 — 기록당 많아야 한 행이다. 엔티티 대신 ID만 읽는다.
        // 작성 요청(9-1)은 트랜잭션 없이 부른다 — getResultStream()은 트랜잭션 밖에서 커서가 먼저 닫혀 쓰지 않는다
        return entityManager.createQuery("""
                        SELECT card.recordCardId
                        FROM RecordCardJpaEntity card
                        WHERE card.runningRecord.runningRecordId = :runningRecordId
                        """, UUID.class)
                .setParameter("runningRecordId", runningRecordId.value())
                .getResultList().stream()
                .findFirst()
                .map(RecordCardId::new);
    }

    @Override
    public Optional<DisplayOrder> loadMax(UserId userId) {
        // uk_record_card_user_display_order(user_id, display_order)의 끝값 하나만 읽는다. 카드가 없으면 MAX가 null이다
        Integer max = entityManager.createQuery("""
                        SELECT MAX(card.displayOrder)
                        FROM RecordCardJpaEntity card
                        WHERE card.userId = :userId
                        """, Integer.class)
                .setParameter("userId", userId.value())
                .getSingleResult();
        return Optional.ofNullable(max).map(DisplayOrder::new);
    }

    @Override
    public CreateResult create(RecordCard recordCard) {
        // 기록은 FK 값만 쓴다 — SELECT 없이 프록시로 건다
        RunningRecordJpaEntity runningRecord = entityManager.getReference(
                RunningRecordJpaEntity.class, recordCard.getRunningRecordId().value());
        RecordCardJpaEntity card = RecordCardJpaEntity.create(
                recordCard.getRecordCardId().value(), runningRecord, recordCard.getUserId().value(),
                recordCard.getVisibility(), recordCard.getActivityType(), recordCard.getBaseMusicStatus(),
                recordCard.getMemo().value(), recordCard.getDisplayOrder().value(),
                recordCard.getThumbnailImageKey().map(RecordCardImageKey::value).orElse(null),
                recordCard.getBaseMusicKey().map(MusicKey::value).orElse(null));
        try {
            entityManager.persist(card);
            for (RecordCardImage image : recordCard.getImages()) {
                entityManager.persist(RecordCardImageJpaEntity.create(
                        image.getRecordCardImageId().value(), card, image.getImageKey().value(),
                        image.getMusicStatus(), image.getMusicKey().map(MusicKey::value).orElse(null),
                        image.getSortOrder(), image.getCaption().map(RecordCardImageCaption::value).orElse(null)));
            }
            // 지금 INSERT를 보내 제약 위반을 이 자리에서 받는다 — 커밋 때 받으면 어느 제약인지 가를 곳이 없다.
            // 사진의 (record_card_id, sort_order)는 지연 검사라 여기서 걸리지 않는다
            entityManager.flush();
            return CreateResult.CREATED;
        } catch (ConstraintViolationException e) {
            // 걸린 뒤 이 트랜잭션은 롤백 전용이다 — 호출한 쪽이 결과를 보고 예외를 던져 롤백한다
            if (UK_USER_DISPLAY_ORDER.equals(e.getConstraintName())) {
                return CreateResult.DISPLAY_ORDER_TAKEN;
            }
            if (UK_RUNNING_RECORD.equals(e.getConstraintName())) {
                return CreateResult.RECORD_ALREADY_HAS_CARD;
            }
            throw e;
        }
    }
}
