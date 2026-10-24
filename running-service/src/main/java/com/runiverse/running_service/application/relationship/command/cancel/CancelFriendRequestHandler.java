package com.runiverse.running_service.application.relationship.command.cancel;

import com.runiverse.running_service.application.relationship.common.RelationshipEnder;
import com.runiverse.running_service.application.relationship.exception.FriendRequestNotFoundException;
import com.runiverse.running_service.application.relationship.port.in.CancelFriendRequestUsecase;
import com.runiverse.running_service.application.relationship.port.out.LoadUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

// 보낸 쪽이 부르면 취소, 받은 쪽이 부르면 거절이다 — 하는 일이 같아 하나로 두고 로그로만 가른다.
// 그새 상대가 수락해 친구가 됐어도 친구 삭제로 처리한다 — 취소하려던 뜻이 관계를 끊는 것이라서다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CancelFriendRequestHandler implements CancelFriendRequestUsecase {

    // 이 경로로 끊을 수 있는 관계 — 그새 수락돼 친구가 된 경우까지 받는다
    private static final Set<RelationshipType> CANCELLABLE = EnumSet.of(
            RelationshipType.FRIEND_REQUEST_SENT,
            RelationshipType.FRIEND_REQUEST_RECEIVED,
            RelationshipType.FRIEND);
    private final LoadUserRelationshipPort loadUserRelationshipPort;
    private final RelationshipEnder relationshipEnder;

    @Override
    public void handle(CancelFriendRequestCommand command) {
        UserId userId = new UserId(command.userId());
        UserId targetId = new UserId(command.targetId());

        // 1. 두 행을 함께 잠가 읽는다 — 상대의 수락·취소와 엇갈려도 둘 중 하나가 먼저 끝난 상태를 읽는다
        UserRelationshipPair pair = loadUserRelationshipPort.loadPairForUpdate(userId, targetId);
        UserRelationship mine = pair.findMine()
                .orElseThrow(() -> notFound(targetId));

        // 2. 대기 중인 요청과 친구만 끊는다 — 끝난 관계는 끊을 것이 없다
        RelationshipType before = mine.getType();
        if (!CANCELLABLE.contains(before)) {
            throw notFound(targetId);
        }

        // 3. 두 행을 함께 NONE으로 되돌린다 — 상대 행이 짝이 아니면 깨진 데이터라 500이다
        relationshipEnder.end(mine, pair.findTheirs().orElse(null));

        switch (before) {
            case FRIEND_REQUEST_SENT -> log.info("[관계] 친구 요청 취소 성공 - targetUserId={}", targetId.value());
            case FRIEND_REQUEST_RECEIVED -> log.info("[관계] 친구 요청 거절 성공 - targetUserId={}", targetId.value());
            default -> log.info("[관계] 친구 삭제 성공 - targetUserId={}", targetId.value());
        }
    }

    private FriendRequestNotFoundException notFound(UserId targetId) {
        log.info("[관계] 친구 요청 취소·거절 실패: 요청·친구 관계 없음 - targetUserId={}", targetId.value());
        return new FriendRequestNotFoundException();
    }
}
