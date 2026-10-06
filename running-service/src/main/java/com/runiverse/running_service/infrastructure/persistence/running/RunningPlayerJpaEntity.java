package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
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
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "running_players",
        // 내 신청 조회·중복 신청 검사. 검사가 deleted_at IS NULL을 항상 함께 보므로 복합으로 둔다
        // (논리 참조라 FK 인덱스가 없어서 이게 유일한 진입 인덱스다)
        indexes = @Index(name = "idx_running_player_user", columnList = "user_id, deleted_at")
)
@Check(name = "ck_running_player_status",
        constraints = "status in ('INVITED', 'JOINED', 'RUNNING', 'COMPLETED',"
                + " 'MATCHED_LEFT_PENALTY', 'MATCHED_LEFT_NO_PENALTY',"
                + " 'RUNNING_LEFT_PENALTY', 'RUNNING_LEFT_NO_PENALTY')")
@Check(name = "ck_running_player_avg_pace", constraints = "avg_pace between 120 and 3600")
@Check(name = "ck_running_player_target_distance",
        constraints = "target_distance between 1 and 500000")
@Check(name = "ck_running_player_desired_player_count",
        constraints = "desired_player_count is null or desired_player_count between 2 and 4")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningPlayerJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "running_player_id", nullable = false, updatable = false)
    private Long runningPlayerId;
    // 논리 참조(FK 제약 없음) — 탈퇴해도 지우지 않고 신청 이력으로 남긴다
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RunningPlayerStatus status;
    // 매칭 희망 페이스(초/km) — 입력받지 않고 서버가 유저 평균에서 세팅한다
    @Column(name = "avg_pace", nullable = false)
    private int avgPace;
    // 설정한 목표 거리(미터). 실적인 running_records.total_distance와는 다른 값이다
    @Column(name = "target_distance", nullable = false)
    private int targetDistance;
    // 희망 시작 시각(예약 매칭)
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;
    // 유저 희망 매칭 인원 — 1차에는 입력 UI가 없어 항상 4로 저장되고 합류 조건에도 쓰지 않는다
    @Column(name = "desired_player_count")
    private Integer desiredPlayerCount;
    // 삭제가 아니라 "신청이 끝난 시각" — 대기 취소·초대 거절·이탈·정상 완주 공통.
    // 이 값이 NULL인 신청만 "진행 중"이라, 완주해도 찍어야 유저가 다음 매칭을 걸 수 있다
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private RunningPlayerJpaEntity(UUID userId, RunningPlayerStatus status,
                                   int avgPace, int targetDistance, Integer desiredPlayerCount,
                                   LocalDateTime startAt) {
        this.userId = userId;
        this.status = status;
        this.avgPace = avgPace;
        this.targetDistance = targetDistance;
        this.desiredPlayerCount = desiredPlayerCount;
        this.startAt = startAt;
    }

    // 신청은 언제나 살아 있는 채로 태어난다 — deleted_at은 종료 시점에만 찍힌다
    public static RunningPlayerJpaEntity create(UUID userId, RunningPlayerStatus status,
                                                int avgPace, int targetDistance,
                                                Integer desiredPlayerCount,
                                                LocalDateTime startAt) {
        return new RunningPlayerJpaEntity(userId, status, avgPace, targetDistance,
                desiredPlayerCount, startAt);
    }

    public void changeStatus(RunningPlayerStatus status) {
        this.status = status;
    }

    public void changeDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
