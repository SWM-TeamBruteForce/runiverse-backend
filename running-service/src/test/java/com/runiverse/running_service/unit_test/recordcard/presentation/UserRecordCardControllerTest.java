package com.runiverse.running_service.unit_test.recordcard.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.exception.InvalidRecordCardCursorException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.in.GetRecordCardsUsecase;
import com.runiverse.running_service.application.recordcard.query.list.GetRecordCardsQuery;
import com.runiverse.running_service.application.recordcard.query.list.GetRecordCardsResult;
import com.runiverse.running_service.infrastructure.config.JacksonConfig;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.recordcard.controller.UserRecordCardController;
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
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 기록 카드 목록(10-3) 컨트롤러 단위 테스트")
class UserRecordCardControllerTest {

    private static final UUID VIEWER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OWNER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new SimpleModule()
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(JacksonConfig.SECONDS)))
            .build();

    @Mock
    private GetRecordCardsUsecase getRecordCardsUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new UserRecordCardController(getRecordCardsUsecase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON_MAPPER))
                .build();
        Jwt jwt = Jwt.withTokenValue("access-token").header("alg", "none").subject(VIEWER_ID.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("토큰 주인이 경로의 사용자 목록을 본다 — 공통 커서 모양(items·nextCursor)으로 내리고, 썸네일이 없으면 null 키도 내려간다")
    void listsCards() throws Exception {
        // given
        UUID withPhoto = UuidCreator.getTimeOrderedEpoch();
        UUID withoutPhoto = UuidCreator.getTimeOrderedEpoch();
        given(getRecordCardsUsecase.handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, "c1", 7)))
                .willReturn(new GetRecordCardsResult(List.of(
                        new GetRecordCardsResult.Item(withPhoto, "https://thumb", 3,
                                LocalDateTime.of(2026, 11, 3, 7, 10, 0, 123_000_000), 5020),
                        new GetRecordCardsResult.Item(withoutPhoto, null, 0,
                                LocalDateTime.of(2026, 11, 1, 6, 0), 3000)), "c2"));

        // when
        MvcResult result = mockMvc.perform(get("/users/{userId}/record-cards", OWNER_ID)
                .param("cursor", "c1").param("limit", "7")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> body = body(result);
        assertThat(body).containsEntry("nextCursor", "c2");
        Map<String, Object> second = new HashMap<>();
        second.put("recordCardId", withoutPhoto.toString());
        second.put("thumbnailUrl", null);
        second.put("imageCount", 0);
        second.put("startedAt", "2026-11-01T06:00:00");
        second.put("totalDistanceMeters", 3000);
        assertThat(body.get("items")).isEqualTo(List.of(
                Map.of("recordCardId", withPhoto.toString(), "thumbnailUrl", "https://thumb", "imageCount", 3,
                        "startedAt", "2026-11-03T07:10:00", "totalDistanceMeters", 5020),
                second));
    }

    @Test
    @DisplayName("limit은 기본 20, 50을 넘으면 50으로 줄이고, 빈 cursor는 첫 페이지다")
    void limitAndBlankCursor() throws Exception {
        // given
        given(getRecordCardsUsecase.handle(any())).willReturn(new GetRecordCardsResult(List.of(), null));

        // when
        mockMvc.perform(get("/users/{userId}/record-cards", OWNER_ID)).andReturn();
        mockMvc.perform(get("/users/{userId}/record-cards", OWNER_ID).param("limit", "500").param("cursor", ""))
                .andReturn();

        // then
        verify(getRecordCardsUsecase).handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, null, 20));
        verify(getRecordCardsUsecase).handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, null, 50));
    }

    @Test
    @DisplayName("사용자 ID가 UUIDv7이 아니거나 limit이 1보다 작으면 400이고 유스케이스를 부르지 않는다")
    void rejectsBadRequest() throws Exception {
        // when & then
        assertThat(mockMvc.perform(get("/users/{userId}/record-cards", UUID.randomUUID())).andReturn()
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(mockMvc.perform(get("/users/{userId}/record-cards", OWNER_ID).param("limit", "0")).andReturn()
                .getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(getRecordCardsUsecase);
    }

    @Test
    @DisplayName("깨진 커서는 400, 대상이 없거나 차단 관계면 404다")
    void errors() throws Exception {
        // given
        willThrow(new InvalidRecordCardCursorException()).given(getRecordCardsUsecase)
                .handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, "broken", 20));
        willThrow(new RecordCardNotFoundException()).given(getRecordCardsUsecase)
                .handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, null, 20));

        // when
        MvcResult broken = mockMvc.perform(get("/users/{userId}/record-cards", OWNER_ID).param("cursor", "broken"))
                .andReturn();
        MvcResult notFound = mockMvc.perform(get("/users/{userId}/record-cards", OWNER_ID)).andReturn();

        // then
        assertThat(broken.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(broken)).containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
        assertThat(notFound.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(notFound)).containsEntry("code", "NOT_FOUND");
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
