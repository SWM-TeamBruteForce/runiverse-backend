package com.runiverse.running_service.unit_test.relationship.application;

import com.runiverse.running_service.application.relationship.exception.InvalidFriendCursorException;
import com.runiverse.running_service.application.relationship.query.friends.FriendCursor;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FriendCursorTest {

    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440013");

    private static String wrap(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("인코딩 테스트")
    class EncodeTest {

        @Test
        @DisplayName("인코딩한 커서를 풀면 같은 정렬 키가 나온다")
        void encodeThenDecodeReturnsSameCursor() {
            // given -> DB가 주는 마이크로초까지 담겨야 경계 비교가 맞는다
            FriendCursor cursor = new FriendCursor(
                    RelationshipType.FRIEND, LocalDateTime.of(2026, 10, 25, 12, 30, 15, 123_456_000), USER_ID);

            // when
            FriendCursor decoded = FriendCursor.decode(cursor.encode());

            // then
            assertThat(decoded).isEqualTo(cursor);
        }

        @Test
        @DisplayName("초가 0인 시각도 왕복한다")
        void encodeThenDecodeKeepsZeroSeconds() {
            // given -> LocalDateTime.toString은 초가 0이면 12:00처럼 줄여 쓴다
            FriendCursor cursor = new FriendCursor(
                    RelationshipType.FRIEND_REQUEST_RECEIVED, LocalDateTime.of(2026, 10, 25, 12, 0), USER_ID);

            // when
            FriendCursor decoded = FriendCursor.decode(cursor.encode());

            // then
            assertThat(decoded).isEqualTo(cursor);
        }

        @Test
        @DisplayName("인코딩 결과는 URL에 그대로 실을 수 있는 문자만 쓴다")
        void encodeUsesUrlSafeCharactersOnly() {
            // given
            FriendCursor cursor = new FriendCursor(
                    RelationshipType.FRIEND_REQUEST_SENT, LocalDateTime.of(2026, 10, 25, 12, 30, 15, 999_999_000), USER_ID);

            // when & then -> 쿼리 문자열로 오므로 +·/·= 가 섞이면 클라가 인코딩해야 한다
            assertThat(cursor.encode()).matches("^[A-Za-z0-9_-]+$");
        }
    }

    @Nested
    @DisplayName("디코딩 실패 테스트")
    class DecodeFailureTest {

        @ParameterizedTest
        @ValueSource(strings = {"", "!!!", "abc+/="})
        @DisplayName("Base64URL로 풀 수 없으면 예외가 발생한다")
        void decodeFailsWhenNotBase64Url(String value) {
            // when & then
            assertThatThrownBy(() -> FriendCursor.decode(value))
                    .isInstanceOf(InvalidFriendCursorException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "FRIEND|2026-10-25T12:00",
                "FRIEND|2026-10-25T12:00|01a0f2d4-6180-7000-8000-446655440013|extra"})
        @DisplayName("칸 수가 셋이 아니면 예외가 발생한다")
        void decodeFailsWhenPartCountIsNotThree(String raw) {
            // when & then
            assertThatThrownBy(() -> FriendCursor.decode(wrap(raw)))
                    .isInstanceOf(InvalidFriendCursorException.class);
        }

        @Test
        @DisplayName("없는 관계 이름이면 예외가 발생한다")
        void decodeFailsWhenTypeIsUnknown() {
            // when & then
            assertThatThrownBy(() -> FriendCursor.decode(
                    wrap("STRANGER|2026-10-25T12:00|01a0f2d4-6180-7000-8000-446655440013")))
                    .isInstanceOf(InvalidFriendCursorException.class);
        }

        @Test
        @DisplayName("시각 형식이 틀리면 예외가 발생한다")
        void decodeFailsWhenUpdatedAtIsMalformed() {
            // when & then
            assertThatThrownBy(() -> FriendCursor.decode(
                    wrap("FRIEND|2026-13-40T99:00|01a0f2d4-6180-7000-8000-446655440013")))
                    .isInstanceOf(InvalidFriendCursorException.class);
        }

        @Test
        @DisplayName("userId 형식이 틀리면 예외가 발생한다")
        void decodeFailsWhenUserIdIsMalformed() {
            // when & then
            assertThatThrownBy(() -> FriendCursor.decode(wrap("FRIEND|2026-10-25T12:00|not-a-uuid")))
                    .isInstanceOf(InvalidFriendCursorException.class);
        }
    }
}
