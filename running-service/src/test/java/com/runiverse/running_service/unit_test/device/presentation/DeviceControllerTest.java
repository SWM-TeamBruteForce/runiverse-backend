package com.runiverse.running_service.unit_test.device.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.command.register.RegisterDeviceCommand;
import com.runiverse.running_service.application.device.port.in.RegisterDeviceUsecase;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.device.controller.DeviceController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
@DisplayName("기기 컨트롤러 단위 테스트")
class DeviceControllerTest {

    private static final String PATH = "/users/me/devices";
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private RegisterDeviceUsecase registerDeviceUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new DeviceController(registerDeviceUsecase))
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
    @DisplayName("본문의 기기 ID·토큰과 헤더의 플랫폼·앱 버전을 넘기고 204다")
    void registersWithHeaderValues() throws Exception {
        // when -> 버전 검사 필터가 소문자 플랫폼도 통과시켜 그대로 넘기고 정규화는 도메인이 한다
        MvcResult result = mockMvc.perform(request("ios", "1.1.0", body("device-1", "token-1"))).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        then(registerDeviceUsecase).should()
                .handle(new RegisterDeviceCommand(USER_ID, "device-1", "token-1", "ios", "1.1.0"));
    }

    @Test
    @DisplayName("헤더가 없으면 500이 아니라 400이고 등록하지 않는다")
    void missingHeadersIs400() throws Exception {
        // when -> 필터 밖에서 불려도 헤더 누락이 도메인 예외(500)까지 가지 않는다
        MvcResult result = mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("device-1", "token-1"))).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST");
        then(registerDeviceUsecase).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "platform={0}")
    @ValueSource(strings = {"WEB", "AOS"})
    @DisplayName("플랫폼 헤더가 정해진 값이 아니면 400이다")
    void unknownPlatformIs400(String platform) throws Exception {
        // when
        MvcResult result = mockMvc.perform(request(platform, "1.1.0", body("device-1", "token-1"))).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
        then(registerDeviceUsecase).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "appVersion={0}")
    @ValueSource(strings = {"1.1", "v1.1.0", "1000000000.0.0"})
    @DisplayName("앱 버전 헤더가 x.y.z가 아니면 400이다")
    void malformedVersionIs400(String appVersion) throws Exception {
        // when -> DB CHECK에 걸려 500이 나기 전에 막는다
        MvcResult result = mockMvc.perform(request("IOS", appVersion, body("device-1", "token-1"))).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
        then(registerDeviceUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기기 ID가 비었거나 100자를 넘으면 400이다")
    void invalidDeviceIdIs400() throws Exception {
        // when
        MvcResult blank = mockMvc.perform(request("IOS", "1.1.0", body(" ", "token-1"))).andReturn();
        MvcResult tooLong = mockMvc.perform(request("IOS", "1.1.0", body("a".repeat(101), "token-1"))).andReturn();

        // then
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(blank)).containsEntry("message", "입력값이 올바르지 않습니다.");
        assertThat(tooLong.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(tooLong)).containsEntry("message", "입력값이 올바르지 않습니다.");
        then(registerDeviceUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기기 ID는 100자까지 받는다")
    void deviceIdAtMaxLengthIs204() throws Exception {
        // when
        MvcResult result = mockMvc.perform(request("IOS", "1.1.0", body("a".repeat(100), "token-1"))).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("푸시 토큰이 비었거나 4096자를 넘으면 400이다")
    void invalidPushTokenIs400() throws Exception {
        // when
        MvcResult missing = mockMvc.perform(request("IOS", "1.1.0", body("device-1", null))).andReturn();
        MvcResult tooLong = mockMvc.perform(request("IOS", "1.1.0", body("device-1", "t".repeat(4097)))).andReturn();

        // then
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(missing)).containsEntry("message", "입력값이 올바르지 않습니다.");
        assertThat(tooLong.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(tooLong)).containsEntry("message", "입력값이 올바르지 않습니다.");
        then(registerDeviceUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("푸시 토큰은 4096자까지 받는다")
    void pushTokenAtMaxLengthIs204() throws Exception {
        // when
        MvcResult result = mockMvc.perform(request("IOS", "1.1.0", body("device-1", "t".repeat(4096)))).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
    }

    private static MockHttpServletRequestBuilder request(String platform, String appVersion, String body) {
        return post(PATH)
                .header("X-App-Platform", platform)
                .header("X-App-Version", appVersion)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String body(String deviceId, String pushToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", deviceId);
        body.put("pushToken", pushToken);
        return JSON_MAPPER.writeValueAsString(body);
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
