package com.runiverse.running_service.application.recordcard.query.list;

import com.runiverse.running_service.application.recordcard.exception.InvalidRecordCardCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 페이지 마지막 카드의 순위 — 목록은 순위(display_order) 역순이고 한 사람 안에서 순위는 겹치지 않아 이것 하나로 다음 위치가 정해진다
public record RecordCardCursor(int displayOrder) {

    // 클라에는 불투명한 값이다 — 형식을 바꿔도 계약이 깨지지 않게 감싼다
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toString(displayOrder).getBytes(StandardCharsets.UTF_8));
    }

    public static RecordCardCursor decode(String value) {
        try {
            int displayOrder = Integer.parseInt(new String(Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8));
            if (displayOrder < 1) {
                throw new InvalidRecordCardCursorException();
            }
            return new RecordCardCursor(displayOrder);
        } catch (IllegalArgumentException e) {
            throw new InvalidRecordCardCursorException();
        }
    }
}
