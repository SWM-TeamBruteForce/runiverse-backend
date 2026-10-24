package com.runiverse.running_service.application.relationship.command.delete;

import com.runiverse.running_service.application.relationship.common.RelationshipEnder;
import com.runiverse.running_service.application.relationship.exception.FriendNotFoundException;
import com.runiverse.running_service.application.relationship.port.in.DeleteFriendUsecase;
import com.runiverse.running_service.application.relationship.port.out.LoadUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 성립한 뒤로는 방향에 의미가 없어 양쪽 누구나 지울 수 있다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeleteFriendHandler implements DeleteFriendUsecase {

    private final LoadUserRelationshipPort loadUserRelationshipPort;
    private final RelationshipEnder relationshipEnder;

    @Override
    public void handle(DeleteFriendCommand command) {
        UserId userId = new UserId(command.userId());
        UserId friendId = new UserId(command.friendId());

        // 1. 두 행을 함께 잠가 읽는다 — 둘이 동시에 지우면 뒤에 온 쪽은 NONE을 읽어 404가 된다
        UserRelationshipPair pair = loadUserRelationshipPort.loadPairForUpdate(userId, friendId);
        UserRelationship mine = pair.findMine()
                .orElseThrow(() -> notFound(friendId));

        // 2. 친구만 지운다 — 요청 중인 관계는 friend-request로 끊는다
        if (mine.getType() != RelationshipType.FRIEND) {
            throw notFound(friendId);
        }

        // 3. 두 행을 함께 NONE으로 되돌린다 — 상대 행이 친구가 아니면 깨진 데이터라 500이다
        relationshipEnder.end(mine, pair.findTheirs().orElse(null));

        log.info("[관계] 친구 삭제 성공 - targetUserId={}", friendId.value());
    }

    private FriendNotFoundException notFound(UserId friendId) {
        log.info("[관계] 친구 삭제 실패: 친구 아님 - targetUserId={}", friendId.value());
        return new FriendNotFoundException();
    }
}
