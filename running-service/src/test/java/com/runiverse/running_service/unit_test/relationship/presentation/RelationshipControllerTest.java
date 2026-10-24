package com.runiverse.running_service.unit_test.relationship.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestResult;
import com.runiverse.running_service.application.relationship.command.cancel.CancelFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.delete.DeleteFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendResult;
import com.runiverse.running_service.application.relationship.exception.CannotFriendSelfException;
import com.runiverse.running_service.application.relationship.exception.FriendNotFoundException;
import com.runiverse.running_service.application.relationship.exception.FriendRequestAlreadyExistsException;
import com.runiverse.running_service.application.relationship.exception.FriendRequestNotFoundException;
import com.runiverse.running_service.application.relationship.exception.FriendTargetNotFoundException;
import com.runiverse.running_service.application.relationship.port.in.AcceptFriendRequestUsecase;
import com.runiverse.running_service.application.relationship.port.in.CancelFriendRequestUsecase;
import com.runiverse.running_service.application.relationship.port.in.DeleteFriendUsecase;
import com.runiverse.running_service.application.relationship.port.in.RequestFriendUsecase;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.relationship.controller.RelationshipController;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
@DisplayName("관계 컨트롤러 단위 테스트")
class RelationshipControllerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID REQUESTER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID TARGET_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private RequestFriendUsecase requestFriendUsecase;

    @Mock
    private AcceptFriendRequestUsecase acceptFriendRequestUsecase;

    @Mock
    private CancelFriendRequestUsecase cancelFriendRequestUsecase;

    @Mock
    private DeleteFriendUsecase deleteFriendUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new RelationshipController(
                requestFriendUsecase, acceptFriendRequestUsecase, cancelFriendRequestUsecase, deleteFriendUsecase))
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
    @DisplayName("친구 요청: 요청자는 토큰에서, 대상은 경로에서 꺼내 넘기고 새 요청이면 201이다")
    void requestFriend() throws Exception {
        // given -> 두 값이 뒤바뀌어 넘어가면 이 스텁이 맞지 않아 빈 응답이 된다
        given(requestFriendUsecase.handle(new RequestFriendCommand(USER_ID, TARGET_ID)))
                .willReturn(new RequestFriendResult(RelationshipType.FRIEND_REQUEST_SENT));

        // when
        MvcResult result = mockMvc.perform(post("/users/{userId}/friend-request", TARGET_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result)).containsExactly(Map.entry("relationshipType", "FRIEND_REQUEST_SENT"));
    }

    @Test
    @DisplayName("친구 요청: 상대가 먼저 요청해 둬서 바로 친구가 되면 만든 게 없으니 200이다")
    void mutualRequestIsOk() throws Exception {
        // given
        given(requestFriendUsecase.handle(new RequestFriendCommand(USER_ID, TARGET_ID)))
                .willReturn(new RequestFriendResult(RelationshipType.FRIEND));

        // when
        MvcResult result = mockMvc.perform(post("/users/{userId}/friend-request", TARGET_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).containsExactly(Map.entry("relationshipType", "FRIEND"));
    }

    @Test
    @DisplayName("친구 요청: 이미 요청했거나 친구면 409 코드와 문구가 그대로 나간다")
    void requestFriendConflict() throws Exception {
        // given -> 노출 정책에서 빠지면 500 INTERNAL_SERVER_ERROR로 가려진다
        given(requestFriendUsecase.handle(new RequestFriendCommand(USER_ID, TARGET_ID)))
                .willThrow(new FriendRequestAlreadyExistsException());

        // when
        MvcResult result = mockMvc.perform(post("/users/{userId}/friend-request", TARGET_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result))
                .containsEntry("code", "FRIEND_REQUEST_ALREADY_EXISTS")
                .containsEntry("message", "이미 요청했거나 이미 친구입니다.");
    }

    @Test
    @DisplayName("친구 요청: 자기 자신에게 보내면 400 CANNOT_FRIEND_SELF다")
    void requestFriendToSelf() throws Exception {
        // given
        given(requestFriendUsecase.handle(new RequestFriendCommand(USER_ID, USER_ID)))
                .willThrow(new CannotFriendSelfException());

        // when
        MvcResult result = mockMvc.perform(post("/users/{userId}/friend-request", USER_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "CANNOT_FRIEND_SELF")
                .containsEntry("message", "자기 자신에게는 친구 요청을 보낼 수 없습니다.");
    }

    @Test
    @DisplayName("친구 요청: 대상이 없으면 404 NOT_FOUND다")
    void requestFriendToUnknownUser() throws Exception {
        // given
        given(requestFriendUsecase.handle(new RequestFriendCommand(USER_ID, TARGET_ID)))
                .willThrow(new FriendTargetNotFoundException());

        // when
        MvcResult result = mockMvc.perform(post("/users/{userId}/friend-request", TARGET_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("친구 요청: UUIDv7이 아닌 경로는 400이고 유스케이스를 부르지 않는다")
    void requestFriendWithInvalidUserId() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/users/{userId}/friend-request", "1-1-1-1-1")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST");
        then(requestFriendUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("요청 수락: 수락자는 토큰에서, 요청자는 경로에서 꺼내 넘기고 200으로 FRIEND를 준다")
    void acceptFriendRequest() throws Exception {
        // given -> 두 값이 뒤바뀌어 넘어가면 이 스텁이 맞지 않아 빈 응답이 된다
        given(acceptFriendRequestUsecase.handle(new AcceptFriendRequestCommand(USER_ID, REQUESTER_ID)))
                .willReturn(new AcceptFriendRequestResult(RelationshipType.FRIEND));

        // when
        MvcResult result = mockMvc.perform(put("/users/{userId}/friend-request", REQUESTER_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).containsExactly(Map.entry("relationshipType", "FRIEND"));
    }

    @Test
    @DisplayName("요청 수락: 수락할 요청이 없으면 404 NOT_FOUND다")
    void acceptFriendRequestNotFound() throws Exception {
        // given
        given(acceptFriendRequestUsecase.handle(new AcceptFriendRequestCommand(USER_ID, REQUESTER_ID)))
                .willThrow(new FriendRequestNotFoundException());

        // when
        MvcResult result = mockMvc.perform(put("/users/{userId}/friend-request", REQUESTER_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result))
                .containsEntry("code", "NOT_FOUND")
                .containsEntry("message", "요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("요청 수락: UUIDv7이 아닌 경로는 400이고 유스케이스를 부르지 않는다")
    void acceptFriendRequestWithInvalidUserId() throws Exception {
        // when -> UUID 타입으로 받으면 변환되는 값이라 형식 검증이 막아야 한다
        MvcResult result = mockMvc.perform(put("/users/{userId}/friend-request", "1-1-1-1-1")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
        then(acceptFriendRequestUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("요청 취소·거절: 호출자는 토큰에서, 상대는 경로에서 꺼내 넘기고 본문 없이 204다")
    void cancelFriendRequest() throws Exception {
        // when
        MvcResult result = mockMvc.perform(delete("/users/{userId}/friend-request", TARGET_ID)).andReturn();

        // then -> 두 값이 뒤바뀌어 넘어가면 이 검증이 깨진다
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        then(cancelFriendRequestUsecase).should().handle(new CancelFriendRequestCommand(USER_ID, TARGET_ID));
    }

    @Test
    @DisplayName("요청 취소·거절: 대기 중인 요청도 친구 관계도 없으면 404 NOT_FOUND다")
    void cancelFriendRequestNotFound() throws Exception {
        // given
        willThrow(new FriendRequestNotFoundException())
                .given(cancelFriendRequestUsecase).handle(new CancelFriendRequestCommand(USER_ID, TARGET_ID));

        // when
        MvcResult result = mockMvc.perform(delete("/users/{userId}/friend-request", TARGET_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result))
                .containsEntry("code", "NOT_FOUND")
                .containsEntry("message", "요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("요청 취소·거절: UUIDv7이 아닌 경로는 400이고 유스케이스를 부르지 않는다")
    void cancelFriendRequestWithInvalidUserId() throws Exception {
        // when
        MvcResult result = mockMvc.perform(delete("/users/{userId}/friend-request", "1-1-1-1-1")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST");
        then(cancelFriendRequestUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("친구 삭제: 삭제하는 사람은 토큰에서, 친구는 경로에서 꺼내 넘기고 본문 없이 204다")
    void deleteFriend() throws Exception {
        // when
        MvcResult result = mockMvc.perform(delete("/users/{userId}/friend", TARGET_ID)).andReturn();

        // then -> 두 값이 뒤바뀌어 넘어가면 이 검증이 깨진다
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        then(deleteFriendUsecase).should().handle(new DeleteFriendCommand(USER_ID, TARGET_ID));
        then(cancelFriendRequestUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("친구 삭제: 친구가 아니면 404 NOT_FOUND다")
    void deleteFriendNotFound() throws Exception {
        // given
        willThrow(new FriendNotFoundException())
                .given(deleteFriendUsecase).handle(new DeleteFriendCommand(USER_ID, TARGET_ID));

        // when
        MvcResult result = mockMvc.perform(delete("/users/{userId}/friend", TARGET_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result))
                .containsEntry("code", "NOT_FOUND")
                .containsEntry("message", "요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("친구 삭제: UUIDv7이 아닌 경로는 400이고 유스케이스를 부르지 않는다")
    void deleteFriendWithInvalidUserId() throws Exception {
        // when
        MvcResult result = mockMvc.perform(delete("/users/{userId}/friend", "1-1-1-1-1")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST");
        then(deleteFriendUsecase).shouldHaveNoInteractions();
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
