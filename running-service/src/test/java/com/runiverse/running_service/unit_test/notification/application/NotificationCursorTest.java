package com.runiverse.running_service.unit_test.notification.application;

import com.runiverse.running_service.application.notification.exception.InvalidNotificationCursorException;
import com.runiverse.running_service.application.notification.query.list.NotificationCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("알림 목록 커서 단위 테스트")
class NotificationCursorTest {

    @Test
    @DisplayName("인코딩한 커서를 풀면 같은 ID가 나온다")
    void roundTrips() {
        // when
        NotificationCursor decoded = NotificationCursor.decode(new NotificationCursor(912L).encode());

        // then
        assertThat(decoded.beforeId()).isEqualTo(912L);
    }

    @Test
    @DisplayName("인코딩한 값은 ID를 그대로 드러내지 않는다")
    void encodedValueIsOpaque() {
        // when & then -> 클라가 숫자로 다루지 못하게 감싼다
        assertThat(new NotificationCursor(912L).encode()).isNotEqualTo("912");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"!!!", "not-base64-%%"})
    @DisplayName("Base64가 아니면 400으로 거절한다")
    void rejectsNonBase64(String value) {
        // when & then
        assertThatThrownBy(() -> NotificationCursor.decode(value))
                .isInstanceOf(InvalidNotificationCursorException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"abc", "0", "-5", "", "\u0000", "912\u0000"})
    @DisplayName("풀어도 1 이상의 숫자가 아니면 400으로 거절한다")
    void rejectsNonPositiveOrNonNumeric(String raw) {
        // given
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        // when & then
        assertThatThrownBy(() -> NotificationCursor.decode(value))
                .isInstanceOf(InvalidNotificationCursorException.class);
    }
}
