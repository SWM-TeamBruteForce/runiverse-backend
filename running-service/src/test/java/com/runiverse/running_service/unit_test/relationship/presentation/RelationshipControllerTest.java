package com.runiverse.running_service.unit_test.relationship.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestResult;
import com.runiverse.running_service.application.relationship.command.cancel.CancelFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.delete.DeleteFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendResult;
import com.runiverse.running_service.application.relationship.exception.CannotFriendSelfException;
import com.runiverse.running_service.application.relationship.exception.FriendListOwnerNotFoundException;
import com.runiverse.running_service.application.relationship.exception.FriendNotFoundException;
import com.runiverse.running_service.application.relationship.exception.FriendRequestAlreadyExistsException;
import com.runiverse.running_service.application.relationship.exception.FriendRequestNotFoundException;
import com.runiverse.running_service.application.relationship.exception.FriendTargetNotFoundException;
import com.runiverse.running_service.application.relationship.exception.InvalidFriendCursorException;
import com.runiverse.running_service.application.relationship.port.in.AcceptFriendRequestUsecase;
import com.runiverse.running_service.application.relationship.port.in.CancelFriendRequestUsecase;
import com.runiverse.running_service.application.relationship.port.in.DeleteFriendUsecase;
import com.runiverse.running_service.application.relationship.port.in.GetFriendRecommendationsUsecase;
import com.runiverse.running_service.application.relationship.port.in.GetFriendsUsecase;
import com.runiverse.running_service.application.relationship.port.in.RequestFriendUsecase;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsQuery;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsResult;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsQuery;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsResult;
import com.runiverse.running_service.application.relationship.query.recommendation.RecommendationReason;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Mock
    private GetFriendsUsecase getFriendsUsecase;

    @Mock
    private GetFriendRecommendationsUsecase getFriendRecommendationsUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new RelationshipController(
                requestFriendUsecase, acceptFriendRequestUsecase, cancelFriendRequestUsecase, deleteFriendUsecase,
                getFriendsUsecase, getFriendRecommendationsUsecase))
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

    @Test
    @DisplayName("친구 목록: 조회자는 토큰, 주인은 경로에서 꺼내고 항목과 다음 커서를 내린다")
    void getFriends() throws Exception {
        // given -> 값이 없는 필드도 키째 null로 나가야 한다
        UUID friendId = UuidCreator.getTimeOrderedEpoch();
        given(getFriendsUsecase.handle(new GetFriendsQuery(USER_ID, TARGET_ID, "철수", "next-cursor", 10)))
                .willReturn(new GetFriendsResult(List.of(
                        new GetFriendsResult.Friend(friendId, "철수", "https://cdn/a.png", RelationshipType.FRIEND),
                        new GetFriendsResult.Friend(USER_ID, "나", null, null)),
                        "cursor-2"));

        // when
        MvcResult result = mockMvc.perform(get("/users/{userId}/friends", TARGET_ID)
                .param("q", "철수")
                .param("cursor", "next-cursor")
                .param("limit", "10")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> body = body(result);
        assertThat(body).containsEntry("nextCursor", "cursor-2");
        assertThat(body.get("items")).isEqualTo(List.of(
                Map.of("userId", friendId.toString(), "nickname", "철수",
                        "profileImageUrl", "https://cdn/a.png", "relationshipType", "FRIEND"),
                nullableMap("userId", USER_ID.toString(), "nickname", "나",
                        "profileImageUrl", null, "relationshipType", null)));
    }

    @Test
    @DisplayName("친구 목록: 마지막 페이지면 nextCursor는 키째 null이다")
    void getFriendsLastPage() throws Exception {
        // given
        given(getFriendsUsecase.handle(any())).willReturn(new GetFriendsResult(List.of(), null));

        // when
        MvcResult result = mockMvc.perform(get("/users/{userId}/friends", TARGET_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).containsEntry("items", List.of()).containsKey("nextCursor");
        assertThat(body(result).get("nextCursor")).isNull();
    }

    @Test
    @DisplayName("친구 목록: limit이 없으면 20, 50을 넘으면 50으로 넘기고 검색어·커서가 없으면 null이다")
    void getFriendsLimitDefaultAndClamp() throws Exception {
        // given
        given(getFriendsUsecase.handle(new GetFriendsQuery(USER_ID, TARGET_ID, null, null, 20)))
                .willReturn(new GetFriendsResult(List.of(), null));
        given(getFriendsUsecase.handle(new GetFriendsQuery(USER_ID, TARGET_ID, null, null, 50)))
                .willReturn(new GetFriendsResult(List.of(), null));

        // when
        MvcResult byDefault = mockMvc.perform(get("/users/{userId}/friends", TARGET_ID)).andReturn();
        MvcResult clamped = mockMvc.perform(get("/users/{userId}/friends", TARGET_ID)
                .param("limit", "51")).andReturn();

        // then -> 스텁과 다른 값이 넘어가면 엄격 스텁이 실패시킨다
        assertThat(byDefault.getResponse().getStatus()).isEqualTo(200);
        assertThat(clamped.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("친구 목록: 빈 cursor는 첫 페이지로 넘긴다")
    void getFriendsBlankCursorIsFirstPage() throws Exception {
        // given
        given(getFriendsUsecase.handle(new GetFriendsQuery(USER_ID, TARGET_ID, null, null, 20)))
                .willReturn(new GetFriendsResult(List.of(), null));

        // when
        MvcResult result = mockMvc.perform(get("/users/{userId}/friends", TARGET_ID)
                .param("cursor", "")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("친구 목록: limit이 1보다 작거나 숫자가 아니거나 검색어가 16자를 넘으면 400이고 유스케이스를 부르지 않는다")
    void getFriendsInvalidParams() throws Exception {
        // when
        MvcResult zeroLimit = mockMvc.perform(get("/users/{userId}/friends", TARGET_ID)
                .param("limit", "0")).andReturn();
        MvcResult textLimit = mockMvc.perform(get("/users/{userId}/friends", TARGET_ID)
                .param("limit", "abc")).andReturn();
        MvcResult longQuery = mockMvc.perform(get("/users/{userId}/friends", TARGET_ID)
                .param("q", "가".repeat(17))).andReturn();

        // then
        for (MvcResult result : List.of(zeroLimit, textLimit, longQuery)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(body(result))
                    .containsEntry("code", "INVALID_REQUEST")
                    .containsEntry("message", "입력값이 올바르지 않습니다.");
        }
        then(getFriendsUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("친구 목록: UUIDv7이 아닌 경로는 400이고 유스케이스를 부르지 않는다")
    void getFriendsWithInvalidUserId() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/{userId}/friends", "1-1-1-1-1")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST");
        then(getFriendsUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("친구 목록: 깨진 커서는 400 INVALID_REQUEST다")
    void getFriendsWithInvalidCursor() throws Exception {
        // given
        given(getFriendsUsecase.handle(any())).willThrow(new InvalidFriendCursorException());

        // when
        MvcResult result = mockMvc.perform(get("/users/{userId}/friends", TARGET_ID)
                .param("cursor", "broken")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("친구 목록: 목록 주인이 없으면 404 NOT_FOUND다")
    void getFriendsOwnerNotFound() throws Exception {
        // given
        given(getFriendsUsecase.handle(any())).willThrow(new FriendListOwnerNotFoundException());

        // when
        MvcResult result = mockMvc.perform(get("/users/{userId}/friends", TARGET_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result))
                .containsEntry("code", "NOT_FOUND")
                .containsEntry("message", "요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("추천 친구: 토큰 주체의 추천을 이유와 함께 내리고 사진이 없으면 null이다")
    void getFriendRecommendations() throws Exception {
        // given
        UUID runnerId = UuidCreator.getTimeOrderedEpoch();
        UUID similarId = UuidCreator.getTimeOrderedEpoch();
        given(getFriendRecommendationsUsecase.handle(new GetFriendRecommendationsQuery(USER_ID)))
                .willReturn(new GetFriendRecommendationsResult(List.of(
                        new GetFriendRecommendationsResult.Recommendation(
                                runnerId, "철수", "https://cdn/a.png", RecommendationReason.RAN_TOGETHER),
                        new GetFriendRecommendationsResult.Recommendation(
                                similarId, "영희", null, RecommendationReason.SIMILAR_PACE))));

        // when
        MvcResult result = mockMvc.perform(get("/users/me/friend-recommendations")).andReturn();

        // then -> 결과는 모두 요청을 보낼 수 있는 사람이라 relationshipType을 싣지 않는다
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("recommendations")).isEqualTo(List.of(
                Map.of("userId", runnerId.toString(), "nickname", "철수",
                        "profileImageUrl", "https://cdn/a.png", "reason", "RAN_TOGETHER"),
                nullableMap("userId", similarId.toString(), "nickname", "영희",
                        "profileImageUrl", null, "reason", "SIMILAR_PACE")));
    }

    @Test
    @DisplayName("추천 친구: 후보가 없으면 빈 배열이다")
    void getFriendRecommendationsEmpty() throws Exception {
        // given
        given(getFriendRecommendationsUsecase.handle(new GetFriendRecommendationsQuery(USER_ID)))
                .willReturn(new GetFriendRecommendationsResult(List.of()));

        // when
        MvcResult result = mockMvc.perform(get("/users/me/friend-recommendations")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).containsExactly(Map.entry("recommendations", List.of()));
    }

    // Map.of는 null 값을 받지 않는다
    private static Map<String, Object> nullableMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
