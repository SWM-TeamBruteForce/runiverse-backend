package com.runiverse.running_service.presentation.match.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// 한 번에 한 명이다. 친구 여부·지금 상태·보낸 횟수는 저장소를 봐야 해 여기서 보지 않는다 — SendInvitationHandler가 본다
public record SendInvitationRequest(
        @NotNull(message = "초대할 친구는 필수입니다.")
        UUID inviteeId
) {

}
