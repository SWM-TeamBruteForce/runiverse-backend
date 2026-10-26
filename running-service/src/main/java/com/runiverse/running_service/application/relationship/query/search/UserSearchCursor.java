package com.runiverse.running_service.application.relationship.query.search;

import com.runiverse.running_service.application.relationship.exception.InvalidUserSearchCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 페이지 마지막 항목의 닉네임 — 닉네임 순으로 읽고 닉네임이 UNIQUE라 이것만으로 다음 위치가 정해진다
public record UserSearchCursor(String nickname) {

    // 클라에는 불투명한 값이다 — 정렬 키가 늘어도 계약이 깨지지 않게 감싼다
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nickname.getBytes(StandardCharsets.UTF_8));
    }

    public static UserSearchCursor decode(String value) {
        try {
            String nickname = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            // 빈 값이면 "처음부터"와 구분되지 않는다 — 서버는 빈 닉네임으로 커서를 만들지 않는다
            if (nickname.isEmpty()) {
                throw new InvalidUserSearchCursorException();
            }
            return new UserSearchCursor(nickname);
        } catch (IllegalArgumentException e) {
            throw new InvalidUserSearchCursorException();
        }
    }
}
