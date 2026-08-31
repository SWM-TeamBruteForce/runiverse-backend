package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "running_splits",
        // 기록당 구간 번호 중복 방지. 구간 조회가 이 순서로 훑으므로 진입 인덱스도 겸한다
        uniqueConstraints = @UniqueConstraint(name = "uk_running_split_record_number",
                columnNames = {"running_record_id", "split_number"})
)
@Check(name = "ck_running_split_number", constraints = "split_number >= 1")
// 구간 페이스는 기록 전체 평균과 범위가 다르다 — 짧은 구간에서는 신호 대기·GPS 튐이
// 사람이 낼 수 있는 속도 범위를 쉽게 벗어난다(SplitPace VO)
@Check(name = "ck_running_split_avg_pace", constraints = "avg_pace between 1 and 86400000")
@Check(name = "ck_running_split_distance", constraints = "distance between 1 and 500000")
@Check(name = "ck_running_split_duration", constraints = "duration between 1 and 86400")
@Check(name = "ck_running_split_calories", constraints = "calories between 0 and 20000")
@Check(name = "ck_running_split_avg_cadence",
        constraints = "avg_cadence is null or avg_cadence between 1 and 300")
@Check(name = "ck_running_split_elevation_change",
        constraints = "elevation_change is null or elevation_change between -10000 and 10000")
@Check(name = "ck_running_split_route_range",
        constraints = "route_start_index >= 0 and route_end_index >= route_start_index")
@Check(name = "ck_running_split_period", constraints = "end_at > start_at")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningSplitJpaEntity extends BaseCreatedAtEntity {

    @Id
    // IDENTITY는 Hibernate가 INSERT 배칭을 끈다 — batch_size 설정과 100건 flush 청크가 전부 무효였다.
    // 채번을 시퀀스에서 미리 받아 와야 배치가 산다. allocationSize는 batch_size와 정합이라 시퀀스 왕복도 100건당 1회다
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "running_split_seq")
    @SequenceGenerator(name = "running_split_seq", sequenceName = "running_split_seq", allocationSize = 100)
    @Column(name = "running_split_id", nullable = false, updatable = false)
    private Long runningSplitId;
    // 기록과 같은 애그리거트라 객체로 참조한다. 기록이 지워지면 구간도 함께 사라진다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_record_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_running_split_record"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningRecordJpaEntity record;
    @Column(name = "split_number", nullable = false, updatable = false)
    private int splitNumber;
    @Column(name = "avg_pace", nullable = false, updatable = false)
    private int avgPace;
    // 고정 구간 거리. 목표에서 끊으므로 마지막 구간도 같은 값이다
    @Column(name = "distance", nullable = false, updatable = false)
    private int distance;
    @Column(name = "duration", nullable = false, updatable = false)
    private int duration;
    @Column(name = "avg_cadence", updatable = false)
    private Integer avgCadence;
    // 순고도차(끝 − 시작)라 음수가 될 수 있다. 짧은 구간에서는 대체로 null이다
    @Column(name = "elevation_change", updatable = false)
    private Integer elevationChange;
    @Column(name = "calories", nullable = false, updatable = false)
    private int calories;
    // route_polyline 배열에서의 위치. 구간 N의 끝점은 N+1의 시작점과 같아 하나 겹친다
    @Column(name = "route_start_index", nullable = false, updatable = false)
    private int routeStartIndex;
    @Column(name = "route_end_index", nullable = false, updatable = false)
    private int routeEndIndex;
    @Column(name = "start_at", nullable = false, updatable = false)
    private LocalDateTime startAt;
    @Column(name = "end_at", nullable = false, updatable = false)
    private LocalDateTime endAt;

    private RunningSplitJpaEntity(RunningRecordJpaEntity record, int splitNumber, int avgPace,
                                  int distance, int duration, Integer avgCadence,
                                  Integer elevationChange, int calories, int routeStartIndex,
                                  int routeEndIndex, LocalDateTime startAt, LocalDateTime endAt) {
        this.record = record;
        this.splitNumber = splitNumber;
        this.avgPace = avgPace;
        this.distance = distance;
        this.duration = duration;
        this.avgCadence = avgCadence;
        this.elevationChange = elevationChange;
        this.calories = calories;
        this.routeStartIndex = routeStartIndex;
        this.routeEndIndex = routeEndIndex;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public static RunningSplitJpaEntity create(RunningRecordJpaEntity record, int splitNumber,
                                               int avgPace, int distance, int duration,
                                               Integer avgCadence, Integer elevationChange,
                                               int calories, int routeStartIndex,
                                               int routeEndIndex, LocalDateTime startAt,
                                               LocalDateTime endAt) {
        return new RunningSplitJpaEntity(record, splitNumber, avgPace, distance, duration,
                avgCadence, elevationChange, calories, routeStartIndex, routeEndIndex,
                startAt, endAt);
    }
}
