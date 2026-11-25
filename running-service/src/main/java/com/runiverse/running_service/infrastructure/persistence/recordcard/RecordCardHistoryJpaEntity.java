package com.runiverse.running_service.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 카드가 바뀌기 직전 모습 — 수정·삭제마다 한 줄. 한 번 쓰고 고치지 않는다(created_at = 스냅샷을 찍은 시각).
// 카드를 하드 삭제해도 남아야 해서 record_card_id·user_id·running_record_id에 FK를 걸지 않는다
@Getter
@Entity
@Table(
        name = "record_card_histories",
        // 한 카드의 이력을 시간순으로 본다 — 수정 확정이 "마지막 수정 이력"을 이 인덱스로 찾는다
        indexes = @Index(name = "idx_record_card_history_card", columnList = "record_card_id, created_at")
)
@Check(name = "ck_record_card_history_snapshot_reason", constraints = "snapshot_reason in ('UPDATE', 'DELETE')")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordCardHistoryJpaEntity extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_card_history_id", nullable = false, updatable = false)
    private Long recordCardHistoryId;

    @Column(name = "record_card_id", nullable = false, updatable = false)
    private UUID recordCardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_reason", nullable = false, updatable = false, length = 20)
    private RecordCardSnapshotReason snapshotReason;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "running_record_id", nullable = false, updatable = false)
    private Long runningRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, updatable = false, length = 20)
    private RecordCardVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, updatable = false, length = 20)
    private RecordCardActivityType activityType;

    @Column(name = "memo", nullable = false, updatable = false, columnDefinition = "text")
    private String memo;

    @Column(name = "thumbnail_image_key", updatable = false, length = 255)
    private String thumbnailImageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_music_status", nullable = false, updatable = false, length = 20)
    private MusicStatus baseMusicStatus;

    @Column(name = "base_music_key", updatable = false, length = 255)
    private String baseMusicKey;

    // 그때의 사진 전체 — [{recordCardImageId, imageKey, caption, sortOrder, musicStatus, musicKey}], 사진이 없으면 [].
    // 사진 단위로 조회하지 않아 테이블을 나누지 않는다. 값이 없어도 키를 빼지 않는다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "images", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> images;

    // 원래 카드를 만든 시각 — 카드를 지우면 record_cards.created_at도 사라져 복사해 둔다
    @Column(name = "card_created_at", nullable = false, updatable = false)
    private LocalDateTime cardCreatedAt;

    private RecordCardHistoryJpaEntity(UUID recordCardId, RecordCardSnapshotReason snapshotReason, UUID userId,
                                       Long runningRecordId, RecordCardVisibility visibility,
                                       RecordCardActivityType activityType, String memo, String thumbnailImageKey,
                                       MusicStatus baseMusicStatus, String baseMusicKey,
                                       List<Map<String, Object>> images, LocalDateTime cardCreatedAt) {
        this.recordCardId = recordCardId;
        this.snapshotReason = snapshotReason;
        this.userId = userId;
        this.runningRecordId = runningRecordId;
        this.visibility = visibility;
        this.activityType = activityType;
        this.memo = memo;
        this.thumbnailImageKey = thumbnailImageKey;
        this.baseMusicStatus = baseMusicStatus;
        this.baseMusicKey = baseMusicKey;
        this.images = images;
        this.cardCreatedAt = cardCreatedAt;
    }

    public static RecordCardHistoryJpaEntity create(UUID recordCardId, RecordCardSnapshotReason snapshotReason,
                                                    UUID userId, Long runningRecordId,
                                                    RecordCardVisibility visibility,
                                                    RecordCardActivityType activityType, String memo,
                                                    String thumbnailImageKey, MusicStatus baseMusicStatus,
                                                    String baseMusicKey, List<Map<String, Object>> images,
                                                    LocalDateTime cardCreatedAt) {
        return new RecordCardHistoryJpaEntity(recordCardId, snapshotReason, userId, runningRecordId, visibility,
                activityType, memo, thumbnailImageKey, baseMusicStatus, baseMusicKey, images, cardCreatedAt);
    }
}
