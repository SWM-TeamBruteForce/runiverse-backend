package com.runiverse.running_service.unit_test.running.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.port.in.GetRunningResultsUsecase;
import com.runiverse.running_service.application.running.port.in.GetRunningSplitResultsUsecase;
import com.runiverse.running_service.application.running.port.in.OpenSoloRoomUsecase;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.running.controller.RunningRoomController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

// 경로의 방 ID는 컨트롤러가 거른다 — 유스케이스로 넘어가면 VO 검증에서 500이 된다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 방 컨트롤러 단위 테스트")
class RunningRoomControllerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private OpenSoloRoomUsecase openSoloRoomUsecase;

    @Mock
    private GetRunningResultsUsecase getRunningResultsUsecase;

    @Mock
    private GetRunningSplitResultsUsecase getRunningSplitResultsUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new RunningRoomController(
                        openSoloRoomUsecase, getRunningResultsUsecase, getRunningSplitResultsUsecase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON_MAPPER))
                .build();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("러닝 결과 조회: 1 미만의 방 ID는 없는 방이라 404다")
    void 결과_조회는_1_미만의_방_ID에_404다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/running-rooms/0/results")).andReturn();

        // then
        assertNotFound(result);
        verifyNoInteractions(getRunningResultsUsecase);
    }

    @Test
    @DisplayName("구간별 결과 조회: 1 미만의 방 ID는 없는 방이라 404다")
    void 구간별_결과_조회는_1_미만의_방_ID에_404다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/running-rooms/-1/split-results")).andReturn();

        // then
        assertNotFound(result);
        verifyNoInteractions(getRunningSplitResultsUsecase);
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
