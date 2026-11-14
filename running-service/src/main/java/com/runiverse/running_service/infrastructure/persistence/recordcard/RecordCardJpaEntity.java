package com.runiverse.running_service.infrastructure.persistence.recordcard;

import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "record_cards",
        uniqueConstraints = {
                // 기록과 1:1 — 카드 쓰기(작성·수정·삭제)는 모두 running_records 행을 먼저 잠가 줄 세운다.
                // 처음 만들 때는 잠글 카드 행이 없고 초안마다 카드 ID가 달라서다. 이 제약은 잠금이 빠졌을 때의 마지막 안전장치다
                @UniqueConstraint(name = "uk_record_card_running_record", columnNames = "running_record_id"),
                // 작성자별 순위가 겹치지 않는다 — 다른 기록 둘로 동시에 만들어 최댓값을 같이 읽으면 하나가 걸린다.
                // 목록(10-3)의 순위 역순·커서 조회 인덱스를 겸한다
                @UniqueConstraint(name = "uk_record_card_user_display_order", columnNames = {"user_id", "display_order"})
        }
)
@Check(name = "ck_record_card_visibility", constraints = "visibility in ('FRIENDS', 'PUBLIC', 'PRIVATE')")
@Check(name = "ck_record_card_base_music_status", constraints = "base_music_status in ('GENERATING', 'COMPLETED', 'FAILED')")
// 음악 key는 AI 서버가 완료 알림으로 준다 — 생성이 끝난(COMPLETED) 기본곡에만 있다
@Check(name = "ck_record_card_base_music_key",
        constraints = "(base_music_status = 'COMPLETED') = (base_music_key is not null)")
@Check(name = "ck_record_card_activity_type", constraints = "activity_type in ('DAILY', 'TRAVEL', 'RACE')")
@Check(name = "ck_record_card_memo_length", constraints = "char_length(memo) between 1 and 500")
@Check(name = "ck_record_card_display_order", constraints = "display_order >= 1")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordCardJpaEntity extends BaseTimeEntity {

    // DB가 매기지 않는다 — 작성 요청(9-1)이 정한 UUIDv7을 그대로 넣는다. 사진 key의 카드 폴더이고 AI 서버도 이 값으로 저장한다
    @Id
    @Column(name = "record_card_id", nullable = false, updatable = false)
    private UUID recordCardId;

    // 기록과 1:1은 uk_record_card_running_record가 지킨다 — @OneToOne은 UNIQUE를 자동 이름으로 따로 만들어
    // 위 제약 이름을 덮는다. 어댑터가 제약 이름으로 409를 가르므로 이름이 환경마다(운영 DDL·ddl-auto) 같아야 한다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_record_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_record_card_running_record"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningRecordJpaEntity runningRecord;

    // 작성자 — 논리 참조다. 탈퇴해도 카드는 남는다
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private RecordCardVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 20)
    private RecordCardActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_music_status", nullable = false, length = 20)
    private MusicStatus baseMusicStatus;

    @Column(name = "memo", nullable = false, columnDefinition = "text")
    private String memo;

    // 1이 가장 오래된 카드 — 지우면 행이 사라지고 남은 순위는 당기지 않는다
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // 이 카드 사진 key 하나를 복사해 둔다 — 목록(10-3)이 사진 테이블을 읽지 않게. 사진이 없으면 null
    @Column(name = "thumbnail_image_key", length = 255)
    private String thumbnailImageKey;

    // AI 서버가 완료 알림으로 준 key — COMPLETED일 때만 있다
    @Column(name = "base_music_key", length = 255)
    private String baseMusicKey;

    private RecordCardJpaEntity(UUID recordCardId, RunningRecordJpaEntity runningRecord, UUID userId) {
        this.recordCardId = recordCardId;
        this.runningRecord = runningRecord;
        this.userId = userId;
    }

    // 처음 만드는 카드 — 나머지 값은 apply가 옮긴다
    public static RecordCardJpaEntity create(UUID recordCardId, RunningRecordJpaEntity runningRecord, UUID userId,
                                             RecordCardVisibility visibility, RecordCardActivityType activityType,
                                             MusicStatus baseMusicStatus, String memo, int displayOrder,
                                             String thumbnailImageKey, String baseMusicKey) {
        RecordCardJpaEntity entity = new RecordCardJpaEntity(recordCardId, runningRecord, userId);
        entity.apply(visibility, activityType, baseMusicStatus, memo, displayOrder, thumbnailImageKey,
                baseMusicKey);
        return entity;
    }

    // 도메인이 바꾼 값을 옮긴다 — 수정·음악 완료가 이 길로 온다. 규칙(순위·썸네일)은 도메인이 이미 지켰다.
    // ID·기록·작성자는 바뀌지 않는다. 삭제는 행을 지운다(하드 삭제)
    public void apply(RecordCardVisibility visibility, RecordCardActivityType activityType,
                      MusicStatus baseMusicStatus, String memo, int displayOrder, String thumbnailImageKey,
                      String baseMusicKey) {
        this.visibility = visibility;
        this.activityType = activityType;
        this.baseMusicStatus = baseMusicStatus;
        this.memo = memo;
        this.displayOrder = displayOrder;
        this.thumbnailImageKey = thumbnailImageKey;
        this.baseMusicKey = baseMusicKey;
    }
}
