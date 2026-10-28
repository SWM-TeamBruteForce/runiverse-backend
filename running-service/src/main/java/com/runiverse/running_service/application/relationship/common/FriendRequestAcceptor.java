package com.runiverse.running_service.application.relationship.common;

import com.runiverse.running_service.application.common.port.out.SendUserNotificationPort;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.domain.notification.vo.FriendAcceptedPayload;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 받은 요청을 친구로 확정한다 — 요청 수락과 서로 요청이 함께 쓴다.
// 두 행은 호출하는 쪽이 loadPairForUpdate로 잠가 읽은 것이어야 한다
@Component
@RequiredArgsConstructor
public class FriendRequestAcceptor {

    private final SaveUserRelationshipPort saveUserRelationshipPort;
    private final SendUserNotificationPort sendUserNotificationPort;

    // mine이 받은 요청인지는 호출하는 쪽이 이미 봤다 — 결과가 404인지 수락인지 유스케이스마다 달라서다
    public void accept(UserRelationship mine, UserRelationship theirs) {
        // 상대 행은 반드시 보낸 요청이다 — 아니면 짝이 깨진 데이터라 사용자 잘못이 아니다
        if (theirs == null || theirs.getType() != RelationshipType.FRIEND_REQUEST_SENT) {
            throw new IllegalStateException("친구 요청의 짝이 맞지 않습니다.");
        }
        mine.accept();
        theirs.accept();
        saveUserRelationshipPort.save(mine);
        saveUserRelationshipPort.save(theirs);
        // 요청했던 사람에게 수락됐다고 알린다 — mine이 수락한 쪽, theirs가 요청했던 쪽이다
        sendUserNotificationPort.send(theirs.getUserId(), new FriendAcceptedPayload(mine.getUserId()));
    }
}
