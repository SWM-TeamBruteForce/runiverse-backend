package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "running_rooms",
        indexes = {
                // 매칭 후보 방 스캔 — 앞 4개는 등가 조건, avg_pace는 순위 재료라 마지막.
                // 거르지 않고 값만 실어 나른다(feature-spec 방 배정 기준).
                // 마감 스케줄러(type='MATCH' AND status='MATCHING' AND start_at <= now() + 오프셋)도
                // 앞 4개 컬럼을 그대로 탄다
                @Index(name = "idx_running_room_candidate",
                        columnList = "deleted_at, type, status, start_at, target_distance, avg_pace")
        }
)
@Check(name = "ck_running_room_type", constraints = "type in ('SOLO', 'MATCH', 'INVITE')")
@Check(name = "ck_running_room_status",
        constraints = "status in ('MATCHING', 'MATCHED', 'STARTED', 'FINISHED', 'CANCELLED')")
@Check(name = "ck_running_room_player_count",
        constraints = "max_player_count >= 1 and current_player_count >= 0"
                + " and current_player_count <= max_player_count")
@Check(name = "ck_running_room_avg_pace", constraints = "avg_pace between 120 and 3600")
@Check(name = "ck_running_room_target_distance",
        constraints = "target_distance between 1 and 500000")
@Check(name = "ck_running_room_close_at",
        constraints = "(status in ('FINISHED', 'CANCELLED') and close_at is not null)"
                + " or (status not in ('FINISHED', 'CANCELLED') and close_at is null)")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningRoomJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "running_room_id", nullable = false, updatable = false)
    private Long runningRoomId;
    // 생성 시 정해지고 바뀌지 않는다 — 후보 스캔이 type='MATCH'만 보므로 솔로·초대방이 안 섞인다
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 10)
    private RunningRoomType type;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RunningRoomStatus status;
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;
    // 방이 닫힌 시각 — FINISHED·CANCELLED 때 찍힌다. 열려 있는 동안은 null
    @Column(name = "close_at")
    private LocalDateTime closeAt;
    // 방의 목표 거리(미터). 매칭 조건이라 정해진 뒤에는 바뀌지 않는다 — 목표 없는 솔로는 null
    @Column(name = "target_distance")
    private Integer targetDistance;
    // 참가자 평균 페이스(초/km). 참가·이탈마다 갱신
    @Column(name = "avg_pace")
    private Integer avgPace;
    // 자리 수 — 매칭 4, 솔로 1. 생성 시 정해지고 갱신하지 않는다
    @Column(name = "max_player_count", nullable = false, updatable = false)
    private int maxPlayerCount;
    // 현재 인원. 러닝 중에는 변하지 않으므로 STARTED 이후 값이 곧 출발 인원이다
    @Column(name = "current_player_count", nullable = false)
    private int currentPlayerCount;
    // 관리자 부정 방 숨김 — 스캔·목록 조회는 전부 deleted_at IS NULL만 본다
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private RunningRoomJpaEntity(RunningRoomType type, RunningRoomStatus status,
                                 LocalDateTime startAt, Integer targetDistance, Integer avgPace,
                                 int currentPlayerCount, int maxPlayerCount) {
        this.type = type;
        this.status = status;
        this.startAt = startAt;

        this.targetDistance = targetDistance;
        this.avgPace = avgPace;
        this.currentPlayerCount = currentPlayerCount;
        this.maxPlayerCount = maxPlayerCount;
    }

    // 방은 언제나 살아 있는 채로 태어난다 — 숨김은 관리자 기능이 생길 때 별도 메서드로 붙인다
    public static RunningRoomJpaEntity create(RunningRoomType type, RunningRoomStatus status,
                                              LocalDateTime startAt, Integer targetDistance, Integer avgPace,
                                              int currentPlayerCount, int maxPlayerCount) {
        return new RunningRoomJpaEntity(type, status, startAt,
                targetDistance, avgPace, currentPlayerCount, maxPlayerCount);
    }

    // 방 애그리거트가 바꾸는 값만 연다 — type·start_at·target_distance·max_player_count는 생성 후 불변이다
    public void changeStatus(RunningRoomStatus status) {
        this.status = status;
    }

    public void changeCloseAt(LocalDateTime closeAt) {
        this.closeAt = closeAt;
    }

    public void changeAvgPace(Integer avgPace) {
        this.avgPace = avgPace;
    }

    public void changeCurrentPlayerCount(int currentPlayerCount) {
        this.currentPlayerCount = currentPlayerCount;
    }
}
