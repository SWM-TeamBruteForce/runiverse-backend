package com.runiverse.running_service.unit_test.relationship.application;

import com.runiverse.running_service.application.relationship.exception.InvalidUserSearchCursorException;
import com.runiverse.running_service.application.relationship.query.search.UserSearchCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserSearchCursorTest {

    @Nested
    @DisplayName("인코딩 테스트")
    class EncodeTest {

        @ParameterizedTest
        @ValueSource(strings = {"철수", "runner_01", "a%b_c\\d", "공백 있는 닉"})
        @DisplayName("인코딩한 커서를 풀면 같은 닉네임이 나온다")
        void encodeThenDecodeReturnsSameNickname(String nickname) {
            // given -> 한글·LIKE 와일드카드·공백도 그대로 왕복해야 경계 비교가 맞는다
            UserSearchCursor cursor = new UserSearchCursor(nickname);

            // when
            UserSearchCursor decoded = UserSearchCursor.decode(cursor.encode());

            // then
            assertThat(decoded).isEqualTo(cursor);
        }

        @Test
        @DisplayName("인코딩 결과는 URL에 그대로 실을 수 있는 문자만 쓴다")
        void encodeUsesUrlSafeCharactersOnly() {
            // given -> 한글 UTF-8 바이트는 표준 Base64로 감싸면 +·/가 섞이기 쉽다
            UserSearchCursor cursor = new UserSearchCursor("가나다라마바사아자차카타");

            // when & then -> 쿼리 문자열로 오므로 +·/·= 가 섞이면 클라가 인코딩해야 한다
            assertThat(cursor.encode()).matches("^[A-Za-z0-9_-]+$");
        }
    }

    @Nested
    @DisplayName("디코딩 실패 테스트")
    class DecodeFailureTest {

        @ParameterizedTest
        @ValueSource(strings = {"!!!", "abc+/="})
        @DisplayName("Base64URL로 풀 수 없으면 예외가 발생한다")
        void decodeFailsWhenNotBase64Url(String value) {
            // when & then
            assertThatThrownBy(() -> UserSearchCursor.decode(value))
                    .isInstanceOf(InvalidUserSearchCursorException.class);
        }

        @Test
        @DisplayName("빈 값은 처음부터와 구분되지 않아 예외가 발생한다")
        void decodeFailsWhenEmpty() {
            // when & then -> Base64로는 오류 없이 빈 문자열로 풀린다
            assertThatThrownBy(() -> UserSearchCursor.decode(""))
                    .isInstanceOf(InvalidUserSearchCursorException.class);
        }
    }
}
