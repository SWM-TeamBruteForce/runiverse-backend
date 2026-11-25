package com.runiverse.running_service.unit_test.recordcard.application;

import com.runiverse.running_service.application.recordcard.exception.InvalidRecordCardCursorException;
import com.runiverse.running_service.application.recordcard.query.list.RecordCardCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("기록 카드 목록 커서 단위 테스트")
class RecordCardCursorTest {

    @Test
    @DisplayName("인코딩한 값을 다시 풀면 같은 순위다")
    void roundTrip() {
        assertThat(RecordCardCursor.decode(new RecordCardCursor(42).encode())).isEqualTo(new RecordCardCursor(42));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%%%", "abc", "0", "-1"})
    @DisplayName("Base64가 아니거나, 숫자가 아니거나, 1보다 작은 순위면 깨진 커서다")
    void rejectsBroken(String raw) {
        String value = raw.equals("%%%") ? raw
                : Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> RecordCardCursor.decode(value)).isInstanceOf(InvalidRecordCardCursorException.class);
    }
}
