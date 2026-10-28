package com.runiverse.running_service.infrastructure.persistence.notification;

import com.runiverse.running_service.application.notification.port.out.InvitationSnapshot;
import com.runiverse.running_service.application.notification.port.out.LoadInvitationSnapshotsPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InvitationSnapshotPersistenceAdapter implements LoadInvitationSnapshotsPort {

    // idx_room_invitation_invitee(invitee_id)로 받은 초대를 찾고 방을 붙인다
    private static final String SELECT_SNAPSHOTS = """
            SELECT NEW com.runiverse.running_service.application.notification.port.out.InvitationSnapshot(
                r.runningRoomId, i.status, r.startAt, r.targetDistance)
            FROM RunningRoomInvitationJpaEntity i
            JOIN i.room r
            WHERE i.inviteeId = :inviteeId
              AND r.runningRoomId IN :runningRoomIds
            """;

    private final EntityManager entityManager;

    @Override
    public Map<Long, InvitationSnapshot> loadSnapshots(UserId inviteeId, Collection<Long> runningRoomIds) {
        return entityManager.createQuery(SELECT_SNAPSHOTS, InvitationSnapshot.class)
                .setParameter("inviteeId", inviteeId.value())
                .setParameter("runningRoomIds", runningRoomIds)
                .getResultStream()
                // 한 방에서 여러 사람에게 받은 초대는 행이 여럿이지만 상태가 함께 바뀐다 — 아무 행이나 하나면 된다
                .collect(Collectors.toMap(InvitationSnapshot::runningRoomId, snapshot -> snapshot,
                        (kept, ignored) -> kept));
    }
}
