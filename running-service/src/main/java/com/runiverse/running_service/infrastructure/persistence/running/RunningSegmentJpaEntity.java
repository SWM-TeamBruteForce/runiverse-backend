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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Entity
@Table(
        name = "running_segments",
        // 기록당 조각 번호 중복 방지. 기록의 조각을 번호순으로 읽는 조회도 이 인덱스를 탄다
        uniqueConstraints = @UniqueConstraint(name = "uk_running_segment_record_number",
                columnNames = {"running_record_id", "segment_number"})
)
@Check(name = "ck_running_segment_number", constraints = "segment_number >= 1")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningSegmentJpaEntity extends BaseCreatedAtEntity {

    @Id
    // 기록당 몇 개뿐이라 배칭이 필요 없다 — IDENTITY로 바로 ID를 받아 구간이 참조한다
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "running_segment_id", nullable = false, updatable = false)
    private Long runningSegmentId;
    // 기록과 같은 애그리거트라 객체로 참조한다. 기록이 지워지면 조각도 함께 사라진다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_record_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_running_segment_record"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RunningRecordJpaEntity record;
    @Column(name = "segment_number", nullable = false, updatable = false)
    private int segmentNumber;
    // 이 조각의 10m 경계점. 점 번호는 조각마다 0부터이고, 구간의 경로 범위가 이 번호를 가리킨다
    @Column(name = "route_polyline", nullable = false, updatable = false, columnDefinition = "text")
    private String routePolyline;

    private RunningSegmentJpaEntity(RunningRecordJpaEntity record, int segmentNumber,
                                    String routePolyline) {
        this.record = record;
        this.segmentNumber = segmentNumber;
        this.routePolyline = routePolyline;
    }

    public static RunningSegmentJpaEntity create(RunningRecordJpaEntity record, int segmentNumber,
                                                 String routePolyline) {
        return new RunningSegmentJpaEntity(record, segmentNumber, routePolyline);
    }
}
