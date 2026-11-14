package com.runiverse.running_service.presentation.match.response;

import com.runiverse.running_service.application.match.port.out.RoomChat;

import java.time.LocalDateTime;
import java.util.UUID;

// 명세의 공통 객체 Chat — 보내기·수정 응답과 ROOM_CHAT_* 이벤트 data가 이 구조를 쓴다.
// 와이어 계약이라 presentation이 갖는다
public record RoomChatResponse(
        Long chatId,
        Long runningRoomId,
        SenderResponse sender,
        // 지운 메시지면 null — 화면에는 "삭제된 메시지"로 그린다
        String content,
        boolean isDeleted,
        // 고친 적 없으면 null — 값이 있으면 "수정됨"을 붙인다
        LocalDateTime editedAt,
        LocalDateTime createdAt
) {

    public static RoomChatResponse from(RoomChat chat) {
        return new RoomChatResponse(
                chat.chatId(),
                chat.runningRoomId(),
                SenderResponse.from(chat.sender()),
                chat.content(),
                chat.isDeleted(),
                chat.editedAt(),
                chat.createdAt());
    }

    // 탈퇴했으면 §0 탈퇴 유저 형식이다 — 조립할 때 이미 바꿔서 온다
    public record SenderResponse(
            UUID userId,
            String nickname,
            String profileImageUrl,
            boolean isDeleted
    ) {

        public static SenderResponse from(RoomChat.Sender sender) {
            return new SenderResponse(
                    sender.userId(),
                    sender.nickname(),
                    sender.profileImageUrl(),
                    sender.isDeleted());
        }
    }
}
