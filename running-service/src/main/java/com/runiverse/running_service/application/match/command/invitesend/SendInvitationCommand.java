package com.runiverse.running_service.application.match.command.invitesend;

import java.util.UUID;

// 한 번에 한 명이다 — 못 보내면 그 사람이 왜 안 되는지를 409로 돌려준다
public record SendInvitationCommand(UUID userId, Long runningRoomId, UUID inviteeId) {

}
