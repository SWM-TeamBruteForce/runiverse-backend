package com.runiverse.running_service.unit_test.user.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.port.in.GetProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetUserProfileUsecase;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.user.controller.UserController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// 경로의 userId는 컨트롤러가 거른다 — 유스케이스로 넘어가면 VO 검증에서 500이 된다.
// 주제와 무관한 유스케이스는 생성자에 null로 들어간다
@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 컨트롤러 단위 테스트")
class UserControllerTest {

    private static final UUID VIEWER_ID = UuidCreator.getTimeOrderedEpoch();
    // 형식은 UUID지만 서버가 발급하지 않는 버전이다
    private static final String NON_V7_USER_ID = "00000000-0000-4000-8000-000000000000";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private GetUserProfileUsecase getUserProfileUsecase;

    @Mock
    private GetProfileImageUsecase getProfileImageUsecase;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON_MAPPER))
                .build();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(VIEWER_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("프로필 조회: v7이 아닌 userId는 없는 사용자라 404다")
    void 프로필_조회는_v7이_아닌_userId에_404다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/" + NON_V7_USER_ID)).andReturn();

        // then
        assertNotFound(result);
        verifyNoInteractions(getUserProfileUsecase);
    }

    @Test
    @DisplayName("프로필 사진 조회: v7이 아닌 userId는 없는 사용자라 404다")
    void 프로필_사진_조회는_v7이_아닌_userId에_404다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/" + NON_V7_USER_ID + "/profile-image")).andReturn();

        // then
        assertNotFound(result);
        verifyNoInteractions(getProfileImageUsecase);
    }

    private static void assertNotFound(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        Map<String, Object> error = JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
        assertThat(error).containsEntry("code", "NOT_FOUND");
    }
}
