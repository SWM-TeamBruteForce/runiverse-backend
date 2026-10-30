package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import com.runiverse.running_service.infrastructure.persistence.user.UserJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "running_room_invitations",
        // 받은 초대 목록 — 받는 사람이 탈퇴할 때 받은 초대 삭제도 이 인덱스를 탄다
        indexes = @Index(name = "idx_room_invitation_invitee", columnList = "invitee_id")
)
@IdClass(RunningRoomInvitationJpaEntity.Pk.class)
@Check(name = "ck_room_invitation_status",
        constraints = "status in ('PENDING', 'ACCEPTED', 'DECLINED', 'LEFT', 'EXPIRED')")
@Check(name = "ck_room_invitation_invite_count", constraints = "invite_count >= 1")
@Check(name = "ck_room_invitation_users", constraints = "inviter_id <> invitee_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningRoomInvitationJpaEntity extends BaseTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_room_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_room_invitation_room"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningRoomJpaEntity room;
    // 보낸 사람 — 논리 참조다. 보낸 사람이 탈퇴해도 초대는 남는다(방장이 없어 방이 그대로 남는다)
    @Id
    @Column(name = "inviter_id", nullable = false, updatable = false)
    private UUID inviterId;
    @Id
    @Column(name = "invitee_id", nullable = false, updatable = false)
    private UUID inviteeId;
    // 받는 사람 기준의 상태 — 같은 방의 같은 받는 사람 행은 늘 같은 값이다(애그리거트가 지킨다)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomInvitationStatus status;
    // 이 보낸 사람이 이 받는 사람에게 보낸 횟수 — 거절·나감 뒤에도 이어서 센다
    @Column(name = "invite_count", nullable = false)
    private int inviteCount;

    // FK 제약 — 받는 사람이 탈퇴하면 받은 초대가 남지 않는다. 지우는 건 탈퇴 처리가 계정보다 먼저 한다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "invitee_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_room_invitation_invitee")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserJpaEntity invitee;

    private RunningRoomInvitationJpaEntity(RunningRoomJpaEntity room, UUID inviterId, UUID inviteeId,
                                           RoomInvitationStatus status, int inviteCount) {
        this.room = room;
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.status = status;
        this.inviteCount = inviteCount;
    }

    public static RunningRoomInvitationJpaEntity create(RunningRoomJpaEntity room, UUID inviterId,
                                                        UUID inviteeId, RoomInvitationStatus status,
                                                        int inviteCount) {
        return new RunningRoomInvitationJpaEntity(room, inviterId, inviteeId, status, inviteCount);
    }

    public void changeStatus(RoomInvitationStatus status) {
        this.status = status;
    }

    public void changeInviteCount(int inviteCount) {
        this.inviteCount = inviteCount;
    }

    // @IdClass의 필드명은 엔티티의 @Id 필드명과 같고, 타입은 대상 엔티티의 PK 타입이다
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {

        private Long room;
        private UUID inviterId;
        private UUID inviteeId;
    }
}
