package com.runiverse.running_service.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.CreateRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadMaxDisplayOrderPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardMusicPort;
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
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RecordCardPersistenceAdapter implements FindRecordCardIdByRunningRecordPort, LoadMaxDisplayOrderPort,
        CreateRecordCardPort, LoadRecordCardForUpdatePort, SaveRecordCardMusicPort {

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

    @Override
    public Optional<RecordCard> loadForUpdate(RecordCardId recordCardId) {
        // 1. 카드가 붙은 기록 ID만 읽는다 — 카드 엔티티는 잠근 뒤에 읽는다(그 사이 수정된 최신 값을 보려고)
        Optional<Long> runningRecordId = entityManager.createQuery("""
                        SELECT card.runningRecord.runningRecordId
                        FROM RecordCardJpaEntity card
                        WHERE card.recordCardId = :recordCardId
                        """, Long.class)
                .setParameter("recordCardId", recordCardId.value())
                .getResultList().stream()
                .findFirst();
        if (runningRecordId.isEmpty()) {
            return Optional.empty();
        }

        // 2. 기록 행 하나만 잠근다 — 카드 쓰기(작성·수정·삭제·음악 완료)는 모두 이 행으로 줄을 선다. 카드 행은 잠그지 않는다
        entityManager.createQuery("""
                        SELECT record.runningRecordId
                        FROM RunningRecordJpaEntity record
                        WHERE record.runningRecordId = :runningRecordId
                        """, Long.class)
                .setParameter("runningRecordId", runningRecordId.get())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        // 3. 잠근 뒤에 카드와 사진을 읽는다 — 그 사이 지워졌으면 비어 있다
        RecordCardJpaEntity card = entityManager.find(RecordCardJpaEntity.class, recordCardId.value());
        if (card == null) {
            return Optional.empty();
        }
        List<RecordCardImage> images = loadImageEntities(card.getRecordCardId()).stream()
                .map(image -> RecordCardImage.restore(image.getRecordCardImageId(), image.getImageKey(),
                        image.getCaption(), image.getMusicKey(), image.getMusicStatus(), image.getSortOrder()))
                .toList();
        return Optional.of(RecordCard.restore(card.getRecordCardId())
                .runningRecordId(runningRecordId.get())
                .userId(card.getUserId())
                .visibility(card.getVisibility())
                .activityType(card.getActivityType())
                .baseMusicStatus(card.getBaseMusicStatus())
                .memo(card.getMemo())
                .displayOrder(card.getDisplayOrder())
                .thumbnailImageKey(card.getThumbnailImageKey())
                .baseMusicKey(card.getBaseMusicKey())
                .images(images)
                .build());
    }

    @Override
    public void saveMusic(RecordCard recordCard) {
        // loadForUpdate가 읽은 엔티티가 이 트랜잭션에 그대로 있다 — 음악 값만 옮기면 커밋 때 UPDATE된다.
        // 글·공개 범위·순위·썸네일·사진 순서·설명은 엔티티의 지금 값을 그대로 넘겨 바꾸지 않는다
        RecordCardJpaEntity card = entityManager.find(RecordCardJpaEntity.class, recordCard.getRecordCardId().value());
        card.apply(card.getVisibility(), card.getActivityType(), recordCard.getBaseMusicStatus(), card.getMemo(),
                card.getDisplayOrder(), card.getThumbnailImageKey(),
                recordCard.getBaseMusicKey().map(MusicKey::value).orElse(null));

        Map<UUID, RecordCardImageJpaEntity> imagesById = loadImageEntities(card.getRecordCardId()).stream()
                .collect(Collectors.toMap(RecordCardImageJpaEntity::getRecordCardImageId, Function.identity()));
        for (RecordCardImage image : recordCard.getImages()) {
            RecordCardImageJpaEntity entity = imagesById.get(image.getRecordCardImageId().value());
            entity.apply(image.getMusicStatus(), image.getMusicKey().map(MusicKey::value).orElse(null),
                    entity.getSortOrder(), entity.getCaption());
        }
    }

    // 카드의 사진을 캐러셀 순서대로 — uk_record_card_image_sort_order(record_card_id, sort_order)를 탄다
    private List<RecordCardImageJpaEntity> loadImageEntities(UUID recordCardId) {
        return entityManager.createQuery("""
                        SELECT image
                        FROM RecordCardImageJpaEntity image
                        WHERE image.recordCard.recordCardId = :recordCardId
                        ORDER BY image.sortOrder
                        """, RecordCardImageJpaEntity.class)
                .setParameter("recordCardId", recordCardId)
                .getResultList();
    }
}
