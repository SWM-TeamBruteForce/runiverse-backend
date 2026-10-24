package com.runiverse.running_service.presentation.relationship.controller;

import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestResult;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendResult;
import com.runiverse.running_service.application.relationship.port.in.AcceptFriendRequestUsecase;
import com.runiverse.running_service.application.relationship.port.in.RequestFriendUsecase;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.presentation.relationship.response.RelationshipTypeResponse;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final RequestFriendUsecase requestFriendUsecase;
    private final AcceptFriendRequestUsecase acceptFriendRequestUsecase;

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
}
