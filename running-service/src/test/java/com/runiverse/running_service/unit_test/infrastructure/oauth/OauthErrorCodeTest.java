package com.runiverse.running_service.unit_test.infrastructure.oauth;

import com.runiverse.running_service.infrastructure.oauth.OauthErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth 오류 코드 추출 단위 테스트")
class OauthErrorCodeTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String errorCodeOf(String body) {
        MockClientHttpResponse response =
                new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.BAD_REQUEST);
        return OauthErrorCode.of(jsonMapper, response);
    }

    @Test
    @DisplayName("카카오 토큰 오류는 뭉뚱그린 error보다 구체적인 error_code를 고른다")
    void prefersKakaoErrorCode() {
        assertThat(errorCodeOf("""
                {"error":"invalid_grant","error_code":"KOE320"}
                """)).isEqualTo("KOE320");
    }

    @Test
    @DisplayName("구글·표준 오류는 error를 꺼낸다")
    void readsStandardError() {
        assertThat(errorCodeOf("""
                {"error":"invalid_grant","error_description":"Bad Request"}
                """)).isEqualTo("invalid_grant");
    }

    @Test
    @DisplayName("카카오 사용자 API의 숫자 code도 문자열로 꺼낸다")
    void readsNumericCode() {
        assertThat(errorCodeOf("""
                {"msg":"this access token does not exist","code":-401}
                """)).isEqualTo("-401");
    }

    @Test
    @DisplayName("JSON이 아닌 본문이면 unknown을 돌려준다")
    void returnsUnknownForNonJsonBody() {
        // when & then -> 게이트웨이 HTML 오류 페이지 등
        assertThat(errorCodeOf("<html>bad gateway</html>")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("본문이 비어 있으면 unknown을 돌려준다")
    void returnsUnknownForEmptyBody() {
        assertThat(errorCodeOf("")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("아는 필드가 하나도 없으면 unknown을 돌려준다")
    void returnsUnknownWhenNoKnownField() {
        assertThat(errorCodeOf("""
                {"message":"something went wrong"}
                """)).isEqualTo("unknown");
    }
}
