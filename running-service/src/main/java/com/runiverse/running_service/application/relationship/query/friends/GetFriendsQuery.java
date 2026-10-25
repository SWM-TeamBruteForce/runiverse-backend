package com.runiverse.running_service.application.relationship.query.friends;

import java.util.UUID;

// q·cursor는 없으면 null이다. limit은 컨트롤러가 기본값·상한을 적용한 값이다
public record GetFriendsQuery(UUID viewerId, UUID targetUserId, String q, String cursor, int limit) {

}
