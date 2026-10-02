package com.runiverse.e2e.auth;

import com.runiverse.e2e.E2eTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 인증 흐름 E2E 테스트")
class AuthFlowE2eTest extends E2eTestSupport {

    private static final String PASSWORD = "Password123!";

    @Test
    @DisplayName("메일 인증부터 로그아웃까지 실제 컨테이너 위에서 한 흐름으로 이어진다")
    void fullFlow() {
        String email = uniqueEmail();
        // given - 1. 인증 메일을 요청하고 앱 로그에서 실제 발송된 코드를 회수한다
        assertThat(post("/auth/email/verifications", Map.of("email", email)).status())
                .isEqualTo(204);
        String code = sentVerificationCode(email);
        // 2. 코드를 확인해 티켓을 받는다
        Response verified =
                post("/auth/email/verifications/confirm", Map.of("email", email, "code", code));
        assertThat(verified.status()).isEqualTo(200);
        // when - 3. 티켓으로 가입하면 자동 로그인까지 이어진다
        Response signedUp = post("/auth/signup", Map.of(
                "verificationTicket", verified.text("verificationTicket"),
                "password", PASSWORD
        ));
        // then
        assertThat(signedUp.status()).isEqualTo(201);
        assertThat(signedUp.text("accessToken")).isNotBlank();
        // 4. 발급된 토큰이 컨테이너의 SecurityFilterChain을 통과하고 온보딩이 DB에 저장된다
        String nickname = uniqueNickname();
        Response onboarded = post("/users/onboarding", Map.of(
                "nickname", nickname,
                "gender", "MALE",
                "birthday", "1998-03-21",
                "averagePaceSecondsPerKm", 330,
                "weightKg", new BigDecimal("68.5"),
                "heightCm", new BigDecimal("176.2")
        ), signedUp.text("accessToken"));
        assertThat(onboarded.status()).isEqualTo(201);
        assertThat(onboarded.text("nickname")).isEqualTo(nickname);
        // 5. 다시 로그인하면 저장된 해시로 인증된다
        Response loggedIn = post("/auth/login", Map.of("email", email, "password", PASSWORD));
        assertThat(loggedIn.status()).isEqualTo(200);
        assertThat(loggedIn.text("accessToken")).isNotBlank();
        // 6. Redis에 저장된 지문과 대조해 재발급된다
        Response reissued =
                post("/auth/refresh", Map.of("refreshToken", loggedIn.text("refreshToken")));
        assertThat(reissued.status()).isEqualTo(200);
        assertThat(reissued.text("accessToken")).isNotBlank();
        // 7. 로그아웃하면 액세스 토큰이 블랙리스트에 올라 재사용이 막힌다
        String accessToken = loggedIn.text("accessToken");
        assertThat(post("/auth/logout", Map.of(), accessToken).status()).isEqualTo(204);
        Response reused = post("/users/onboarding", Map.of(
                "nickname", uniqueNickname(),
                "gender", "FEMALE",
                "birthday", "1999-01-02",
                "averagePaceSecondsPerKm", 400,
                "weightKg", new BigDecimal("55.0"),
                "heightCm", new BigDecimal("162.0")
        ), accessToken);
        assertThat(reused.status()).isEqualTo(401);
        assertThat(reused.text("code")).isEqualTo("TOKEN_BLOCKED");
    }

    @Test
    @DisplayName("토큰 없이 보호된 엔드포인트를 부르면 401과 함께 인증 필요 코드가 내려온다")
    void protectedEndpointRequiresToken() {
        // when
        Response response = post("/users/onboarding", Map.of(
                "nickname", uniqueNickname(),
                "gender", "MALE",
                "birthday", "1998-03-21",
                "averagePaceSecondsPerKm", 330,
                "weightKg", new BigDecimal("68.5"),
                "heightCm", new BigDecimal("176.2")
        ));
        // then
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.text("code")).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("인증을 마쳐도 비밀번호 규칙을 어기면 400으로 걸러지고 가입되지 않는다")
    void signUpRejectsInvalidPassword() {
        // given
        String email = uniqueEmail();
        post("/auth/email/verifications", Map.of("email", email));
        Response verified = post("/auth/email/verifications/confirm",
                Map.of("email", email, "code", sentVerificationCode(email)));
        // when -> 숫자·특수문자가 없는 비밀번호
        Response response = post("/auth/signup", Map.of(
                "verificationTicket", verified.text("verificationTicket"),
                "password", "onlyletters"
        ));
        // then
        assertThat(response.status()).isEqualTo(400);
        // 가입이 막혔으므로 같은 자격으로 로그인할 수 없다
        assertThat(post("/auth/login", Map.of("email", email, "password", "onlyletters")).status())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("지원하지 않는 소셜 로그인 제공자는 외부 호출 없이 400으로 거절된다")
    void unsupportedOauthProviderIsRejected() {
        // when -> 목록에 없는 제공자, 대소문자만 다른 제공자. 본문은 보지 않는다
        Response unknown = post("/auth/oauth/apple", Map.of());
        Response upperCase = post("/auth/oauth/GOOGLE", Map.of("idToken", "anything"));
        // then -> 404가 아니라 400이다
        assertThat(unknown.status()).isEqualTo(400);
        assertThat(unknown.text("code")).isEqualTo("UNSUPPORTED_PROVIDER");
        assertThat(upperCase.status()).isEqualTo(400);
        assertThat(upperCase.text("code")).isEqualTo("UNSUPPORTED_PROVIDER");
    }
}
