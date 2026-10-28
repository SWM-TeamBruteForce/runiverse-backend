package com.runiverse.running_service.unit_test.match.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchCommand;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationCommand;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationResult;
import com.runiverse.running_service.application.match.command.invitedecline.DeclineInvitationCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomResult;
import com.runiverse.running_service.application.match.command.invitesend.SendInvitationCommand;
import com.runiverse.running_service.application.match.exception.InvalidInviteOptionException;
import com.runiverse.running_service.application.match.exception.InvitationNotFoundException;
import com.runiverse.running_service.application.match.exception.InviteRoomNotFoundException;
import com.runiverse.running_service.application.match.exception.InviteStartAtOutOfRangeException;
import com.runiverse.running_service.application.match.exception.InviteeInMatchingException;
import com.runiverse.running_service.application.match.exception.MatchCooldownException;
import com.runiverse.running_service.application.match.exception.NotFriendException;
import com.runiverse.running_service.application.match.exception.RunningInvitationNotPendingException;
import com.runiverse.running_service.application.match.exception.RunningRoomFullException;
import com.runiverse.running_service.application.match.port.in.AcceptInvitationUsecase;
import com.runiverse.running_service.application.match.port.in.ApplyMatchUsecase;
import com.runiverse.running_service.application.match.port.in.CancelMatchUsecase;
import com.runiverse.running_service.application.match.port.in.DeclineInvitationUsecase;
import com.runiverse.running_service.application.match.port.in.GetMatchSlotsUsecase;
import com.runiverse.running_service.application.match.port.in.OpenInviteRoomUsecase;
import com.runiverse.running_service.application.match.port.in.SendInvitationUsecase;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsQuery;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;
import com.runiverse.running_service.infrastructure.config.JacksonConfig;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.match.controller.MatchRoomController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// 모집 단계 REST — 신청·나가기·슬롯 조회와 초대 방 개설·초대·수락·거절.
// 쿨다운만 공통 에러 형식이 아니라 전용 응답을 탄다. 경로의 방 ID와 본문 모양은 컨트롤러가 거른다 —
// 유스케이스로 넘어가면 VO 검증에서 500이 된다
@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 방 컨트롤러 단위 테스트")
class MatchRoomControllerTest {

    // UserId VO가 UUIDv7만 받는다
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new SimpleModule()
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(JacksonConfig.SECONDS)))
            .build();

    @Mock
    private GetMatchSlotsUsecase getMatchSlotsUsecase;

    @Mock
    private ApplyMatchUsecase applyMatchUsecase;

    @Mock
    private CancelMatchUsecase cancelMatchUsecase;

    @Mock
    private OpenInviteRoomUsecase openInviteRoomUsecase;

    @Mock
    private SendInvitationUsecase sendInvitationUsecase;

    @Mock
    private AcceptInvitationUsecase acceptInvitationUsecase;

    @Mock
    private DeclineInvitationUsecase declineInvitationUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new MatchRoomController(
                getMatchSlotsUsecase, applyMatchUsecase, cancelMatchUsecase,
                openInviteRoomUsecase, sendInvitationUsecase,
                acceptInvitationUsecase, declineInvitationUsecase))
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
        MvcResult result = mockMvc.perform(post("/match-rooms")
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
    @DisplayName("슬롯 조회는 날짜를 넘기고 거리별 슬롯을 초 단위 시각으로 내려 준다")
    void slotsResponseShape() throws Exception {
        // given
        LocalDate date = LocalDate.now().plusDays(1);
        given(getMatchSlotsUsecase.handle(any())).willReturn(new GetMatchSlotsResult(List.of(
                new GetMatchSlotsResult.DistanceSlots(3_000, List.of(
                        new GetMatchSlotsResult.MatchSlot(date.atTime(18, 0), 0, false))),
                new GetMatchSlotsResult.DistanceSlots(5_000, List.of(
                        new GetMatchSlotsResult.MatchSlot(date.atTime(18, 0), 0, false),
                        new GetMatchSlotsResult.MatchSlot(date.atTime(18, 30), 3, true))))));

        // when
        MvcResult result = mockMvc.perform(get("/match-rooms/slots").param("date", date.toString()))
                .andReturn();

        // then -> 응답에 조회 날짜를 따로 싣지 않는다
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        verify(getMatchSlotsUsecase).handle(new GetMatchSlotsQuery(date));
        Map<String, Object> body = JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
        assertThat(body.keySet()).containsExactly("distances");
        assertThat(body.get("distances")).isEqualTo(List.of(
                Map.of("targetDistanceMeters", 3_000, "slots", List.of(
                        Map.of("scheduledStartAt", date + "T18:00:00", "waitingCount", 0, "selectable", false))),
                Map.of("targetDistanceMeters", 5_000, "slots", List.of(
                        Map.of("scheduledStartAt", date + "T18:00:00", "waitingCount", 0, "selectable", false),
                        Map.of("scheduledStartAt", date + "T18:30:00", "waitingCount", 3, "selectable", true)))));
    }

    @Test
    @DisplayName("날짜를 생략하면 오늘을 조회한다")
    void slotsDefaultToToday() throws Exception {
        // given
        given(getMatchSlotsUsecase.handle(any())).willReturn(new GetMatchSlotsResult(List.of()));

        // when
        mockMvc.perform(get("/match-rooms/slots"));

        // then
        verify(getMatchSlotsUsecase).handle(new GetMatchSlotsQuery(LocalDate.now()));
    }

    @Test
    @DisplayName("예전 클라가 거리를 붙여 보내도 거절하지 않고 날짜로만 조회한다")
    void ignoresLegacyDistanceParam() throws Exception {
        // given
        given(getMatchSlotsUsecase.handle(any())).willReturn(new GetMatchSlotsResult(List.of()));

        // when
        MvcResult result = mockMvc.perform(get("/match-rooms/slots").param("targetDistanceMeters", "abc"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        verify(getMatchSlotsUsecase).handle(new GetMatchSlotsQuery(LocalDate.now()));
    }

    @Test
    @DisplayName("날짜 형식이 틀리면 변환 오류 문구 대신 공통 문구로 400을 낸다")
    void malformedDateIsInvalidRequest() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/match-rooms/slots").param("date", "2026/07/25"))
                .andReturn();

        // then -> 내부 타입명·입력값이 본문에 실리면 안 된다
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
        verifyNoInteractions(getMatchSlotsUsecase);
    }

    @Test
    @DisplayName("오늘 이전 날짜는 400이다")
    void pastDateIsRejected() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/match-rooms/slots")
                        .param("date", LocalDate.now().minusDays(1).toString()))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "조회 날짜는 오늘 이전일 수 없습니다.");
        verifyNoInteractions(getMatchSlotsUsecase);
    }

    @Test
    @DisplayName("나가기: 본문 없이 토큰 주체로 POST /leave를 받아 204다")
    void leaveIsPostWithNoContent() throws Exception {
        // when -> 방을 지우는 게 아니라 내가 빠지는 것이라 DELETE가 아니다
        MvcResult result = mockMvc.perform(post("/match-rooms/leave")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        verify(cancelMatchUsecase).handle(new CancelMatchCommand(USER_ID));
    }

    @Test
    @DisplayName("초대 방 개설: 토큰 주체와 본문 값을 넘기고 201로 방 ID만 돌려준다")
    void 초대_방_개설은_201로_방_ID를_돌려준다() throws Exception {
        // given -> 시각이 다르게 바인딩되면 이 스텁이 맞지 않아 빈 응답이 된다
        given(openInviteRoomUsecase.handle(new OpenInviteRoomCommand(
                USER_ID, LocalDateTime.of(2026, 10, 26, 19, 30), 7_000)))
                .willReturn(new OpenInviteRoomResult(130L));

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledStartAt":"2026-10-26T19:30:00","targetDistanceMeters":7000}
                                """))
                .andReturn();

        // then -> 방 정보는 스트림이 나르므로 응답은 ID 하나다
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result)).containsExactly(Map.entry("runningRoomId", 130));
    }

    @Test
    @DisplayName("초대 방 개설: 시작 시각이 빠지면 400이고 유스케이스를 부르지 않는다")
    void 초대_방_개설은_시작_시각이_없으면_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDistanceMeters":7000}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "시작 시각은 필수입니다.");
        then(openInviteRoomUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("초대 방 개설: 목표 거리가 빠지면 400이고 유스케이스를 부르지 않는다")
    void 초대_방_개설은_목표_거리가_없으면_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledStartAt":"2026-10-26T19:30:00"}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "목표 거리는 필수입니다.");
        then(openInviteRoomUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("초대 방 개설: 운영값 범위 밖은 정책이 만든 문구로 400이다")
    void 초대_방_개설은_범위_밖_거리에_정책_문구로_400이다() throws Exception {
        // given
        given(openInviteRoomUsecase.handle(any()))
                .willThrow(new InvalidInviteOptionException("목표 거리는 1000m 이상 42195m 이하여야 합니다."));

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledStartAt":"2026-10-26T19:30:00","targetDistanceMeters":500}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "목표 거리는 1000m 이상 42195m 이하여야 합니다.");
    }

    @Test
    @DisplayName("초대 방 개설: 고를 수 없는 시각은 409 INVITE_START_AT_OUT_OF_RANGE로 그대로 나간다")
    void 초대_방_개설은_범위_밖_시각에_409다() throws Exception {
        // given -> 노출 목록에서 빠지면 500으로 가려진다
        given(openInviteRoomUsecase.handle(any())).willThrow(new InviteStartAtOutOfRangeException());

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledStartAt":"2026-10-27T07:30:00","targetDistanceMeters":7000}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result))
                .containsEntry("code", "INVITE_START_AT_OUT_OF_RANGE")
                .containsEntry("message", "선택할 수 없는 시작 시각입니다.");
    }

    @Test
    @DisplayName("친구 초대: 토큰 주체·경로 방 ID·대상 한 명을 넘기고 본문 없이 204다")
    void 친구_초대는_204다() throws Exception {
        // given
        UUID friend = UuidCreator.getTimeOrderedEpoch();

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeId":"%s"}
                                """.formatted(friend)))
                .andReturn();

        // then -> 초대 현황은 스트림이 나른다
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        then(sendInvitationUsecase).should().handle(new SendInvitationCommand(USER_ID, 130L, friend));
    }

    @Test
    @DisplayName("친구 초대: 대상이 빠지면 400이고 유스케이스를 부르지 않는다")
    void 친구_초대는_대상이_없으면_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "초대할 친구는 필수입니다.");
        then(sendInvitationUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("친구 초대: 친구가 매칭 중이면 409 INVITEE_IN_MATCHING으로 이유가 그대로 나간다")
    void 친구_초대는_매칭_중이면_409다() throws Exception {
        // given -> 노출 목록에서 빠지면 500으로 가려진다
        willThrow(new InviteeInMatchingException()).given(sendInvitationUsecase).handle(any());

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeId":"%s"}
                                """.formatted(UuidCreator.getTimeOrderedEpoch())))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result))
                .containsEntry("code", "INVITEE_IN_MATCHING")
                .containsEntry("message", "지금 매칭 중인 친구입니다.");
    }

    @Test
    @DisplayName("친구 초대: 대상이 UUID가 아니면 본문을 읽지 못해 400이다")
    void 친구_초대는_UUID가_아닌_대상에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeId":"not-a-uuid"}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "MALFORMED_REQUEST_BODY");
        then(sendInvitationUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("친구 초대: 1 미만의 방 ID는 400이고 유스케이스를 부르지 않는다")
    void 친구_초대는_1_미만의_방_ID에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/0/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeId":"%s"}
                                """.formatted(UuidCreator.getTimeOrderedEpoch())))
                .andReturn();

        // then
        assertInvalidRequest(result);
        then(sendInvitationUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("친구 초대: 친구가 아니면 409 NOT_FRIEND로 그대로 나간다")
    void 친구_초대는_친구가_아니면_409다() throws Exception {
        // given -> 노출 목록에서 빠지면 500으로 가려진다
        willThrow(new NotFriendException()).given(sendInvitationUsecase).handle(any());

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeId":"%s"}
                                """.formatted(UuidCreator.getTimeOrderedEpoch())))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result))
                .containsEntry("code", "NOT_FRIEND")
                .containsEntry("message", "친구가 아닙니다.");
    }

    @Test
    @DisplayName("친구 초대: 방이 없거나 참가자가 아니면 404 NOT_FOUND다")
    void 친구_초대는_방을_못_찾으면_404다() throws Exception {
        // given
        willThrow(new InviteRoomNotFoundException()).given(sendInvitationUsecase).handle(any());

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeId":"%s"}
                                """.formatted(UuidCreator.getTimeOrderedEpoch())))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("초대 수락: 본문 없이 토큰 주체와 경로 방 ID로 수락하고 201로 방 ID만 돌려준다")
    void 초대_수락은_201로_방_ID를_돌려준다() throws Exception {
        // given
        given(acceptInvitationUsecase.handle(new AcceptInvitationCommand(USER_ID, 130L)))
                .willReturn(new AcceptInvitationResult(130L));

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations/accept")).andReturn();

        // then -> 방 정보는 스트림을 연 직후의 입장 스냅샷이 나른다
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result)).containsExactly(Map.entry("runningRoomId", 130));
    }

    @Test
    @DisplayName("초대 수락: 자리가 차면 409 RUNNING_ROOM_FULL로 그대로 나간다")
    void 초대_수락은_자리가_없으면_409다() throws Exception {
        // given
        given(acceptInvitationUsecase.handle(any())).willThrow(new RunningRoomFullException());

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations/accept")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result))
                .containsEntry("code", "RUNNING_ROOM_FULL")
                .containsEntry("message", "방이 가득 찼습니다.");
    }

    @Test
    @DisplayName("초대 수락: 받은 초대가 없으면 404 NOT_FOUND다")
    void 초대_수락은_초대가_없으면_404다() throws Exception {
        // given -> 방 ID만 알아서는 들어갈 수 없다
        given(acceptInvitationUsecase.handle(any())).willThrow(new InvitationNotFoundException());

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations/accept")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("초대 수락: 1 미만의 방 ID는 400이고 유스케이스를 부르지 않는다")
    void 초대_수락은_1_미만의_방_ID에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/0/invitations/accept")).andReturn();

        // then
        assertInvalidRequest(result);
        then(acceptInvitationUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("초대 거절: 토큰 주체와 경로 방 ID로 거절하고 204다")
    void 초대_거절은_204다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations/decline")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        then(declineInvitationUsecase).should().handle(new DeclineInvitationCommand(USER_ID, 130L));
    }

    @Test
    @DisplayName("초대 거절: 대기 중이 아니면 409 RUNNING_INVITATION_NOT_PENDING으로 그대로 나간다")
    void 초대_거절은_대기_중이_아니면_409다() throws Exception {
        // given
        willThrow(new RunningInvitationNotPendingException()).given(declineInvitationUsecase).handle(any());

        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/130/invitations/decline")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result))
                .containsEntry("code", "RUNNING_INVITATION_NOT_PENDING")
                .containsEntry("message", "이미 응답한 초대입니다.");
    }

    @Test
    @DisplayName("초대 거절: 1 미만의 방 ID는 400이고 유스케이스를 부르지 않는다")
    void 초대_거절은_1_미만의_방_ID에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/match-rooms/-1/invitations/decline")).andReturn();

        // then
        assertInvalidRequest(result);
        then(declineInvitationUsecase).shouldHaveNoInteractions();
    }

    private static void assertInvalidRequest(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
