package com.runiverse.running_service.infrastructure.persistence.recordcard;

import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
        name = "record_card_images",
        // 한 카드에서 같은 자리에 사진 두 장이 오지 않는다 — 커밋 때 검사한다(deferrable).
        // 수정 때 순서를 맞바꾸거나 빼고 같은 자리에 넣으면 flush 중간에 값이 겹치기 때문이다(erd record_card_images).
        // 앞 열이 record_card_id라 카드 사진 조회(8-1·10-3 장수)와 카드 삭제 CASCADE의 인덱스를 겸한다
        uniqueConstraints = @UniqueConstraint(name = "uk_record_card_image_sort_order",
                columnNames = {"record_card_id", "sort_order"}, options = "deferrable initially deferred")
)
@Check(name = "ck_record_card_image_music_status", constraints = "music_status in ('GENERATING', 'COMPLETED', 'FAILED')")
// 음악 key는 AI 서버가 완료 알림으로 준다 — 생성이 끝난(COMPLETED) 음악에만 있다
@Check(name = "ck_record_card_image_music_key", constraints = "(music_status = 'COMPLETED') = (music_key is not null)")
@Check(name = "ck_record_card_image_sort_order", constraints = "sort_order between 0 and 9")
@Check(name = "ck_record_card_image_caption_length",
        constraints = "caption is null or char_length(caption) between 1 and 100")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordCardImageJpaEntity extends BaseTimeEntity {

    // DB가 매기지 않는다 — 사진 key를 발급할 때(9-1·9-3) 정한 UUIDv7을 그대로 넣는다.
    // 사진 key의 사진 폴더이고, AI 서버가 이 값으로 완료 결과를 돌려준다
    @Id
    @Column(name = "record_card_image_id", nullable = false, updatable = false)
    private UUID recordCardImageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_card_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_record_card_image_card"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RecordCardJpaEntity recordCard;

    @Enumerated(EnumType.STRING)
    @Column(name = "music_status", nullable = false, length = 20)
    private MusicStatus musicStatus;

    // 캐러셀 순서(0부터) — 수정 때 다시 매긴다
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "image_key", nullable = false, length = 255, updatable = false)
    private String imageKey;

    // 설명이 없으면 null — 빈 문자열로 저장하지 않는다
    @Column(name = "caption", length = 100)
    private String caption;

    // AI 서버가 완료 알림으로 준 key — COMPLETED일 때만 있다
    @Column(name = "music_key", length = 255)
    private String musicKey;

    private RecordCardImageJpaEntity(UUID recordCardImageId, RecordCardJpaEntity recordCard, String imageKey) {
        this.recordCardImageId = recordCardImageId;
        this.recordCard = recordCard;
        this.imageKey = imageKey;
    }

    // 새로 넣는 사진 — 나머지 값은 apply가 옮긴다
    public static RecordCardImageJpaEntity create(UUID recordCardImageId, RecordCardJpaEntity recordCard,
                                                  String imageKey, MusicStatus musicStatus, String musicKey,
                                                  int sortOrder, String caption) {
        RecordCardImageJpaEntity entity = new RecordCardImageJpaEntity(recordCardImageId, recordCard, imageKey);
        entity.apply(musicStatus, musicKey, sortOrder, caption);
        return entity;
    }

    // 도메인이 바꾼 값을 옮긴다 — 순서·설명(수정)과 음악 상태·key(완료 알림)만 바뀐다. 사진 key는 바뀌지 않는다
    public void apply(MusicStatus musicStatus, String musicKey, int sortOrder, String caption) {
        this.musicStatus = musicStatus;
        this.musicKey = musicKey;
        this.sortOrder = sortOrder;
        this.caption = caption;
    }
}
