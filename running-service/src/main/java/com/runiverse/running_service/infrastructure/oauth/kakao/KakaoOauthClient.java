package com.runiverse.running_service.infrastructure.oauth.kakao;

import com.runiverse.running_service.application.auth.exception.OauthEmailNotProvidedException;
import com.runiverse.running_service.application.auth.exception.OauthLoginFailedException;
import com.runiverse.running_service.application.auth.exception.OauthProviderUnavailableException;
import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.auth.port.out.LoadKakaoProfilePort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.application.user.port.out.UnlinkKakaoPort;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.user.vo.ProviderId;
import com.runiverse.running_service.infrastructure.oauth.OauthErrorCode;
import com.runiverse.running_service.infrastructure.oauth.kakao.dto.KakaoTokenResponse;
import com.runiverse.running_service.infrastructure.oauth.kakao.dto.KakaoUserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Slf4j
@Component
public class KakaoOauthClient implements LoadKakaoProfilePort, UnlinkKakaoPort {

    private static final String GRANT_TYPE = "authorization_code";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String KAKAO_AK_PREFIX = "KakaoAK ";
    private static final String TARGET_ID_TYPE = "user_id";
    private static final String API_LIMIT_EXCEEDED = "-10";
    private final RestClient restClient;
    private final KakaoOauthProperties properties;
    private final JsonMapper jsonMapper;

    KakaoOauthClient(
            RestClient restClient,
            KakaoOauthProperties properties,
            JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public OauthProfile load(String authorizationCode, String codeVerifier) {
        try {
            String kakaoAccessToken = requestAccessToken(authorizationCode, codeVerifier);
            KakaoUserResponse user = fetchUser(kakaoAccessToken);
            return toProfile(user);
        } catch (ResourceAccessException e) {
            // 연결 실패·타임아웃 — 카카오 장애인지 우리 네트워크 문제인지는 여기서 가를 수 없다
            log.error("[인증] 카카오 로그인 실패: 카카오 통신 오류", e);
            throw new OauthProviderUnavailableException();
        } catch (RestClientException e) {
            log.error("[인증] 카카오 로그인 실패: 카카오 통신 오류", e);
            throw new OauthLoginFailedException();
        }
    }

    private String requestAccessToken(String authorizationCode, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);
        // 앱이 PKCE로 인가를 시작하므로 검증값은 항상 온다
        form.add("code_verifier", codeVerifier);
        // REST API 키에 기본 활성화돼 있으면 필수다
        if (StringUtils.hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }
        KakaoTokenResponse response = restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    throw failureOf("[인증] 카카오 토큰 요청 실패: 카카오 응답 오류", res);
                })
                .body(KakaoTokenResponse.class);
        if (response == null || !StringUtils.hasText(response.accessToken())) {
            log.error("[인증] 카카오 토큰 요청 실패: access_token 누락");
            throw new OauthLoginFailedException();
        }
        return response.accessToken();
    }

    // 카카오 액세스 토큰으로 사용자 정보 조회
    private KakaoUserResponse fetchUser(String kakaoAccessToken) {
        KakaoUserResponse response = restClient.get()
                .uri(properties.userInfoUri())
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + kakaoAccessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    throw failureOf("[인증] 카카오 사용자 조회 실패: 카카오 응답 오류", res);
                })
                .body(KakaoUserResponse.class);
        if (response == null || response.id() == null) {
            log.error("[인증] 카카오 사용자 조회 실패: id 누락");
            throw new OauthLoginFailedException();
        }
        return response;
    }

    // 탈퇴가 커밋된 뒤에 부른다. 실패해도 던지지 않는다 — 되돌릴 수 없고,
    // 남는 피해는 카카오 앱 목록에 이름이 남는 것뿐이다
    @Override
    public void unlink(ProviderId providerId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", TARGET_ID_TYPE);
        form.add("target_id", providerId.value());
        try {
            restClient.post()
                    .uri(properties.unlinkUri())
                    .header(HttpHeaders.AUTHORIZATION, KAKAO_AK_PREFIX + properties.unlinkAdminKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    // 본문을 남기지 않는다 — 카카오 오류 메시지에 어드민 키가 섞여 온다
                    .onStatus(HttpStatusCode::isError, (request, res) ->
                            log.warn("[회원] 카카오 연동 해제 실패: 카카오 응답 오류 - status={}", res.getStatusCode().value()))
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // 예외 메시지에 어드민 키가 섞여 오므로 예외 객체 대신 종류만 남긴다
            log.error("[회원] 카카오 연동 해제 실패: 카카오 통신 오류 - cause={}", e.getClass().getSimpleName());
        }
    }

    private OauthProfile toProfile(KakaoUserResponse response) {
        // 보낸 데이터 에서 email이 있으면 account로 받아온다
        KakaoUserResponse.KakaoAccount account = response.kakaoAccount();
        String email = (account == null) ? null : account.email();
        // 동의하지 않았거나, 인증되지 않았거나, 다른 카카오계정에 사용돼 만료된 이메일은 가입에 쓰지 않는다
        // — 만료된 이메일은 카카오가 마스킹(ka***@kakao.com)해서 준다
        if (!StringUtils.hasText(email)
                || !Boolean.TRUE.equals(account.isEmailValid())
                || !Boolean.TRUE.equals(account.isEmailVerified())) {
            throw new OauthEmailNotProvidedException();
        }
        return new OauthProfile(Provider.KAKAO, String.valueOf(response.id()), email);
    }

    // 문구는 호출하는 곳에서 고정 문자열로 넘긴다 — 변하는 값은 key=value로만 붙인다.
    // 카카오 쪽 사정(장애·점검·호출 한도 초과)이면 다시 시도해도 당장은 소용없어 503, 나머지는 사용자 인증 실패로 본다
    private BusinessException failureOf(String message, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        String errorCode = OauthErrorCode.of(jsonMapper, response);
        if (isProviderUnavailable(status, errorCode)) {
            log.error(message + " - status={}, errorCode={}", status.value(), errorCode);
            return new OauthProviderUnavailableException();
        }
        log.warn(message + " - status={}, errorCode={}", status.value(), errorCode);
        return new OauthLoginFailedException();
    }

    // 카카오 사용자 조회는 호출 한도 초과를 429가 아니라 400 + code -10으로 준다(카카오 REST API 응답 코드 표)
    private static boolean isProviderUnavailable(HttpStatusCode status, String errorCode) {
        return status.is5xxServerError()
                || status.value() == HttpStatus.TOO_MANY_REQUESTS.value()
                || API_LIMIT_EXCEEDED.equals(errorCode);
    }
}
