package com.runiverse.running_service.application.recordcard.query.list;

import java.util.UUID;

// 10-3 — viewerId가 ownerId의 카드 목록을 본다. cursor가 null이면 첫 페이지, limit은 컨트롤러가 50 이하로 줄였다
public record GetRecordCardsQuery(UUID viewerId, UUID ownerId, String cursor, int limit) {

}
