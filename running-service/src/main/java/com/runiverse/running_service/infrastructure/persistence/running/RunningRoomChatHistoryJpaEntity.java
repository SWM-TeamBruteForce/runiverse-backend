package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Entity
@Table(
        name = "running_room_chat_histories",
        // 메시지 하나의 수정 이력 조회(신고 대응)와 메시지 삭제 CASCADE가 이 인덱스를 탄다
        indexes = @Index(name = "idx_room_chat_history_chat", columnList = "running_room_chat_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningRoomChatHistoryJpaEntity extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "running_room_chat_history_id", nullable = false, updatable = false)
    private Long runningRoomChatHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_room_chat_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_room_chat_history_chat"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningRoomChatJpaEntity chat;

    // 이번 수정 직전의 내용 — 메시지의 길이 CHECK를 거친 값이라 따로 CHECK를 두지 않는다(erd)
    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    private RunningRoomChatHistoryJpaEntity(RunningRoomChatJpaEntity chat, String content) {
        this.chat = chat;
        this.content = content;
    }

    // 고치기 직전 내용 한 건 — created_at이 곧 고친 시각이다
    public static RunningRoomChatHistoryJpaEntity create(RunningRoomChatJpaEntity chat, String content) {
        return new RunningRoomChatHistoryJpaEntity(chat, content);
    }
}
