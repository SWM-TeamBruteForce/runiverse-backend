package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InviteErrorCode implements ErrorCode {
    // 지금 시각에 따라 갈리는 판정이라 요청 검증이 아니다 — 매칭의 MATCH_SLOT_CLOSED와 같은 자리다
    INVITE_START_AT_OUT_OF_RANGE("INVITE_START_AT_OUT_OF_RANGE", "선택할 수 없는 시작 시각입니다."),
    // 범위가 운영값이라 Request DTO가 못 보고 유스케이스가 본다 —
    // 클라에게는 요청 검증 실패와 같은 코드로 보인다. 실제 문구는 예외가 운영값으로 만든다
    INVALID_INVITE_OPTION("INVALID_REQUEST", "만들 수 없는 시작 시각 또는 목표 거리입니다."),
    // 모집 중이 아니거나 모집 마감(start_at - 초대 마감 오프셋)이 지났다 — 초대·수락이 함께 쓴다
    RUNNING_INVITATION_CLOSED("RUNNING_INVITATION_CLOSED", "초대를 받을 수 없는 방입니다."),
    // 대상이 친구가 아니다(자기 자신 포함) — 한 명씩 보내므로 그 사람이 왜 안 되는지만 알린다
    NOT_FRIEND("NOT_FRIEND", "친구가 아닙니다."),
    // 친구가 이미 이 방의 참가자다
    INVITEE_ALREADY_IN_ROOM("INVITEE_ALREADY_IN_ROOM", "이미 방에 들어와 있는 친구입니다."),
    // 친구가 다른 매칭 방·초대 방에서 대기 중이다(모집 중·확정 후 시작 전)
    INVITEE_IN_MATCHING("INVITEE_IN_MATCHING", "지금 매칭 중인 친구입니다."),
    // 친구가 시작된 방에 있거나 솔로 방을 열었다 — 솔로는 모집 단계가 없어 시작 전이어도 러닝으로 본다
    INVITEE_RUNNING("INVITEE_RUNNING", "지금 러닝 중인 친구입니다."),
    // 호출자가 이 친구에게 보낸 횟수가 상한(invite.max-invite-count)에 닿았다
    INVITE_COUNT_EXCEEDED("INVITE_COUNT_EXCEEDED", "더 이상 이 친구에게 초대를 보낼 수 없습니다."),
    // 이미 수락·거절했거나 나갔거나 만료됐다(수락은 만료를 마감으로 먼저 거른다) — 거절·나감은 다시 초대받으면 대기로 돌아온다
    RUNNING_INVITATION_NOT_PENDING("RUNNING_INVITATION_NOT_PENDING", "이미 응답한 초대입니다."),
    // 자리가 다 찼다 — 초대는 PENDING으로 남아 누가 나가면 다시 수락할 수 있다
    RUNNING_ROOM_FULL("RUNNING_ROOM_FULL", "방이 가득 찼습니다.");
    private final String code;
    private final String message;
}
