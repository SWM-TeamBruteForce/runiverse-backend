package com.runiverse.running_service.presentation.match.controller;

import com.runiverse.running_service.application.match.command.apply.ApplyMatchCommand;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchResult;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchCommand;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationCommand;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationResult;
import com.runiverse.running_service.application.match.command.invitedecline.DeclineInvitationCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomResult;
import com.runiverse.running_service.application.match.command.invitesend.SendInvitationCommand;
import com.runiverse.running_service.application.match.port.in.AcceptInvitationUsecase;
import com.runiverse.running_service.application.match.port.in.ApplyMatchUsecase;
import com.runiverse.running_service.application.match.port.in.CancelMatchUsecase;
import com.runiverse.running_service.application.match.port.in.DeclineInvitationUsecase;
import com.runiverse.running_service.application.match.port.in.GetMatchSlotsUsecase;
import com.runiverse.running_service.application.match.port.in.OpenInviteRoomUsecase;
import com.runiverse.running_service.application.match.port.in.SendInvitationUsecase;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsQuery;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;
import com.runiverse.running_service.presentation.match.request.ApplyMatchRequest;
import com.runiverse.running_service.presentation.match.request.MatchSlotsRequest;
import com.runiverse.running_service.presentation.match.request.OpenInviteRoomRequest;
import com.runiverse.running_service.presentation.match.request.SendInvitationRequest;
import com.runiverse.running_service.presentation.match.response.AcceptInvitationResponse;
import com.runiverse.running_service.presentation.match.response.ApplyMatchResponse;
import com.runiverse.running_service.presentation.match.response.MatchSlotsResponse;
import com.runiverse.running_service.presentation.match.response.OpenInviteRoomResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 모집 단계(매칭 방·초대 방)의 REST — 신청·나가기·슬롯 조회와 초대 방 개설·초대·수락·거절.
// 스트림(MatchStreamController)과 경로 앞부분을 공유하지만 SSE 연결 수명을 다루는 일이라 나눈다.
// 내 신청은 토큰 주체가 곧 대상이라 경로에 ID가 없고, 특정 방을 가리키는 초대만 방 ID를 쓴다
@RestController
@RequestMapping("match-rooms")
@RequiredArgsConstructor
public class MatchRoomController {

    private final GetMatchSlotsUsecase getMatchSlotsUsecase;
    private final ApplyMatchUsecase applyMatchUsecase;
    private final CancelMatchUsecase cancelMatchUsecase;
    private final OpenInviteRoomUsecase openInviteRoomUsecase;
    private final SendInvitationUsecase sendInvitationUsecase;
    private final AcceptInvitationUsecase acceptInvitationUsecase;
    private final DeclineInvitationUsecase declineInvitationUsecase;

    // 누가 보든 같은 목록이라 사용자를 넘기지 않는다 — 인증은 SecurityConfig가 요구한다
    @GetMapping("slots")
    public ResponseEntity<MatchSlotsResponse> getSlots(@Valid @ModelAttribute MatchSlotsRequest request) {
        GetMatchSlotsResult result = getMatchSlotsUsecase.handle(new GetMatchSlotsQuery(request.dateOrToday()));
        return ResponseEntity.ok(MatchSlotsResponse.from(result));
    }

    @PostMapping
    public ResponseEntity<ApplyMatchResponse> apply(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ApplyMatchRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ApplyMatchResult result = applyMatchUsecase.handle(new ApplyMatchCommand(
                userId, request.scheduledStartAt(), request.targetDistanceMeters()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplyMatchResponse.from(result));
    }

    // 대기 취소와 확정 후 나가기를 겸한다 — 서버가 모집 마감 시각으로 분기한다. 초대 방도 같다.
    // DELETE가 아니다 — 방을 지우는 게 아니라 내가 빠지는 것이라 혼자 나가도 방은 남는다
    @PostMapping("leave")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt) {
        cancelMatchUsecase.handle(new CancelMatchCommand(UUID.fromString(jwt.getSubject())));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("invite")
    public ResponseEntity<OpenInviteRoomResponse> openInvite(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OpenInviteRoomRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        OpenInviteRoomResult result = openInviteRoomUsecase.handle(new OpenInviteRoomCommand(
                userId, request.scheduledStartAt(), request.targetDistanceMeters()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OpenInviteRoomResponse(result.runningRoomId()));
    }

    // 한 명씩 보낸다 — 못 보내면 그 친구가 왜 안 되는지를 409로 알린다. 수락·거절은 받는 사람이 토큰 주체라 본문이 없다
    @PostMapping("{runningRoomId}/invitations")
    public ResponseEntity<Void> sendInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(value = 1, message = "입력값이 올바르지 않습니다.") Long runningRoomId,
            @Valid @RequestBody SendInvitationRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        sendInvitationUsecase.handle(new SendInvitationCommand(userId, runningRoomId, request.inviteeId()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("{runningRoomId}/invitations/accept")
    public ResponseEntity<AcceptInvitationResponse> acceptInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(value = 1, message = "입력값이 올바르지 않습니다.") Long runningRoomId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        AcceptInvitationResult result = acceptInvitationUsecase.handle(
                new AcceptInvitationCommand(userId, runningRoomId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AcceptInvitationResponse(result.runningRoomId()));
    }

    @PostMapping("{runningRoomId}/invitations/decline")
    public ResponseEntity<Void> declineInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(value = 1, message = "입력값이 올바르지 않습니다.") Long runningRoomId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        declineInvitationUsecase.handle(new DeclineInvitationCommand(userId, runningRoomId));
        return ResponseEntity.noContent().build();
    }
}
