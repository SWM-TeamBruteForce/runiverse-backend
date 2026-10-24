package com.runiverse.running_service.application.relationship.common;

import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 요청 중이거나 친구인 두 행을 함께 NONE으로 되돌린다 — 요청 취소·거절과 친구 삭제가 함께 쓴다.
// 두 행은 호출하는 쪽이 loadPairForUpdate로 잠가 읽은 것이어야 한다
@Component
@RequiredArgsConstructor
public class RelationshipEnder {

    private final SaveUserRelationshipPort saveUserRelationshipPort;

    // mine이 끊을 관계인지는 호출하는 쪽이 이미 봤다 — 어떤 상태를 404로 볼지 유스케이스마다 달라서다
    public void end(UserRelationship mine, UserRelationship theirs) {
        // 상대 행은 내 행과 짝이 맞아야 한다 — 아니면 깨진 데이터라 사용자 잘못이 아니다
        if (theirs == null || theirs.getType() != counterpartOf(mine.getType())) {
            throw new IllegalStateException("관계의 짝이 맞지 않습니다.");
        }
        mine.end();
        theirs.end();
        saveUserRelationshipPort.save(mine);
        saveUserRelationshipPort.save(theirs);
    }

    // 내 행이 이 상태일 때 상대 행이 가져야 하는 상태
    private RelationshipType counterpartOf(RelationshipType type) {
        return switch (type) {
            case FRIEND_REQUEST_SENT -> RelationshipType.FRIEND_REQUEST_RECEIVED;
            case FRIEND_REQUEST_RECEIVED -> RelationshipType.FRIEND_REQUEST_SENT;
            case FRIEND -> RelationshipType.FRIEND;
            case NONE, BLOCKED -> throw new IllegalStateException("끊을 관계가 아닙니다.");
        };
    }
}
