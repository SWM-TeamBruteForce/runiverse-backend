package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "running_room_sessions",
        // 유저 → 배정된 방 역방향 조회 (복합 PK 선두가 running_room_id라 미커버).
        // 활성 신청에서 지금 속한 방을 찾을 때 is_connected를 항상 함께 보므로 복합으로 둔다
        indexes = @Index(name = "idx_room_session_user", columnList = "user_id, is_connected")
)
@IdClass(RunningRoomSessionJpaEntity.Pk.class)
@Check(name = "ck_room_session_leave_count", constraints = "leave_count >= 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningRoomSessionJpaEntity extends BaseTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_room_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_room_session_room"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningRoomJpaEntity room;
    // 키를 신청이 아니라 유저로 잡는다 — 취소 후 같은 방에 다시 신청해도 행이 늘지 않는다.
    // users 논리 참조라 FK를 걸지 않는다
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
    // 지금 이 방에 들어와 있는 신청 — 다른 애그리거트라 ID로만 참조한다(Reference by Identity).
    // PK가 아니라 재배정 시 갱신되며, 참가자의 상태·페이스·기록을 읽는 조인 경로다.
    // 무결성은 앱이 관리한다 — 탈퇴해도 세션은 지우지 않는다
    @Column(name = "running_player_id", nullable = false)
    private Long runningPlayerId;
    // 이 유저가 이 방에서 나간 누적 횟수 — 나갔다 다시 들어오면 또 쌓인다. 배정 후보의 순위 재료
    @Column(name = "leave_count", nullable = false)
    private int leaveCount;
    // 이 방에 남아 있는지 여부 — WS 연결 상태가 아니다.
    // 나가면 false, 다시 들어오면 true. 네트워크가 끊긴 것만으로는 바뀌지 않는다
    @Column(name = "is_connected", nullable = false)
    private boolean connected;

    private RunningRoomSessionJpaEntity(RunningRoomJpaEntity room, UUID userId,
                                        Long runningPlayerId, int leaveCount, boolean connected) {
        this.room = room;
        this.userId = userId;
        this.runningPlayerId = runningPlayerId;
        this.leaveCount = leaveCount;
        this.connected = connected;
    }

    public static RunningRoomSessionJpaEntity create(RunningRoomJpaEntity room, UUID userId,
                                                     Long runningPlayerId,
                                                     int leaveCount, boolean connected) {
        return new RunningRoomSessionJpaEntity(room, userId, runningPlayerId, leaveCount, connected);
    }

    // 재배정 — 행을 새로 만들지 않고 신청만 갈아 끼운다
    public void changeRunningPlayerId(Long runningPlayerId) {
        this.runningPlayerId = runningPlayerId;
    }

    public void changeLeaveCount(int leaveCount) {
        this.leaveCount = leaveCount;
    }

    public void changeConnected(boolean connected) {
        this.connected = connected;
    }

    // @IdClass의 필드명은 엔티티의 @Id 필드명과 같고, 타입은 대상 엔티티의 PK 타입이다
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {

        private Long room;
        private UUID userId;
    }
}
