package com.runiverse.running_service.unit_test.match.application;

import com.runiverse.running_service.application.match.exception.InvalidRoomChatCursorException;
import com.runiverse.running_service.application.match.query.roomchatlist.RoomChatCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 클라에는 불투명한 값이다 — 서버가 준 값만 되읽고, 깨진 값은 모두 400이다
@DisplayName("방 채팅 목록 커서 단위 테스트")
class RoomChatCursorTest {

    private static String encodeRaw(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("인코딩한 커서를 되읽으면 같은 기준 ID다")
    void roundTrip() {
        // when
        RoomChatCursor decoded = RoomChatCursor.decode(new RoomChatCursor(3021L).encode());

        // then
        assertThat(decoded.beforeId()).isEqualTo(3021L);
    }

    @Test
    @DisplayName("URL에 그대로 실을 수 있게 패딩·특수문자 없이 인코딩한다")
    void encodesUrlSafe() {
        // when
        String encoded = new RoomChatCursor(Long.MAX_VALUE).encode();

        // then -> 쿼리 파라미터에 실려도 깨지지 않는다
        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(RoomChatCursor.decode(encoded).beforeId()).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!!", "not base64 ~", "%%%"})
    @DisplayName("Base64가 아니면 400 계열 예외다")
    void rejectsNonBase64(String value) {
        assertThatThrownBy(() -> RoomChatCursor.decode(value))
                .isInstanceOf(InvalidRoomChatCursorException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12.5", "", "99999999999999999999"})
    @DisplayName("풀어도 정수가 아니면 400 계열 예외다")
    void rejectsNonNumber(String raw) {
        assertThatThrownBy(() -> RoomChatCursor.decode(encodeRaw(raw)))
                .isInstanceOf(InvalidRoomChatCursorException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("1 미만의 ID는 서버가 준 적 없는 값이라 400 계열 예외다")
    void rejectsNonPositive(String raw) {
        assertThatThrownBy(() -> RoomChatCursor.decode(encodeRaw(raw)))
                .isInstanceOf(InvalidRoomChatCursorException.class);
    }
}
