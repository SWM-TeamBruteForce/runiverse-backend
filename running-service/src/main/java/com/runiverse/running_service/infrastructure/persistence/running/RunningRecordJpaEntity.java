package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "running_records",
        // 유저당 방별 1기록 — 멱등한 종료 처리가 이 제약에 기댄다
        uniqueConstraints = @UniqueConstraint(name = "uk_running_record_room_user",
                columnNames = {"running_room_id", "user_id"}),
        // 내 기록 목록(7-1)·피드 카드가 유저 기준 최신순으로 훑는다
        indexes = @Index(name = "idx_running_record_user", columnList = "user_id, start_at")
)
@Check(name = "ck_running_record_avg_pace", constraints = "avg_pace between 120 and 3600")
@Check(name = "ck_running_record_total_distance",
        constraints = "total_distance between 1 and 500000")
@Check(name = "ck_running_record_total_duration",
        constraints = "total_duration between 1 and 86400")
@Check(name = "ck_running_record_total_calories",
        constraints = "total_calories between 0 and 20000")
@Check(name = "ck_running_record_avg_cadence",
        constraints = "avg_cadence is null or avg_cadence between 1 and 300")
@Check(name = "ck_running_record_elevation_gain",
        constraints = "total_elevation_gain is null or total_elevation_gain between 0 and 20000")
@Check(name = "ck_running_record_weather_code", constraints = "weather_code between 0 and 99")
@Check(name = "ck_running_record_period", constraints = "end_at > start_at")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningRecordJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "running_record_id", nullable = false, updatable = false)
    private Long runningRecordId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_room_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_running_record_room"))
    private RunningRoomJpaEntity room;
    // 논리 참조(FK 제약 없음) — 탈퇴해도 기록은 남는다
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
    @Column(name = "avg_pace", nullable = false, updatable = false)
    private int avgPace;
    // 목표를 넘겨 뛰어도 목표 지점에서 끊은 값이다 — 항상 구간 거리의 배수다
    @Column(name = "total_distance", nullable = false, updatable = false)
    private int totalDistance;
    // 구간 duration의 합. 일시정지 시간은 빠져 end_at - start_at보다 작을 수 있다
    @Column(name = "total_duration", nullable = false, updatable = false)
    private int totalDuration;
    // 보수 센서가 있어야 온다
    @Column(name = "avg_cadence", updatable = false)
    private Integer avgCadence;
    // 올라간 것만 합산 — 구간의 순고도차 합과는 다른 값이다
    @Column(name = "total_elevation_gain", updatable = false)
    private Integer totalElevationGain;
    @Column(name = "total_calories", nullable = false, updatable = false)
    private int totalCalories;
    // S3 원본 트랙 키. 재계산·분석용이라 API 응답에는 쓰지 않는다
    @Column(name = "gps_track_key", nullable = false, updatable = false)
    private String gpsTrackKey;
    // API가 내려주는 유일한 경로 데이터. 구간 인덱스가 이 배열의 위치를 가리킨다
    @Column(name = "route_polyline", nullable = false, updatable = false, columnDefinition = "text")
    private String routePolyline;
    // WMO 4677 코드 — 악조건 여부는 저장하지 않고 판정 시 계산한다
    @Column(name = "weather_code", nullable = false, updatable = false)
    private int weatherCode;
    @Column(name = "temperature", nullable = false, updatable = false, precision = 3, scale = 1)
    private BigDecimal temperature;
    @Column(name = "start_at", nullable = false, updatable = false)
    private LocalDateTime startAt;
    @Column(name = "end_at", nullable = false, updatable = false)
    private LocalDateTime endAt;

    private RunningRecordJpaEntity(RunningRoomJpaEntity room, UUID userId, int avgPace,
                                   int totalDistance, int totalDuration, Integer avgCadence,
                                   Integer totalElevationGain, int totalCalories,
                                   String gpsTrackKey, String routePolyline, int weatherCode,
                                   BigDecimal temperature, LocalDateTime startAt,
                                   LocalDateTime endAt) {
        this.room = room;
        this.userId = userId;
        this.avgPace = avgPace;
        this.totalDistance = totalDistance;
        this.totalDuration = totalDuration;
        this.avgCadence = avgCadence;
        this.totalElevationGain = totalElevationGain;
        this.totalCalories = totalCalories;
        this.gpsTrackKey = gpsTrackKey;
        this.routePolyline = routePolyline;
        this.weatherCode = weatherCode;
        this.temperature = temperature;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public static RunningRecordJpaEntity create(RunningRoomJpaEntity room, UUID userId,
                                                int avgPace, int totalDistance, int totalDuration,
                                                Integer avgCadence, Integer totalElevationGain,
                                                int totalCalories, String gpsTrackKey,
                                                String routePolyline, int weatherCode,
                                                BigDecimal temperature, LocalDateTime startAt,
                                                LocalDateTime endAt) {
        return new RunningRecordJpaEntity(room, userId, avgPace, totalDistance, totalDuration,
                avgCadence, totalElevationGain, totalCalories, gpsTrackKey, routePolyline,
                weatherCode, temperature, startAt, endAt);
    }
}
