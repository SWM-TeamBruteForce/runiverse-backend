package com.runiverse.running_service.application.match.query.roomchatlist;

import com.runiverse.running_service.application.match.exception.InvalidRoomChatCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 다음 페이지는 이 ID보다 오래된 채팅부터 — ID가 보낸 순서라 하나로 위치가 정해진다
public record RoomChatCursor(Long beforeId) {

    // 클라에는 불투명한 값이다 — 형식을 바꿔도 계약이 깨지지 않게 감싼다
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(beforeId.toString().getBytes(StandardCharsets.UTF_8));
    }

    // 깨진 값은 전부 400이다 — Base64가 아니거나, 숫자가 아니거나, 1 미만이다
    public static RoomChatCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            long beforeId = Long.parseLong(raw);
            if (beforeId < 1) {
                throw new InvalidRoomChatCursorException();
            }
            return new RoomChatCursor(beforeId);
        } catch (IllegalArgumentException e) {
            throw new InvalidRoomChatCursorException();
        }
    }
}
