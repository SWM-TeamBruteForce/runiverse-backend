package com.runiverse.running_service.application.user.query.profile;

import java.util.UUID;

// 본인 여부를 가려야 해서 조회하는 쪽과 대상을 함께 받는다
public record GetUserProfileQuery(
        UUID viewerId,
        UUID targetUserId
) {

}
