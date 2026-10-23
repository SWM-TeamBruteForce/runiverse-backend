package com.runiverse.running_service.unit_test.app.presentation;

import com.runiverse.running_service.application.app.port.in.GetAppVersionUsecase;
import com.runiverse.running_service.application.app.query.version.GetAppVersionQuery;
import com.runiverse.running_service.application.app.query.version.GetAppVersionResult;
import com.runiverse.running_service.presentation.app.controller.AppController;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ExtendWith(MockitoExtension.class)
@DisplayName("앱 버전 확인 컨트롤러 단위 테스트")
class AppControllerTest {

    private static final String PATH = "/app/version";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private GetAppVersionUsecase getAppVersionUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AppController(getAppVersionUsecase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON_MAPPER))
                .build();
    }

    @Test
    @DisplayName("쿼리의 플랫폼을 유스케이스에 넘기고 두 버전을 명세 모양으로 내린다")
    void returnsVersionsInSpecShape() throws Exception {
        // given -> 플랫폼이 다르면 이 스텁이 맞지 않아 빈 응답이 된다
        given(getAppVersionUsecase.handle(new GetAppVersionQuery("ios")))
                .willReturn(new GetAppVersionResult("1.1.0", "1.2.0"));

        // when
        MvcResult result = mockMvc.perform(get(PATH).queryParam("platform", "ios")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> body = body(result);
        assertThat(body.keySet()).containsExactly("minimumVersion", "latestVersion");
        assertThat(body)
                .containsEntry("minimumVersion", "1.1.0")
                .containsEntry("latestVersion", "1.2.0");
    }

    @Test
    @DisplayName("플랫폼이 없으면 400 필수 문구이고 조회하지 않는다")
    void missingPlatformIs400() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get(PATH)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "플랫폼은 필수입니다.");
        then(getAppVersionUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("정해진 값이 아닌 플랫폼은 400 명세 문구다")
    void unknownPlatformIs400() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get(PATH).queryParam("platform", "WEB")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "플랫폼은 IOS 또는 ANDROID이어야 합니다.");
        then(getAppVersionUsecase).shouldHaveNoInteractions();
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
