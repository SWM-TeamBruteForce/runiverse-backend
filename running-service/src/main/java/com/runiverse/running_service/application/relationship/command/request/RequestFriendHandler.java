package com.runiverse.running_service.application.relationship.command.request;

import com.runiverse.running_service.application.common.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.relationship.common.FriendRequestAcceptor;
import com.runiverse.running_service.application.relationship.exception.CannotFriendSelfException;
import com.runiverse.running_service.application.relationship.exception.FriendRequestAlreadyExistsException;
import com.runiverse.running_service.application.relationship.exception.FriendTargetNotFoundException;
import com.runiverse.running_service.application.relationship.port.in.RequestFriendUsecase;
import com.runiverse.running_service.application.relationship.port.out.EnsureUserRelationshipPairPort;
import com.runiverse.running_service.application.relationship.port.out.LoadUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.SaveUserRelationshipPort;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RequestFriendHandler implements RequestFriendUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final EnsureUserRelationshipPairPort ensureUserRelationshipPairPort;
    private final LoadUserRelationshipPort loadUserRelationshipPort;
    private final SaveUserRelationshipPort saveUserRelationshipPort;
    private final FriendRequestAcceptor friendRequestAcceptor;

    @Override
    public RequestFriendResult handle(RequestFriendCommand command) {
        UserId userId = new UserId(command.userId());
        UserId targetId = new UserId(command.targetId());

        // 1. 자기 자신이면 행을 만들기 전에 막는다 — 도메인까지 가면 500이 된다
        if (userId.equals(targetId)) {
            log.info("[관계] 친구 요청 실패: 자기 자신");
            throw new CannotFriendSelfException();
        }

        // 2. 없는 사용자와 탈퇴한 사용자를 구분하지 않는다 — 행을 넣기 전에 봐야 FK 위반 500이 나지 않는다
        if (loadUserByIdPort.loadById(targetId).isEmpty()) {
            log.info("[관계] 친구 요청 실패: 대상 없음 - targetUserId={}", targetId.value());
            throw new FriendTargetNotFoundException();
        }

        // 3. 처음 맺는 쌍도 잠글 수 있게 행을 먼저 확보하고 두 행을 함께 잠가 읽는다
        ensureUserRelationshipPairPort.ensurePair(userId, targetId);
        UserRelationshipPair pair = loadUserRelationshipPort.loadPairForUpdate(userId, targetId);
        UserRelationship mine = pair.findMine()
                .orElseThrow(() -> new IllegalStateException("확보한 관계 행이 없습니다."));
        UserRelationship theirs = pair.findTheirs()
                .orElseThrow(() -> new IllegalStateException("확보한 관계 행이 없습니다."));

        return switch (mine.getType()) {
            // 4. 처음이거나 끝난 관계 — 새 요청을 만든다
            case NONE -> {
                mine.sendRequest();
                theirs.receiveRequest();
                saveUserRelationshipPort.save(mine);
                saveUserRelationshipPort.save(theirs);
                log.info("[관계] 친구 요청 성공 - targetUserId={}", targetId.value());
                yield new RequestFriendResult(RelationshipType.FRIEND_REQUEST_SENT);
            }
            // 5. 상대가 먼저 요청해 뒀다 — 이미 합의된 것이라 바로 수락한다
            case FRIEND_REQUEST_RECEIVED -> {
                friendRequestAcceptor.accept(mine, theirs);
                log.info("[관계] 친구 요청 수락 성공 - targetUserId={}", targetId.value());
                yield new RequestFriendResult(RelationshipType.FRIEND);
            }
            // 6. 내가 이미 요청했거나 이미 친구다
            case FRIEND_REQUEST_SENT, FRIEND -> {
                log.info("[관계] 친구 요청 실패: 이미 요청했거나 친구 - targetUserId={}", targetId.value());
                throw new FriendRequestAlreadyExistsException();
            }
            // 7. 차단은 아직 만들어지지 않아 생길 수 없다 — 어떻게 보일지는 차단 구현 때 정한다
            case BLOCKED -> throw new IllegalStateException("차단 관계의 친구 요청은 아직 정하지 않았습니다.");
        };
    }
}
