package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.exception.AlreadyLeftRoomException;
import com.runiverse.running_service.domain.running.room.exception.AlreadyRoomPlayerException;
import com.runiverse.running_service.domain.running.room.exception.InvalidCloseAtException;
import com.runiverse.running_service.domain.running.room.exception.InviteCountReachedException;
import com.runiverse.running_service.domain.running.room.exception.NotInvitedException;
import com.runiverse.running_service.domain.running.room.exception.NotRoomPlayerException;
import com.runiverse.running_service.domain.running.room.exception.RoomNotInvitableException;
import com.runiverse.running_service.domain.running.room.exception.RoomNotJoinableException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomTypeRequiredException;
import com.runiverse.running_service.domain.running.room.exception.SelfInvitationException;
import com.runiverse.running_service.domain.running.room.exception.StartAtRequiredException;
import com.runiverse.running_service.domain.running.room.vo.PlayerCount;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
public class RunningRoom {

    private static final int SOLO_MAX_PLAYER = 1;
    private static final int MATCH_MAX_PLAYER = 4;
    private static final int INVITE_MAX_PLAYER = 4;
    private final RunningRoomId runningRoomId;   // 저장 전에는 null
    private final RunningRoomType type;          // 생성 후 바뀌지 않는다
    private final LocalDateTime startAt;
    private LocalDateTime closeAt;         // 방이 실제로 닫힌 시각 — 열려 있는 동안 null
    private final Distance targetDistance;       // 정해진 뒤 바뀌지 않는다
    private RunningRoomStatus status;
    private PlayerCount playerCount;
    private Pace avgPace;       // 시작 전에 참가자가 0이 되면 null — 시작 후에는 바꾸지 않는다
    private final List<RoomSession> sessions;    // 방이 맺고 있는 관계들
    private final List<RoomInvitation> invitations;   // 초대 방만 갖는다 — 방 행을 잠그면 함께 잠긴다

    //
    @Builder
    private RunningRoom(Long runningRoomId, RunningRoomType type, RunningRoomStatus status,
                        LocalDateTime startAt, LocalDateTime closeAt,
                        Integer targetDistance, Integer avgPace,
                        int currentPlayerCount, int maxPlayerCount,
                        List<SessionDraft> sessions, List<InvitationDraft> invitations) {
        this.runningRoomId = runningRoomId == null ? null : new RunningRoomId(runningRoomId);
        if (type == null) {
            throw new RunningRoomTypeRequiredException();
        }
        this.type = type;
        this.status = status == null ? type.initialStatus() : status;
        if (startAt == null) {
            throw new StartAtRequiredException();
        }
        this.startAt = startAt;
        validateCloseAt(this.status, closeAt);
        this.closeAt = closeAt;
        this.targetDistance = targetDistance == null ? null : new Distance(targetDistance);
        this.avgPace = avgPace == null ? null : new Pace(avgPace);
        this.playerCount = new PlayerCount(currentPlayerCount, maxPlayerCount);
        // 세션은 방을 거쳐야만 만들어진다 — 밖에서는 SessionDraft까지만 채울 수 있다
        this.sessions = new ArrayList<>();
        if (sessions != null) {
            sessions.forEach(draft -> this.sessions.add(RoomSession.from(draft)));
        }
        // 초대도 세션과 같다 — 밖에서는 InvitationDraft까지만 채울 수 있다
        this.invitations = new ArrayList<>();
        if (invitations != null) {
            invitations.forEach(draft -> this.invitations.add(RoomInvitation.from(draft)));
        }
    }


    // 솔로 — 모집 단계 없이 확정(MATCHED)된 채로 태어난다
    public static RunningRoom openSolo(UserId userId, RunningPlayerId runningPlayerId,
                                       int avgPace, Integer targetDistance, LocalDateTime startAt) {
        RunningRoom room = builder()
                .type(RunningRoomType.SOLO)
                .startAt(startAt)
                .targetDistance(targetDistance)
                .avgPace(avgPace)
                .currentPlayerCount(1)
                .maxPlayerCount(SOLO_MAX_PLAYER)
                .build();
        room.sessions.add(RoomSession.open(userId, runningPlayerId));
        return room;
    }

    // 매칭 — 항상 1인 방으로 태어나 start_at - 모집마감오프셋까지 모집한다
    public static RunningRoom openMatch(UserId userId, RunningPlayerId runningPlayerId,
                                        int avgPace, Integer targetDistance, LocalDateTime startAt) {
        RunningRoom room = builder()
                .type(RunningRoomType.MATCH)
                .startAt(startAt)
                .targetDistance(targetDistance)
                .avgPace(avgPace)
                .currentPlayerCount(1)
                .maxPlayerCount(MATCH_MAX_PLAYER)
                .build();
        room.sessions.add(RoomSession.open(userId, runningPlayerId));
        return room;
    }

    // 초대 — 만든 사람 혼자 MATCHING으로 태어나 초대 수락으로만 사람이 들어온다.
    // 후보 스캔은 type='MATCH'만 보므로 여기서 막을 것은 없다
    public static RunningRoom openInvite(UserId userId, RunningPlayerId runningPlayerId,
                                         int avgPace, Integer targetDistance, LocalDateTime startAt) {
        RunningRoom room = builder()
                .type(RunningRoomType.INVITE)
                .startAt(startAt)
                .targetDistance(targetDistance)
                .avgPace(avgPace)
                .currentPlayerCount(1)
                .maxPlayerCount(INVITE_MAX_PLAYER)
                .build();
        room.sessions.add(RoomSession.open(userId, runningPlayerId));
        return room;
    }

    public void start() {
        this.status = status.transitionTo(RunningRoomStatus.STARTED);
    }

    // 종료 — 상태와 닫힌 시각이 한 번에 확정된다(둘이 갈라지면 조회가 거짓말을 한다)
    public void finish(LocalDateTime closeAt) {
        this.status = status.transitionTo(RunningRoomStatus.FINISHED);
        this.closeAt = closeAt;
    }

    public void cancel(LocalDateTime closeAt) {
        this.status = status.transitionTo(RunningRoomStatus.CANCELLED);
        this.closeAt = closeAt;
        expirePendingInvitations();
    }

    // 참가자 페이스는 다른 애그리거트라 application이 읽어 넘긴다
    public void recalculateAvgPace(List<Pace> paces) {
        if (paces.isEmpty()) {
            this.avgPace = null;
            return;
        }
        int sum = paces.stream().mapToInt(Pace::secondsPerKm).sum();
        this.avgPace = new Pace(sum / paces.size());
    }

    // 후보 스캔이 걸러도 애그리거트가 다시 지킨다 — 스캔과 합류 사이에 자리가 찰 수 있다
    public void join(UserId userId, RunningPlayerId runningPlayerId) {
        if (!canJoin()) {
            throw new RoomNotJoinableException();
        }
        RoomSession existing = findSession(userId).orElse(null);
        if (existing == null) {
            this.playerCount = playerCount.join();
            this.sessions.add(RoomSession.open(userId, runningPlayerId));
            return;
        }
        if (existing.isConnected()) {
            throw new AlreadyRoomPlayerException();   // 한 플레이어 = 최대 한 방
        }
        // 전에 거쳐 간 방이다 — 키가 유저라 행을 새로 만들지 않고 되살린다.
        // 몇 번을 나갔든 다시 받아 준다 — 이탈 이력은 후보 순위만 낮출 뿐 문을 잠그지 않는다
        this.playerCount = playerCount.join();
        existing.reassign(runningPlayerId);
    }

    // start_at·target_distance는 조회가 등가로 거르고, 방은 모집 상태와 자리만 본다.
    // 페이스는 합류 자격이 아니라 후보를 고르는 순서다(MatchRoomAssigner)
    public boolean canJoin() {
        return status == RunningRoomStatus.MATCHING
                && playerCount.canJoin()
                // 참가자가 0이면 평균이 지워지고 그 방은 같은 순간 닫힌다 —
                // 순위를 매길 기준이 없는 방에 붙이지 않는다
                && avgPace != null;
    }

    // 모집 마감(start_at - 오프셋) 도달 — 인원 수와 무관하게 확정된다(1인이면 1인으로 확정돼 혼자 뛴다).
    // 남은 초대는 더 이상 수락할 수 없어 만료로 끝낸다
    public void closeMatching() {
        this.status = status.transitionTo(RunningRoomStatus.MATCHED);
        expirePendingInvitations();
    }

    // 나갔던 사람이 돌아옴 — 러닝 중에도 가능해서 join()의 모집 조건을 타지 않는다
    public void rejoin(UserId userId) {
        if (status.isTerminal()) {
            throw new RoomNotJoinableException();   // 끝났거나 취소된 방엔 돌아올 수 없다
        }
        RoomSession session = session(userId);
        if (session.isConnected()) {
            throw new AlreadyRoomPlayerException();
        }
        this.playerCount = playerCount.join();   // 그새 자리가 찼으면 RoomIsFullException
        session.rejoin();
    }

    public void leave(UserId userId, LocalDateTime leftAt) {
        RoomSession session = session(userId);                    // 1. 이 방 참가자인가
        if (!session.isConnected()) {
            throw new AlreadyLeftRoomException();                // 2. 이미 나갔는가 — 중복 이탈 방어
        }
        PlayerCount left = playerCount.leave();                  // 3. 계산만 한다, 아직 반영 안 함
        boolean lastOne = left.current() == 0;
        RunningRoomStatus nextStatus = lastOne                   // 4. 상태 전이 가능 여부까지 여기서 확인한다
                ? status.transitionTo(RunningRoomStatus.CANCELLED)
                : status;
        this.playerCount = left;                                 // 5. 여기부터 확정 — 더는 예외가 나지 않는다
        this.status = nextStatus;
        if (lastOne) {
            this.closeAt = leftAt;
            expirePendingInvitations();   // 방이 취소돼 남은 초대는 수락할 곳이 없다
        }
        session.leave();
        // 초대로 들어온 사람이면 "지금 방에 있음"을 거둔다 — 그래야 다시 초대할 수 있다
        if (invitationStatusOf(userId).orElse(null) == RoomInvitationStatus.ACCEPTED) {
            changeInvitationStatus(userId, RoomInvitationStatus.LEFT);
        }
    }

    // 완주로 자리를 비운다 — 인원도 방 상태도 건드리지 않는다.
    // leave()를 쓰면 마지막 완주자가 인원을 0으로 만들어 방이 CANCELLED가 된다
    public void finishSession(UserId userId) {
        session(userId).finish();
    }

    // 초대 — 한 명씩 보낸다. 못 보내는 경우는 건너뛰지 않고 막는다.
    // 시각 기준 마감·친구·다른 방의 활성 신청은 운영값이거나 다른 애그리거트라 애플리케이션이 먼저 본다.
    // 이미 방에 있음·횟수 상한도 애플리케이션이 isInRoom·hasReachedInviteLimit로 먼저 409를 내고, 여기는 방어선이다
    public void invite(UserId inviterId, UserId inviteeId, int maxInviteCount) {
        if (type != RunningRoomType.INVITE || status != RunningRoomStatus.MATCHING) {
            throw new RoomNotInvitableException();
        }
        if (!session(inviterId).isConnected()) {
            throw new NotRoomPlayerException();   // 나간 사람은 부를 수 없다
        }
        if (inviterId.equals(inviteeId)) {
            throw new SelfInvitationException();
        }
        if (isInRoom(inviteeId)) {
            throw new AlreadyRoomPlayerException();
        }
        if (hasReachedInviteLimit(inviterId, inviteeId, maxInviteCount)) {
            throw new InviteCountReachedException();   // 상태도 건드리지 않는다
        }
        // 거절했거나 나간 사람은 다른 보낸 사람의 행까지 함께 대기로 되살린다
        RoomInvitationStatus current = invitationStatusOf(inviteeId).orElse(null);
        if (current != null && current != RoomInvitationStatus.PENDING) {
            changeInvitationStatus(inviteeId, RoomInvitationStatus.PENDING);
        }
        sentBy(inviterId, inviteeId).ifPresentOrElse(
                RoomInvitation::resend,   // 대기 중에 다시 보냄(독촉) 포함
                () -> invitations.add(RoomInvitation.open(inviterId, inviteeId)));
    }

    // 지금 이 방의 참가자인가 — 나간 세션은 아니다
    public boolean isInRoom(UserId userId) {
        return findSession(userId).filter(RoomSession::isConnected).isPresent();
    }

    // 상한은 보낸 사람마다 센다 — 아직 보낸 적 없으면 닿지 않았다
    public boolean hasReachedInviteLimit(UserId inviterId, UserId inviteeId, int maxInviteCount) {
        return sentBy(inviterId, inviteeId)
                .filter(invitation -> invitation.getInviteCount().hasReached(maxInviteCount))
                .isPresent();
    }

    // 수락 — 매칭 합류와 같게 자리를 잡고, 누구의 초대로 들어오든 받는 사람의 행을 모두 수락으로 바꾼다
    public void acceptInvitation(UserId inviteeId, RunningPlayerId runningPlayerId) {
        // 합류보다 먼저 본다 — 합류 뒤에 터지면 인원만 오른 채 남는다
        received(inviteeId).get(0).getStatus().transitionTo(RoomInvitationStatus.ACCEPTED);
        join(inviteeId, runningPlayerId);   // 자리가 없거나 모집 중이 아니면 초대는 PENDING 그대로다
        changeInvitationStatus(inviteeId, RoomInvitationStatus.ACCEPTED);
    }

    // 거절 — 행을 지우지 않는다. 현황에서는 빠지지만 다시 초대할 때 보낸 횟수를 이어서 세야 한다
    public void declineInvitation(UserId inviteeId) {
        changeInvitationStatus(inviteeId, RoomInvitationStatus.DECLINED);
    }

    // 받는 사람 기준이라 행 하나만 보면 된다 — 받은 초대가 없으면 빈 값
    public Optional<RoomInvitationStatus> invitationStatusOf(UserId inviteeId) {
        return invitations.stream()
                .filter(invitation -> invitation.isFor(inviteeId))
                .map(RoomInvitation::getStatus)
                .findFirst();
    }

    public boolean isNew() {
        return runningRoomId == null;
    }

    public Optional<RunningRoomId> getRunningRoomId() {
        return Optional.ofNullable(runningRoomId);
    }

    public Optional<Distance> getTargetDistance() {
        return Optional.ofNullable(targetDistance);
    }

    public Optional<LocalDateTime> getCloseAt() {
        return Optional.ofNullable(closeAt);
    }

    public Optional<Pace> getAvgPace() {
        return Optional.ofNullable(avgPace);
    }

    public List<RoomSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public List<RoomInvitation> getInvitations() {
        return Collections.unmodifiableList(invitations);
    }

    private RoomSession session(UserId userId) {
        return findSession(userId).orElseThrow(NotRoomPlayerException::new);
    }

    private Optional<RoomSession> findSession(UserId userId) {
        return sessions.stream().filter(session -> session.isSameUser(userId)).findFirst();
    }

    private Optional<RoomInvitation> sentBy(UserId inviterId, UserId inviteeId) {
        return invitations.stream()
                .filter(invitation -> invitation.isFor(inviteeId) && invitation.isSentBy(inviterId))
                .findFirst();
    }

    private List<RoomInvitation> received(UserId inviteeId) {
        List<RoomInvitation> received = invitations.stream()
                .filter(invitation -> invitation.isFor(inviteeId))
                .toList();
        if (received.isEmpty()) {
            throw new NotInvitedException();
        }
        return received;
    }

    // 같은 받는 사람의 행은 늘 같은 상태라 첫 행으로 전이를 먼저 확인한다 — 반쯤 바뀐 채로 남지 않는다
    private void changeInvitationStatus(UserId inviteeId, RoomInvitationStatus next) {
        List<RoomInvitation> received = received(inviteeId);
        received.get(0).getStatus().transitionTo(next);
        received.forEach(invitation -> invitation.changeStatus(next));
    }

    // 응답 전인 초대만 끝낸다 — 거절·나감은 이미 응답이 끝나 그대로 둔다.
    // 받는 사람마다 행이 여럿이어도 PENDING이면 모두 PENDING이라 행 단위로 바꿔도 받는 사람 기준이 지켜진다
    private void expirePendingInvitations() {
        invitations.stream()
                .filter(invitation -> invitation.getStatus() == RoomInvitationStatus.PENDING)
                .forEach(invitation -> invitation.changeStatus(RoomInvitationStatus.EXPIRED));
    }

    // 닫힌 시각은 종료 상태와 짝이다 — DB에서 복원할 때 어긋난 행을 여기서 잡는다
    private static void validateCloseAt(RunningRoomStatus status, LocalDateTime closeAt) {
        if (status.isTerminal() != (closeAt != null)) {
            throw new InvalidCloseAtException();
        }
    }
}
