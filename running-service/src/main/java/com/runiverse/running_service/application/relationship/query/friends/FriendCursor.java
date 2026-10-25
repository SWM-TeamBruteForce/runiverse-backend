package com.runiverse.running_service.application.relationship.query.friends;

import com.runiverse.running_service.application.relationship.exception.InvalidFriendCursorException;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

// 페이지 마지막 항목의 정렬 키 — 묶음(type) 안에서 updated_at·userId 역순이라 셋이 있어야 다음 위치가 정해진다
public record FriendCursor(RelationshipType type, LocalDateTime updatedAt, UUID userId) {

    private static final String DELIMITER = "|";

    // 클라에는 불투명한 값이다 — 형식을 바꿔도 계약이 깨지지 않게 감싼다
    public String encode() {
        String raw = type.name() + DELIMITER + updatedAt + DELIMITER + userId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // 묶음이 이 목록에 맞는지는 조회 대상에 따라 달라 핸들러가 본다
    public static FriendCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + DELIMITER, -1);
            if (parts.length != 3) {
                throw new InvalidFriendCursorException();
            }
            return new FriendCursor(
                    RelationshipType.valueOf(parts[0]),
                    LocalDateTime.parse(parts[1]),
                    UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new InvalidFriendCursorException();
        }
    }
}
