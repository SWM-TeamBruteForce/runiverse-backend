package com.runiverse.running_service.presentation.relationship.controller;

import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestResult;
import com.runiverse.running_service.application.relationship.command.cancel.CancelFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.delete.DeleteFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendResult;
import com.runiverse.running_service.application.relationship.port.in.AcceptFriendRequestUsecase;
import com.runiverse.running_service.application.relationship.port.in.CancelFriendRequestUsecase;
import com.runiverse.running_service.application.relationship.port.in.DeleteFriendUsecase;
import com.runiverse.running_service.application.relationship.port.in.GetFriendRecommendationsUsecase;
import com.runiverse.running_service.application.relationship.port.in.GetFriendsUsecase;
import com.runiverse.running_service.application.relationship.port.in.RequestFriendUsecase;
import com.runiverse.running_service.application.relationship.port.in.SearchUsersUsecase;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsQuery;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsResult;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsQuery;
import com.runiverse.running_service.application.relationship.query.recommendation.GetFriendRecommendationsResult;
import com.runiverse.running_service.application.relationship.query.search.SearchUsersQuery;
import com.runiverse.running_service.application.relationship.query.search.SearchUsersResult;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.presentation.relationship.response.FriendRecommendationsResponse;
import com.runiverse.running_service.presentation.relationship.response.FriendsResponse;
import com.runiverse.running_service.presentation.relationship.response.RelationshipTypeResponse;
import com.runiverse.running_service.presentation.relationship.response.UserSearchResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 경로는 /users 아래지만 다루는 리소스가 관계라 relationship에 둔다
@RestController
@RequestMapping("users")
@RequiredArgsConstructor
public class RelationshipController {

    // UserId VO가 받는 UUIDv7만 — UUID 타입으로 받으면 1-1-1-1-1 같은 값도 변환된다
    private static final String USER_ID_FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    // 커서 페이지네이션 — 기본 20, 넘으면 50으로 줄인다
    private static final int MAX_LIMIT = 50;
    private final RequestFriendUsecase requestFriendUsecase;
    private final AcceptFriendRequestUsecase acceptFriendRequestUsecase;
    private final CancelFriendRequestUsecase cancelFriendRequestUsecase;
    private final DeleteFriendUsecase deleteFriendUsecase;
    private final GetFriendsUsecase getFriendsUsecase;
    private final GetFriendRecommendationsUsecase getFriendRecommendationsUsecase;
    private final SearchUsersUsecase searchUsersUsecase;

    // 경로의 userId는 요청을 받을 사람이다 — 요청하는 사람은 토큰에서만 온다
    @PostMapping("/{userId}/friend-request")
    public ResponseEntity<RelationshipTypeResponse> requestFriend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = USER_ID_FORMAT, message = "입력값이 올바르지 않습니다.") String userId
    ) {
        RequestFriendResult result = requestFriendUsecase.handle(new RequestFriendCommand(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(userId)));
        // 상대가 먼저 요청해 뒀으면 새 요청이 아니라 친구 확정이다 — 만든 게 없으니 201이 아니다
        HttpStatus status = result.relationshipType() == RelationshipType.FRIEND ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(new RelationshipTypeResponse(result.relationshipType().name()));
    }

    // 경로의 userId는 요청을 보낸 사람이다 — 수락하는 사람은 토큰에서만 온다
    @PutMapping("/{userId}/friend-request")
    public ResponseEntity<RelationshipTypeResponse> acceptFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = USER_ID_FORMAT, message = "입력값이 올바르지 않습니다.") String userId
    ) {
        AcceptFriendRequestResult result = acceptFriendRequestUsecase.handle(new AcceptFriendRequestCommand(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(userId)));
        return ResponseEntity.ok(new RelationshipTypeResponse(result.relationshipType().name()));
    }

    // 경로의 userId는 요청을 주고받은 상대다 — 취소인지 거절인지는 누가 보냈는지로 서버가 가른다
    @DeleteMapping("/{userId}/friend-request")
    public ResponseEntity<Void> cancelFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = USER_ID_FORMAT, message = "입력값이 올바르지 않습니다.") String userId
    ) {
        cancelFriendRequestUsecase.handle(new CancelFriendRequestCommand(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(userId)));
        return ResponseEntity.noContent().build();
    }

    // 경로의 userId는 삭제할 친구다 — 성립한 관계라 누가 요청했었는지는 보지 않는다
    @DeleteMapping("/{userId}/friend")
    public ResponseEntity<Void> deleteFriend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = USER_ID_FORMAT, message = "입력값이 올바르지 않습니다.") String userId
    ) {
        deleteFriendUsecase.handle(new DeleteFriendCommand(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(userId)));
        return ResponseEntity.noContent().build();
    }

    // 경로의 userId는 목록 주인이다 — 본인이면 요청까지, 타인이면 친구만 담는 것은 유스케이스가 가른다
    @GetMapping("/{userId}/friends")
    public ResponseEntity<FriendsResponse> getFriends(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = USER_ID_FORMAT, message = "입력값이 올바르지 않습니다.") String userId,
            // 닉네임 최대 길이보다 긴 검색어는 아무것도 찾지 못한다
            @RequestParam(required = false) @Size(max = 16, message = "입력값이 올바르지 않습니다.") String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "입력값이 올바르지 않습니다.") int limit
    ) {
        GetFriendsResult result = getFriendsUsecase.handle(new GetFriendsQuery(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(userId),
                q,
                // 첫 페이지에 cursor=를 빈 값으로 실어 보내도 처음부터 읽는다
                cursor == null || cursor.isBlank() ? null : cursor,
                Math.min(limit, MAX_LIMIT)));
        return ResponseEntity.ok(FriendsResponse.from(result));
    }

    // 남이 볼 경우가 없는 리소스라 /me다 — 대상은 토큰 주체뿐이다
    @GetMapping("/me/friend-recommendations")
    public ResponseEntity<FriendRecommendationsResponse> getFriendRecommendations(@AuthenticationPrincipal Jwt jwt) {
        GetFriendRecommendationsResult result = getFriendRecommendationsUsecase.handle(
                new GetFriendRecommendationsQuery(UUID.fromString(jwt.getSubject())));
        return ResponseEntity.ok(FriendRecommendationsResponse.from(result));
    }

    // 친구 기능의 진입점이라 relationship에 둔다 — /{userId} 경로보다 고정 경로가 먼저 매칭된다
    @GetMapping("/search")
    public ResponseEntity<UserSearchResponse> searchUsers(
            @AuthenticationPrincipal Jwt jwt,
            // 필수지만 required=false로 받는다 — 빠졌을 때 누락 예외가 아니라 검증 실패로 400이 나게 한다.
            // 닉네임 최대 길이보다 긴 검색어는 아무것도 찾지 못한다
            @RequestParam(required = false)
            @NotBlank(message = "입력값이 올바르지 않습니다.")
            @Size(max = 16, message = "입력값이 올바르지 않습니다.") String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "입력값이 올바르지 않습니다.") int limit
    ) {
        SearchUsersResult result = searchUsersUsecase.handle(new SearchUsersQuery(
                UUID.fromString(jwt.getSubject()),
                q,
                // 첫 페이지에 cursor=를 빈 값으로 실어 보내도 처음부터 읽는다
                cursor == null || cursor.isBlank() ? null : cursor,
                Math.min(limit, MAX_LIMIT)));
        return ResponseEntity.ok(UserSearchResponse.from(result));
    }
}
