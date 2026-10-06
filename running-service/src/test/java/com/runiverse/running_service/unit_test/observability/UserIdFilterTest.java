package com.runiverse.running_service.unit_test.observability;

import com.runiverse.running_service.observability.logging.UserIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("사용자 ID 필터 단위 테스트")
public class UserIdFilterTest {

    private static final String USER_ID = "0192f3a4-7b1c-7d2e-8f30-9b41c7d2e5a6";

    private final UserIdFilter filter = new UserIdFilter();

    @AfterEach
    void clear() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithAccessToken() {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(USER_ID)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private List<String> captureUserIdDuringRequest() throws Exception {
        List<String> captured = new ArrayList<>();
        FilterChain chain = (request, response) -> captured.add(MDC.get(UserIdFilter.USER_ID));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        return captured;
    }

    @Test
    @DisplayName("access token으로 인증된 요청이면 처리 중 MDC에 토큰의 userId가 들어 있다")
    void userIdIsSetForAuthenticatedRequest() throws Exception {
        // given
        authenticateWithAccessToken();
        // when
        List<String> captured = captureUserIdDuringRequest();
        // then -> 이 요청의 모든 로그 줄에 userId가 붙는다
        assertThat(captured).containsExactly(USER_ID);
    }

    @Test
    @DisplayName("인증 정보가 없는 요청이면 MDC에 userId를 넣지 않는다")
    void userIdIsAbsentWithoutAuthentication() throws Exception {
        // when
        List<String> captured = captureUserIdDuringRequest();
        // then
        assertThat(captured).containsExactly((String) null);
    }

    @Test
    @DisplayName("공개 경로의 익명 인증이면 MDC에 userId를 넣지 않는다")
    void userIdIsAbsentForAnonymousRequest() throws Exception {
        // given - permitAll 경로는 익명 인증이 채워진다
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        // when
        List<String> captured = captureUserIdDuringRequest();
        // then -> "anonymousUser"가 userId로 찍히지 않는다
        assertThat(captured).containsExactly((String) null);
    }

    @Test
    @DisplayName("요청이 끝나면 MDC에서 userId를 지운다")
    void userIdIsRemovedAfterRequest() throws Exception {
        // given
        authenticateWithAccessToken();
        // when
        captureUserIdDuringRequest();
        // then -> 재사용되는 톰캣 스레드의 다음 요청에 섞이지 않는다
        assertThat(MDC.get(UserIdFilter.USER_ID)).isNull();
    }

    @Test
    @DisplayName("처리 중 예외가 나도 MDC에서 userId를 지운다")
    void userIdIsRemovedEvenWhenChainThrows() {
        // given
        authenticateWithAccessToken();
        FilterChain failingChain = (request, response) -> {
            throw new ServletException("처리 실패");
        };
        // when
        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), failingChain))
                .isInstanceOf(ServletException.class);
        // then
        assertThat(MDC.get(UserIdFilter.USER_ID)).isNull();
    }
}
