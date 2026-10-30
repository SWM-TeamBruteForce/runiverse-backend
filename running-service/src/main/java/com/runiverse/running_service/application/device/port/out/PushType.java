package com.runiverse.running_service.application.device.port.out;

// 앱이 data의 type으로 눌렀을 때 갈 화면을 고른다 — 값 이름이 곧 앱과의 계약이다
public enum PushType {
    FRIEND_REQUEST_RECEIVED,
    FRIEND_REQUEST_ACCEPTED,
    RUNNING_INVITATION_RECEIVED,
    RUNNING_INVITATION_ACCEPTED,
    MATCH_CONFIRMED,
    RUNNING_REMINDER
}
