package com.runiverse.running_service.unit_test.match.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.exception.InvalidMatchOptionException;
import com.runiverse.running_service.application.match.exception.MatchCooldownException;
import com.runiverse.running_service.application.match.port.in.ApplyMatchUsecase;
import com.runiverse.running_service.application.match.port.in.CancelMatchUsecase;
import com.runiverse.running_service.application.match.port.in.GetMatchSlotsUsecase;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsQuery;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;
import com.runiverse.running_service.infrastructure.config.JacksonConfig;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.match.controller.RunningMatchController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 쿨다운만 공통 에러 형식이 아니라 전용 응답을 탄다 — 그 모양을 요청 단위로 확인한다.
// 슬롯 조회는 쿼리 파라미터 바인딩·응답 모양·400 문구를 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 컨트롤러 단위 테스트")
class RunningMatchControllerTest {

    // UserId VO가 UUIDv7만 받는다
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new SimpleModule()
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(JacksonConfig.SECONDS)))
            .build();

    @Mock
    private ApplyMatchUsecase applyMatchUsecase;

    @Mock
    private CancelMatchUsecase cancelMatchUsecase;

    @Mock
    private GetMatchSlotsUsecase getMatchSlotsUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RunningMatchController(getMatchSlotsUsecase, applyMatchUsecase, cancelMatchUsecase))
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
    @DisplayName("쿨다운 중이면 409와 함께 해제 시각을 초 단위로 싣는다")
    void cooldownCarriesUntil() throws Exception {
        // given
        given(applyMatchUsecase.handle(any()))
                .willThrow(new MatchCooldownException(LocalDateTime.of(2026, 7, 26, 7, 30)));

        // when
        MvcResult result = mockMvc.perform(post("/running-matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledStartAt":"2026-07-26T19:00:00","targetDistanceMeters":5000}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        Map<String, Object> error = JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
        assertThat(error.keySet()).containsExactly("code", "message", "cooldownUntil");
        assertThat(error)
                .containsEntry("code", "MATCH_COOLDOWN")
                .containsEntry("message", "매칭 또는 러닝 중 이탈해 일정 시간 신청이 제한됩니다.")
                .containsEntry("cooldownUntil", "2026-07-26T07:30:00");
    }

    @Test
    @DisplayName("슬롯 조회는 날짜·거리를 넘기고 슬롯을 초 단위 시각으로 내려 준다")
    void slotsResponseShape() throws Exception {
        // given
        LocalDate date = LocalDate.now().plusDays(1);
        given(getMatchSlotsUsecase.handle(any())).willReturn(new GetMatchSlotsResult(List.of(
                new GetMatchSlotsResult.MatchSlot(date.atTime(18, 0), 0, false),
                new GetMatchSlotsResult.MatchSlot(date.atTime(18, 30), 3, true))));

        // when
        MvcResult result = mockMvc.perform(get("/running-matches/slots")
                        .param("date", date.toString())
                        .param("targetDistanceMeters", "5000"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        ArgumentCaptor<GetMatchSlotsQuery> captor = ArgumentCaptor.forClass(GetMatchSlotsQuery.class);
        verify(getMatchSlotsUsecase).handle(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new GetMatchSlotsQuery(date, 5_000));
        Map<String, List<Map<String, Object>>> body = JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
        assertThat(body.get("slots")).containsExactly(
                Map.of("scheduledStartAt", date + "T18:00:00", "waitingCount", 0, "selectable", false),
                Map.of("scheduledStartAt", date + "T18:30:00", "waitingCount", 3, "selectable", true));
    }

    @Test
    @DisplayName("날짜를 생략하면 오늘을 조회한다")
    void slotsDefaultToToday() throws Exception {
        // given
        given(getMatchSlotsUsecase.handle(any())).willReturn(new GetMatchSlotsResult(List.of()));

        // when
        mockMvc.perform(get("/running-matches/slots").param("targetDistanceMeters", "5000"));

        // then
        verify(getMatchSlotsUsecase).handle(new GetMatchSlotsQuery(LocalDate.now(), 5_000));
    }

    @Test
    @DisplayName("거리가 숫자가 아니면 변환 오류 문구 대신 공통 문구로 400을 낸다")
    void nonNumericDistanceIsInvalidRequest() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/running-matches/slots").param("targetDistanceMeters", "abc"))
                .andReturn();

        // then -> 내부 타입명·입력값이 본문에 실리면 안 된다
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorOf(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
        verifyNoInteractions(getMatchSlotsUsecase);
    }

    @Test
    @DisplayName("거리가 없으면 필수 문구로 400을 낸다")
    void missingDistanceIsRequired() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/running-matches/slots")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorOf(result)).containsEntry("message", "목표 거리는 필수입니다.");
        verifyNoInteractions(getMatchSlotsUsecase);
    }

    @Test
    @DisplayName("오늘 이전 날짜는 400이다")
    void pastDateIsRejected() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/running-matches/slots")
                        .param("date", LocalDate.now().minusDays(1).toString())
                        .param("targetDistanceMeters", "5000"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorOf(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "조회 날짜는 오늘 이전일 수 없습니다.");
        verifyNoInteractions(getMatchSlotsUsecase);
    }

    @Test
    @DisplayName("목록 밖 거리는 유스케이스가 거절해 같은 400 INVALID_REQUEST로 나간다")
    void unlistedDistanceIsInvalidRequest() throws Exception {
        // given
        given(getMatchSlotsUsecase.handle(any()))
                .willThrow(new InvalidMatchOptionException("목표 거리는 3000, 5000, 10000 중 하나여야 합니다."));

        // when
        MvcResult result = mockMvc.perform(get("/running-matches/slots").param("targetDistanceMeters", "7000"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorOf(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "목표 거리는 3000, 5000, 10000 중 하나여야 합니다.");
    }

    private Map<String, Object> errorOf(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
