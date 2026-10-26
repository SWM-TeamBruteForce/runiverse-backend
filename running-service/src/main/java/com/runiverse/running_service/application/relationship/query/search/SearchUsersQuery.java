package com.runiverse.running_service.application.relationship.query.search;

import java.util.UUID;

// q는 컨트롤러가 공백만인 값을 걸러 낸 값이다. cursor는 없으면 null, limit은 컨트롤러가 기본값·상한을 적용한 값이다
public record SearchUsersQuery(UUID viewerId, String q, String cursor, int limit) {

}
