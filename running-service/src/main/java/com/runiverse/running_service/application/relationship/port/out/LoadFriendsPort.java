package com.runiverse.running_service.application.relationship.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.util.List;

public interface LoadFriendsPort {

    // 한 묶음만 updated_at·userId 역순으로 읽는다 — 묶음을 나눠 읽어야 인덱스 순서를 그대로 쓴다.
    // after가 null이면 묶음 처음부터, nicknameFilter가 null이면 거르지 않는다. 온보딩 전인 상대는 담지 않는다
    List<FriendRow> loadGroup(UserId ownerId, UserId viewerId, RelationshipType type,
                              String nicknameFilter, FriendKeyset after, int limit);
}
