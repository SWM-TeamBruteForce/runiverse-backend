package com.runiverse.running_service.unit_test.notification.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.notification.command.read.ReadAllUserNotificationsCommand;
import com.runiverse.running_service.application.notification.exception.InvalidNotificationCursorException;
import com.runiverse.running_service.application.notification.port.in.GetUserNotificationsUsecase;
import com.runiverse.running_service.application.notification.port.in.ReadAllUserNotificationsUsecase;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsQuery;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.notification.controller.UserNotificationController;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 목록 컨트롤러 단위 테스트")
class UserNotificationControllerTest {

    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final GetUserNotificationsResult EMPTY = new GetUserNotificationsResult(List.of(), null, 0L);

    @Mock
    private GetUserNotificationsUsecase getUserNotificationsUsecase;

    @Mock
    private ReadAllUserNotificationsUsecase readAllUserNotificationsUsecase;

    @InjectMocks
    private UserNotificationController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON_MAPPER))
                .build();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(ME.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static JsonNode json(MvcResult result) {
        return JSON_MAPPER.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("항목·관계·초대·다음 커서·안 읽은 수를 명세 키 이름으로 내보낸다")
    void writesSpecShape() throws Exception {
        // given
        given(getUserNotificationsUsecase.handle(new GetUserNotificationsQuery(ME, null, 20)))
                .willReturn(new GetUserNotificationsResult(List.of(new GetUserNotificationsResult.Item(
                        913L, UserNotificationType.RUNNING_INVITED, false, LocalDateTime.of(2026, 10, 28, 12, 3),
                        new GetUserNotificationsResult.Actor(CHULSU, "철수", null, false, RelationshipType.FRIEND),
                        new GetUserNotificationsResult.Invitation(130L, RoomInvitationStatus.PENDING,
                                LocalDateTime.of(2026, 10, 28, 19, 30), 7_000))),
                        "next", 3L));

        // when
        MvcResult result = mockMvc.perform(get("/users/me/notifications")).andReturn();

        // then -> read·deleted가 아니라 isRead·isDeleted다. 관계는 actor 안에 있다
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        JsonNode item = body.get("items").get(0);
        assertThat(item.get("notificationId").asLong()).isEqualTo(913L);
        assertThat(item.get("type").asString()).isEqualTo("RUNNING_INVITED");
        assertThat(item.get("isRead").asBoolean()).isFalse();
        assertThat(item.get("createdAt").asString()).isEqualTo("2026-10-28T12:03:00");
        assertThat(item.get("actor").get("userId").asString()).isEqualTo(CHULSU.toString());
        assertThat(item.get("actor").get("isDeleted").asBoolean()).isFalse();
        assertThat(item.get("actor").get("relationshipType").asString()).isEqualTo("FRIEND");
        assertThat(item.get("actor").get("profileImageUrl").isNull()).isTrue();
        assertThat(item.has("relationshipType")).isFalse();
        assertThat(item.get("invitation").get("status").asString()).isEqualTo("PENDING");
        assertThat(item.get("invitation").get("targetDistanceMeters").asInt()).isEqualTo(7_000);
        assertThat(body.get("nextCursor").asString()).isEqualTo("next");
        assertThat(body.get("unreadCount").asLong()).isEqualTo(3L);
    }

    @Test
    @DisplayName("친구 알림은 invitation이 null이다")
    void friendItemHasNullInvitation() throws Exception {
        // given
        given(getUserNotificationsUsecase.handle(any())).willReturn(new GetUserNotificationsResult(
                List.of(new GetUserNotificationsResult.Item(912L, UserNotificationType.FRIEND_REQUESTED, true,
                        LocalDateTime.of(2026, 10, 28, 12, 0),
                        new GetUserNotificationsResult.Actor(CHULSU, "탈퇴한 사용자", null, true, null), null)),
                null, 0L));

        // when
        JsonNode item = json(mockMvc.perform(get("/users/me/notifications")).andReturn()).get("items").get(0);

        // then
        assertThat(item.get("invitation").isNull()).isTrue();
        assertThat(item.get("actor").get("isDeleted").asBoolean()).isTrue();
        assertThat(item.get("actor").get("relationshipType").isNull()).isTrue();
    }

    @Test
    @DisplayName("빈 cursor는 첫 페이지로 보고, 상한을 넘는 limit은 50으로 줄인다")
    void normalizesCursorAndLimit() throws Exception {
        // given
        given(getUserNotificationsUsecase.handle(new GetUserNotificationsQuery(ME, null, 50))).willReturn(EMPTY);

        // when
        MvcResult result = mockMvc.perform(get("/users/me/notifications").param("cursor", "").param("limit", "100"))
                .andReturn();

        // then -> 스텁이 맞지 않으면 null 결과로 500이 났을 것이다
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("cursor를 그대로 유스케이스에 넘긴다")
    void passesCursor() throws Exception {
        // given
        given(getUserNotificationsUsecase.handle(new GetUserNotificationsQuery(ME, "abc", 20))).willReturn(EMPTY);

        // when
        MvcResult result = mockMvc.perform(get("/users/me/notifications").param("cursor", "abc")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("limit이 1보다 작으면 400이고 조회하지 않는다")
    void rejectsNonPositiveLimit() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/me/notifications").param("limit", "0")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("code").asString()).isEqualTo("INVALID_REQUEST");
        then(getUserNotificationsUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("깨진 cursor는 400 INVALID_REQUEST로 나간다")
    void brokenCursorIsBadRequest() throws Exception {
        // given -> 매핑이 빠지면 런타임에 500으로 숨겨진다
        given(getUserNotificationsUsecase.handle(any())).willThrow(new InvalidNotificationCursorException());

        // when
        MvcResult result = mockMvc.perform(get("/users/me/notifications").param("cursor", "!!!")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("code").asString()).isEqualTo("INVALID_REQUEST");
        assertThat(json(result).get("message").asString()).isEqualTo("입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("모두 읽음은 토큰의 사용자로 처리하고 본문 없이 204다")
    void readAllReturnsNoContent() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/users/me/notifications/read")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        then(readAllUserNotificationsUsecase).should().handle(new ReadAllUserNotificationsCommand(ME));
    }
}
