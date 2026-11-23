package com.runiverse.running_service.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.CreateRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.DeleteRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadLastRecordCardHistoryPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadMaxDisplayOrderPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadOwnRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardDetailPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardSummariesPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDetail;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSummary;
import com.runiverse.running_service.application.recordcard.port.out.SnapshotRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.UpdateRecordCardPort;
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
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RecordCardPersistenceAdapter implements FindRecordCardIdByRunningRecordPort, LoadMaxDisplayOrderPort,
        CreateRecordCardPort, LoadRecordCardForUpdatePort, LoadOwnRecordCardForUpdatePort, SaveRecordCardMusicPort,
        LoadRecordCardDetailPort, SnapshotRecordCardPort, DeleteRecordCardPort, UpdateRecordCardPort,
        LoadLastRecordCardHistoryPort, LoadRecordCardSummariesPort {

    private static final String UK_RUNNING_RECORD = "uk_record_card_running_record";
    private static final String UK_USER_DISPLAY_ORDER = "uk_record_card_user_display_order";

    // 러닝을 출발한 참가자 상태 — 확정됐지만 나타나지 않은 참가자는 같이 뛴 사람이 아니다(7-1 playerCount와 같은 기준)
    private static final List<RunningPlayerStatus> STARTED_STATUSES = Arrays.stream(RunningPlayerStatus.values())
            .filter(RunningPlayerStatus::hasStartedRunning)
            .toList();

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
        return lockAndLoad(recordCardId, runningRecordId.get());
    }

    @Override
    public Optional<RecordCard> loadOwnedForUpdate(RecordCardId recordCardId, UserId userId) {
        // 1. 이 사람 카드일 때만 기록 ID를 읽는다 — 남의 카드면 여기서 끝나 기록 행을 잠그지 않는다.
        //    카드 주인은 바뀌지 않아(user_id는 수정 불가) 잠그기 전에 봐도 된다
        Optional<Long> runningRecordId = entityManager.createQuery("""
                        SELECT card.runningRecord.runningRecordId
                        FROM RecordCardJpaEntity card
                        WHERE card.recordCardId = :recordCardId
                          AND card.userId = :userId
                        """, Long.class)
                .setParameter("recordCardId", recordCardId.value())
                .setParameter("userId", userId.value())
                .getResultList().stream()
                .findFirst();
        if (runningRecordId.isEmpty()) {
            return Optional.empty();
        }
        return lockAndLoad(recordCardId, runningRecordId.get());
    }

    private Optional<RecordCard> lockAndLoad(RecordCardId recordCardId, Long runningRecordId) {
        // 2. 기록 행 하나만 잠근다 — 카드 쓰기(작성·수정·삭제·음악 완료)는 모두 이 행으로 줄을 선다. 카드 행은 잠그지 않는다
        entityManager.createQuery("""
                        SELECT record.runningRecordId
                        FROM RunningRecordJpaEntity record
                        WHERE record.runningRecordId = :runningRecordId
                        """, Long.class)
                .setParameter("runningRecordId", runningRecordId)
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
                .runningRecordId(runningRecordId)
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
        // loadForUpdate·loadOwnedForUpdate가 읽은 엔티티가 이 트랜잭션에 그대로 있다 — 음악 값만 옮기면 커밋 때 UPDATE된다.
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

    @Override
    public Optional<RecordCardDetail> loadDetail(RecordCardId recordCardId) {
        // 1. 카드와 붙은 기록을 한 번에 읽는다 — PK로 한 행이다. 조회라 잠그지 않는다
        Optional<RecordCardJpaEntity> found = entityManager.createQuery("""
                        SELECT card
                        FROM RecordCardJpaEntity card
                        JOIN FETCH card.runningRecord
                        WHERE card.recordCardId = :recordCardId
                        """, RecordCardJpaEntity.class)
                .setParameter("recordCardId", recordCardId.value())
                .getResultList().stream()
                .findFirst();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RecordCardJpaEntity card = found.get();
        RunningRecordJpaEntity record = card.getRunningRecord();

        // 2. 사진 — sort_order 순서가 캐러셀 순서다
        List<RecordCardDetail.Image> images = loadImageEntities(card.getRecordCardId()).stream()
                .map(image -> new RecordCardDetail.Image(image.getRecordCardImageId(), image.getImageKey(),
                        image.getCaption(), image.getMusicStatus(), image.getMusicKey()))
                .toList();

        // 3. 구간 경로 — uk_running_segment_record_number(running_record_id, segment_number)가 정렬을 맡는다
        List<String> segmentPolylines = entityManager.createQuery("""
                        SELECT segment.routePolyline
                        FROM RunningSegmentJpaEntity segment
                        WHERE segment.record.runningRecordId = :runningRecordId
                        ORDER BY segment.segmentNumber
                        """, String.class)
                .setParameter("runningRecordId", record.getRunningRecordId())
                .getResultList();

        // 4. 같이 뛴 사람 — 그 방에서 러닝을 출발한 참가자(6-1 players·7-1 playerCount와 같은 기준) 중 작성자를 뺀다.
        // 탈퇴자·차단 관계는 유스케이스가 거른다
        List<UUID> companionUserIds = entityManager.createQuery("""
                        SELECT player.userId
                        FROM RunningRoomSessionJpaEntity session
                        JOIN RunningPlayerJpaEntity player ON player.runningPlayerId = session.runningPlayerId
                        WHERE session.room = :room
                          AND player.status IN :startedStatuses
                          AND player.userId <> :authorId
                        ORDER BY player.runningPlayerId
                        """, UUID.class)
                .setParameter("room", record.getRoom())
                .setParameter("startedStatuses", STARTED_STATUSES)
                .setParameter("authorId", card.getUserId())
                .getResultList();

        return Optional.of(new RecordCardDetail(
                card.getRecordCardId(), card.getUserId(), card.getVisibility(), card.getActivityType(),
                card.getMemo(), card.getThumbnailImageKey(), card.getBaseMusicStatus(), card.getBaseMusicKey(),
                images,
                new RecordCardDetail.Record(record.getRunningRecordId(), record.getStartAt(),
                        record.getTotalDistance(), record.getTotalDuration(), record.getAvgPace(), segmentPolylines),
                companionUserIds,
                card.getCreatedAt(), card.getUpdatedAt()));
    }

    // 목록(10-3) — uk_record_card_user_display_order(user_id, display_order)를 역순으로 탄다. 사진 수는 행마다 센다(한 장에 많아야 5장).
    // 커서가 없으면 맨 위(가장 큰 순위)부터다
    @Override
    public List<RecordCardSummary> loadSummaries(UserId ownerId, Collection<RecordCardVisibility> visibilities,
                                                 Integer beforeDisplayOrder, int limit) {
        return entityManager.createQuery("""
                        SELECT NEW com.runiverse.running_service.application.recordcard.port.out.RecordCardSummary(
                            card.recordCardId, card.thumbnailImageKey,
                            (SELECT COUNT(image) FROM RecordCardImageJpaEntity image WHERE image.recordCard = card),
                            record.startAt, record.totalDistance, card.displayOrder)
                        FROM RecordCardJpaEntity card
                        JOIN card.runningRecord record
                        WHERE card.userId = :ownerId
                          AND card.visibility IN :visibilities
                          AND card.displayOrder < :before
                          AND record.room.deletedAt IS NULL
                        ORDER BY card.displayOrder DESC
                        """, RecordCardSummary.class)
                .setParameter("ownerId", ownerId.value())
                .setParameter("visibilities", visibilities)
                .setParameter("before", beforeDisplayOrder == null ? Integer.MAX_VALUE : beforeDisplayOrder)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public void snapshot(RecordCardId recordCardId, RecordCardSnapshotReason reason) {
        // 엔티티의 지금 값을 남긴다 — loadOwnedForUpdate가 읽은 카드·사진이 이 트랜잭션에 있어 다시 SELECT하지 않는다.
        // 수정 저장(update)보다 먼저 불러 엔티티가 아직 바뀌기 전이다
        RecordCardJpaEntity card = entityManager.find(RecordCardJpaEntity.class, recordCardId.value());
        if (card == null) {
            throw new IllegalStateException("이력을 남길 카드가 없습니다 — 잠근 뒤 다시 읽은 카드로 불러야 합니다");
        }
        entityManager.persist(RecordCardHistoryJpaEntity.create(
                card.getRecordCardId(), reason, card.getUserId(), card.getRunningRecord().getRunningRecordId(),
                card.getVisibility(), card.getActivityType(), card.getMemo(), card.getThumbnailImageKey(),
                card.getBaseMusicStatus(), card.getBaseMusicKey(),
                loadImageEntities(card.getRecordCardId()).stream()
                        .map(RecordCardPersistenceAdapter::historyImage).toList(),
                card.getCreatedAt()));
    }

    @Override
    public Optional<Long> loadLastId(RecordCardId recordCardId) {
        // idx_record_card_history_card(record_card_id, created_at)가 카드의 이력만 좁힌다 — 카드마다 이력은 몇 줄뿐이다.
        // ID가 IDENTITY라 클수록 나중에 남긴 이력이다
        Long last = entityManager.createQuery("""
                        SELECT MAX(history.recordCardHistoryId)
                        FROM RecordCardHistoryJpaEntity history
                        WHERE history.recordCardId = :recordCardId
                        """, Long.class)
                .setParameter("recordCardId", recordCardId.value())
                .getSingleResult();
        return Optional.ofNullable(last);
    }

    @Override
    public void update(RecordCard recordCard) {
        // loadOwnedForUpdate가 읽은 엔티티에 도메인이 바꾼 값을 옮긴다 — 커밋 때 UPDATE된다. 순위(display_order)는 수정이 바꾸지 않는다
        RecordCardJpaEntity card = entityManager.find(RecordCardJpaEntity.class, recordCard.getRecordCardId().value());
        if (card == null) {
            throw new IllegalStateException("고칠 카드가 없습니다 — 잠근 뒤 다시 읽은 카드로 불러야 합니다");
        }
        card.apply(recordCard.getVisibility(), recordCard.getActivityType(), recordCard.getBaseMusicStatus(),
                recordCard.getMemo().value(), card.getDisplayOrder(),
                recordCard.getThumbnailImageKey().map(RecordCardImageKey::value).orElse(null),
                recordCard.getBaseMusicKey().map(MusicKey::value).orElse(null));

        // 사진 — 빠진 사진은 지우고, 남긴 사진은 순서·설명·음악을 옮기고, 새 사진은 넣는다.
        // 순서를 맞바꿔도 uk_record_card_image_sort_order가 커밋 때 검사(deferrable)라 중간에 걸리지 않는다
        Map<UUID, RecordCardImageJpaEntity> existing = loadImageEntities(card.getRecordCardId()).stream()
                .collect(Collectors.toMap(RecordCardImageJpaEntity::getRecordCardImageId, Function.identity()));
        Map<UUID, RecordCardImage> next = recordCard.getImages().stream()
                .collect(Collectors.toMap(image -> image.getRecordCardImageId().value(), Function.identity()));
        existing.forEach((id, entity) -> {
            if (!next.containsKey(id)) {
                entityManager.remove(entity);
            }
        });
        for (RecordCardImage image : recordCard.getImages()) {
            String musicKey = image.getMusicKey().map(MusicKey::value).orElse(null);
            String caption = image.getCaption().map(RecordCardImageCaption::value).orElse(null);
            RecordCardImageJpaEntity entity = existing.get(image.getRecordCardImageId().value());
            if (entity != null) {
                entity.apply(image.getMusicStatus(), musicKey, image.getSortOrder(), caption);
            } else {
                entityManager.persist(RecordCardImageJpaEntity.create(image.getRecordCardImageId().value(), card,
                        image.getImageKey().value(), image.getMusicStatus(), musicKey, image.getSortOrder(), caption));
            }
        }
    }

    @Override
    public void delete(RecordCardId recordCardId) {
        // 엔티티를 지운다 — 벌크 DELETE를 쓰지 않아 앞서 남긴 이력 INSERT와 순서가 섞이지 않는다.
        // 사진 행은 DB의 ON DELETE CASCADE가 지운다(사진 엔티티는 읽기만 해 밀린 UPDATE가 없다)
        RecordCardJpaEntity card = entityManager.find(RecordCardJpaEntity.class, recordCardId.value());
        if (card == null) {
            throw new IllegalStateException("지울 카드가 없습니다 — 잠근 뒤 다시 읽은 카드로 불러야 합니다");
        }
        entityManager.remove(card);
    }

    // 이력의 사진 한 장 — 키 이름은 images 칸의 저장 약속이라 바꾸지 않는다. 값이 없어도 키를 빼지 않는다(LinkedHashMap은 null을 담는다)
    private static Map<String, Object> historyImage(RecordCardImageJpaEntity image) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("recordCardImageId", image.getRecordCardImageId().toString());
        map.put("imageKey", image.getImageKey());
        map.put("caption", image.getCaption());
        map.put("sortOrder", image.getSortOrder());
        map.put("musicStatus", image.getMusicStatus().name());
        map.put("musicKey", image.getMusicKey());
        return map;
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
