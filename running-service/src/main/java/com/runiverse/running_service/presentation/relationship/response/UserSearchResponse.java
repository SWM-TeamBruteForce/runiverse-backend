package com.runiverse.running_service.presentation.relationship.response;

import com.runiverse.running_service.application.relationship.query.search.SearchUsersResult;

import java.util.List;
import java.util.UUID;

public record UserSearchResponse(List<UserResponse> items, String nextCursor) {   // 마지막 페이지면 nextCursor는 null

    public static UserSearchResponse from(SearchUsersResult result) {
        return new UserSearchResponse(result.items().stream()
                .map(UserResponse::from)
                .toList(),
                result.nextCursor());
    }

    public record UserResponse(
            UUID userId,
            String nickname,
            String profileImageUrl,      // 사진이 없으면 null
            String relationshipType      // 조회자 기준 — 본인은 결과에 없어 늘 값이 있다
    ) {

        static UserResponse from(SearchUsersResult.User user) {
            return new UserResponse(
                    user.userId(),
                    user.nickname(),
                    user.profileImageUrl(),
                    user.relationshipType().name());
        }
    }
}
