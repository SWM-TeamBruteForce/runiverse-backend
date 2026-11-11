package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "running_room_chats",
        // 방의 채팅 목록(최신순 커서)과 방 삭제 CASCADE가 이 인덱스를 탄다
        indexes = @Index(name = "idx_room_chat_room_chat", columnList = "running_room_id, running_room_chat_id")
)
@Check(name = "ck_running_room_chat_content_length", constraints = "char_length(content) between 1 and 200")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningRoomChatJpaEntity extends BaseTimeEntity {

    // 생성 순서대로 커진다 — 정렬·커서·클라 중복 제거의 기준이다
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "running_room_chat_id", nullable = false, updatable = false)
    private Long runningRoomChatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_room_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_room_chat_room"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningRoomJpaEntity room;

    // 보낸 사람 — 논리 참조다. 탈퇴해도 채팅은 남고, 탈퇴는 이 행을 건드리지 않는다
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // 지금 보이는 내용 — 지워도 남긴다(신고 대응). API로는 지운 뒤 내려가지 않는다
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    // 마지막 수정 시각 — updated_at은 삭제로도 바뀌어 "수정됨"을 가르지 못한다
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    // 본인이 지운 시각 — 한 번 찍히면 바뀌지 않는다
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private RunningRoomChatJpaEntity(RunningRoomJpaEntity room, UUID userId, String content) {
        this.room = room;
        this.userId = userId;
        this.content = content;
    }

    // 새로 보낸 채팅 — 수정·삭제 시각은 비어서 시작한다
    public static RunningRoomChatJpaEntity create(RunningRoomJpaEntity room, UUID userId, String content) {
        return new RunningRoomChatJpaEntity(room, userId, content);
    }

    // 도메인이 바꾼 값을 옮긴다 — 고치기는 content·editedAt을, 지우기는 deletedAt을 바꾼다.
    // 규칙(보낸 본인·지운 뒤 금지)은 도메인이 이미 지켰다. 잠가 읽은 행이라 커밋 때 UPDATE가 나간다
    public void apply(String content, LocalDateTime editedAt, LocalDateTime deletedAt) {
        this.content = content;
        this.editedAt = editedAt;
        this.deletedAt = deletedAt;
    }
}
