package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
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
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "running_combos",
        // 쌍마다 구간 번호 중복 방지 — 방 안에서 first_user_id로 찾는 조회도 이 순서로 탄다
        uniqueConstraints = @UniqueConstraint(name = "uk_running_combo_room_pair_number",
                columnNames = {"running_room_id", "first_user_id", "second_user_id", "segment_number"}),
        // 참가자는 쌍의 어느 쪽에든 있을 수 있다 — UNIQUE가 덮지 못하는 second_user_id 쪽을 맡는다
        indexes = @Index(name = "idx_running_combo_room_second_user",
                columnList = "running_room_id, second_user_id, segment_number")
)
// 정렬과 함께 같은 사람끼리의 쌍도 막는다. 앱과 DB의 UUID 정렬은 UUIDv7에서만 같다
@Check(name = "ck_running_combo_users", constraints = "first_user_id < second_user_id")
@Check(name = "ck_running_combo_segment_number", constraints = "segment_number >= 1")
@Check(name = "ck_running_combo_count", constraints = "combo_count >= 1")
@Check(name = "ck_running_combo_period", constraints = "end_at > start_at")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningComboJpaEntity extends BaseCreatedAtEntity {

    @Id
    // IDENTITY는 Hibernate가 INSERT 배칭을 끈다 — 구간과 같이 시퀀스에서 미리 받아 와 배치를 살린다
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "running_combo_seq")
    @SequenceGenerator(name = "running_combo_seq", sequenceName = "running_combo_seq", allocationSize = 100)
    @Column(name = "running_combo_id", nullable = false, updatable = false)
    private Long runningComboId;
    // 방이 지워지면 콤보도 함께 사라진다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_room_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_running_combo_room"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningRoomJpaEntity room;
    // 논리 참조(FK 제약 없음) — 탈퇴해도 콤보는 남는다
    @Column(name = "first_user_id", nullable = false, updatable = false)
    private UUID firstUserId;
    @Column(name = "second_user_id", nullable = false, updatable = false)
    private UUID secondUserId;
    @Column(name = "segment_number", nullable = false, updatable = false)
    private int segmentNumber;
    @Column(name = "combo_count", nullable = false, updatable = false)
    private int comboCount;
    // i번째 점이 i콤보를 달성한 자리 — 그 참가자의 route_polyline에서 고른 점이다. 기록이 없으면 null
    @Column(name = "first_route_polyline", updatable = false, columnDefinition = "text")
    private String firstRoutePolyline;
    @Column(name = "second_route_polyline", updatable = false, columnDefinition = "text")
    private String secondRoutePolyline;
    @Column(name = "start_at", nullable = false, updatable = false)
    private LocalDateTime startAt;
    @Column(name = "end_at", nullable = false, updatable = false)
    private LocalDateTime endAt;

    private RunningComboJpaEntity(RunningRoomJpaEntity room, UUID firstUserId, UUID secondUserId,
                                  int segmentNumber, int comboCount, String firstRoutePolyline,
                                  String secondRoutePolyline, LocalDateTime startAt, LocalDateTime endAt) {
        this.room = room;
        this.firstUserId = firstUserId;
        this.secondUserId = secondUserId;
        this.segmentNumber = segmentNumber;
        this.comboCount = comboCount;
        this.firstRoutePolyline = firstRoutePolyline;
        this.secondRoutePolyline = secondRoutePolyline;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public static RunningComboJpaEntity create(RunningRoomJpaEntity room, UUID firstUserId,
                                               UUID secondUserId, int segmentNumber, int comboCount,
                                               String firstRoutePolyline, String secondRoutePolyline,
                                               LocalDateTime startAt, LocalDateTime endAt) {
        return new RunningComboJpaEntity(room, firstUserId, secondUserId, segmentNumber, comboCount,
                firstRoutePolyline, secondRoutePolyline, startAt, endAt);
    }
}
