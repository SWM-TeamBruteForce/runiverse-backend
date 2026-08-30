package com.runiverse.running_service.application.running.query.split;

import java.util.UUID;

// 6-1과 같은 모양이다 — viewerId 하나가 403 판정과 isMe·본인 기준 값을 모두 결정한다
public record GetRunningSplitResultsQuery(Long runningRoomId, UUID viewerId) {

}
