package com.runiverse.running_service.unit_test.running.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.port.in.GetMyRunningRecordsUsecase;
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsQuery;
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsResult;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.infrastructure.config.JacksonConfig;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.running.controller.RunningRecordController;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// 쿼리 파라미터를 받는 첫 API다 — 바인딩·검증·응답 모양을 요청 단위로 확인한다.
// 시각 직렬화는 앱의 JacksonConfig와 같은 설정을 붙여 초 단위까지 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록 목록 컨트롤러 단위 테스트")
class RunningRecordControllerTest {

    private static final String PATH = "/users/me/running-records";
    // UserId VO가 UUIDv7만 받는다
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new SimpleModule()
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(JacksonConfig.SECONDS)))
            .build();

    @Mock
    private GetMyRunningRecordsUsecase getMyRunningRecordsUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RunningRecordController(getMyRunningRecordsUsecase))
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
    @DisplayName("토큰의 유저와 두 날짜를 유스케이스에 넘기고 기록을 명세 모양으로 내린다")
    void 기록을_명세_모양으로_내린다() throws Exception {
        // given -> 유저·날짜가 하나라도 다르면 이 스텁이 맞지 않아 빈 응답이 된다
        given(getMyRunningRecordsUsecase.handle(
                new GetMyRunningRecordsQuery(USER_ID, LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 3))))
                .willReturn(new GetMyRunningRecordsResult(List.of(
                        new GetMyRunningRecordsResult.RunningRecord(
                                501L, 125L, RunningRoomType.MATCH, 3L,
                                LocalDateTime.of(2026, 9, 28, 19, 0, 30, 123_456_000),
                                5020, 1800, 359, null, "u{~vFvyys@fS]pT_@"))));

        // when
        MvcResult result = mockMvc.perform(request("2026-09-27", "2026-10-03")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> records = records(result);
        assertThat(records).hasSize(1);
        Map<String, Object> record = records.get(0);
        // 필드 순서까지 명세와 같다 — 레코드 선언 순서대로 직렬화된다
        assertThat(record.keySet()).containsExactly(
                "runningRecordId", "runningRoomId", "type", "playerCount", "startedAt",
                "totalDistanceMeters", "totalDurationSeconds", "averagePaceSecondsPerKm",
                "totalElevationGainMeters", "routePolyline");
        assertThat(record)
                .containsEntry("runningRecordId", 501)
                .containsEntry("runningRoomId", 125)
                .containsEntry("type", "MATCH")
                .containsEntry("playerCount", 3)
                // 나노초가 있어도 초 단위까지만 나간다
                .containsEntry("startedAt", "2026-09-28T19:00:30")
                .containsEntry("totalDistanceMeters", 5020)
                .containsEntry("totalDurationSeconds", 1800)
                .containsEntry("averagePaceSecondsPerKm", 359)
                // 값이 없어도 키는 내려간다
                .containsEntry("totalElevationGainMeters", null)
                .containsEntry("routePolyline", "u{~vFvyys@fS]pT_@");
    }

    @Test
    @DisplayName("기간에 기록이 없으면 빈 배열이다")
    void 기록이_없으면_빈_배열이다() throws Exception {
        // given
        given(getMyRunningRecordsUsecase.handle(
                new GetMyRunningRecordsQuery(USER_ID, LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 3))))
                .willReturn(new GetMyRunningRecordsResult(List.of()));

        // when
        MvcResult result = mockMvc.perform(request("2026-09-27", "2026-10-03")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(records(result)).isEmpty();
    }

    @Test
    @DisplayName("종료일이 없으면 400 필수 문구이고 조회하지 않는다")
    void 종료일이_없으면_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get(PATH).queryParam("from", "2026-09-27")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(error(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "조회 종료일은 필수입니다.");
        then(getMyRunningRecordsUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("형식이 다른 날짜는 400 기본 문구다 -> 스프링 변환 문구가 새지 않는다")
    void 형식이_다르면_기본_문구다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(request("2026-9-1", "2026-09-02")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(error(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("없는 날짜도 400 기본 문구다")
    void 없는_날짜도_기본_문구다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(request("2026-02-30", "2026-03-01")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(error(result)).containsEntry("message", "입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("형식 오류와 누락이 함께면 두 문구가 다 나온다")
    void 형식_오류와_누락은_두_문구다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get(PATH).queryParam("from", "abc")).andReturn();

        // then -> 문장 순서는 보장하지 않는다
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat((String) error(result).get("message"))
                .contains("입력값이 올바르지 않습니다.")
                .contains("조회 종료일은 필수입니다.");
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 400 순서 문구다")
    void 순서가_뒤집히면_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(request("2026-10-03", "2026-09-27")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(error(result)).containsEntry("message", "조회 시작일은 종료일보다 늦을 수 없습니다.");
    }

    @Test
    @DisplayName("32일이면 400 기간 문구다")
    void 기간이_넘으면_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(request("2026-09-01", "2026-10-02")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(error(result)).containsEntry("message", "조회 기간은 31일 이하여야 합니다.");
    }

    private static MockHttpServletRequestBuilder request(String from, String to) {
        return get(PATH).queryParam("from", from).queryParam("to", to);
    }

    private static List<Map<String, Object>> records(MvcResult result) throws Exception {
        Map<String, List<Map<String, Object>>> body = JSON_MAPPER.readValue(
                body(result), new TypeReference<>() {
                });
        return body.get("runningRecords");
    }

    private static Map<String, Object> error(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(body(result), new TypeReference<>() {
        });
    }

    private static String body(MvcResult result) throws Exception {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
