package com.runiverse.running_service.infrastructure.oauth;

import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

// 외부 OAuth 오류 본문에서 원인 코드만 꺼낸다
// 본문 전체는 공백이 섞여 key=value 파싱을 깨고, 인가 코드가 되돌아올 수 있어 남기지 않는다
public final class OauthErrorCode {

    private static final String UNKNOWN = "unknown";
    // 카카오 토큰은 error_code(KOE...), 구글과 표준 응답은 error, 카카오 사용자 조회는 code
    private static final List<String> FIELDS = List.of("error_code", "error", "code");

    private OauthErrorCode() {
    }

    public static String of(JsonMapper jsonMapper, ClientHttpResponse response) {
        try {
            JsonNode body = jsonMapper.readTree(response.getBody());
            return FIELDS.stream()
                    .filter(body::hasNonNull)
                    .map(field -> body.get(field).asString())
                    .findFirst()
                    .orElse(UNKNOWN);
        } catch (IOException | JacksonException e) {
            // 본문이 JSON이 아니면(게이트웨이 HTML 오류 페이지 등) 코드를 알 수 없다 — status가 대신 말해준다
            return UNKNOWN;
        }
    }
}
