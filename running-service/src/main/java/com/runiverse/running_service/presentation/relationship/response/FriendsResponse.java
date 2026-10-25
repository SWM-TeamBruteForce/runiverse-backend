package com.runiverse.running_service.presentation.relationship.response;

import com.runiverse.running_service.application.relationship.query.friends.GetFriendsResult;

import java.util.List;
import java.util.UUID;

public record FriendsResponse(List<FriendResponse> items, String nextCursor) {   // 마지막 페이지면 nextCursor는 null

    public static FriendsResponse from(GetFriendsResult result) {
        return new FriendsResponse(result.items().stream()
                .map(FriendResponse::from)
                .toList(),
                result.nextCursor());
    }

    public record FriendResponse(
            UUID userId,
            String nickname,
            String profileImageUrl,      // 사진이 없으면 null
            String relationshipType      // 조회자 기준 — 항목이 조회자 본인이면 null
    ) {

        static FriendResponse from(GetFriendsResult.Friend friend) {
            return new FriendResponse(
                    friend.userId(),
                    friend.nickname(),
                    friend.profileImageUrl(),
                    friend.relationshipType() == null ? null : friend.relationshipType().name());
        }
    }
}
