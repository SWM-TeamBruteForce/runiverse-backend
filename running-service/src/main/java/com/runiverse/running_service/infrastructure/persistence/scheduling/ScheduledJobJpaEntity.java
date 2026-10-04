package com.runiverse.running_service.infrastructure.persistence.scheduling;

import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

// 도메인 테이블이 아니라 기전 테이블이다 — 무엇을 언제 실행할지만 담고 결과는 대상 테이블이 갖는다.
// 실행 후에도 지우지 않는다: 이력이 곧 "그 시각에 실제로 돌았는가"의 근거다
@Getter
@Entity
@Table(
        name = "scheduled_jobs",
        // 한 대상에 같은 종류의 예약은 하나 — 인스턴스 여럿이 넣으려 해도 DB가 막는다
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scheduled_job", columnNames = {"job_type", "target_id"}),
        // 부팅 복구는 미실행분만 읽는다. is_sent가 선두라 끝난 대다수를 인덱스에서 배제한다
        indexes = @Index(name = "idx_scheduled_job_pending", columnList = "is_sent, execute_at")
)
@Check(name = "ck_scheduled_job_type",
        constraints = "job_type in ('MATCH_CLOSE', 'RUNNING_READY', 'RUNNING_START', "
                + "'RUNNING_FORCE_FINISH')")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledJobJpaEntity extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scheduled_job_id")
    private Long scheduledJobId;
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", length = 50, nullable = false, updatable = false)
    private ScheduledJobType jobType;
    // 타입마다 가리키는 테이블이 달라 FK를 걸지 않는다
    @Column(name = "target_id", length = 100, nullable = false, updatable = false)
    private String targetId;
    // 오프셋을 바꾸면 아직 실행되지 않은 행의 이 값을 함께 옮겨야 한다 — 계산값을 굳힌 대가다
    @Column(name = "execute_at", nullable = false)
    private LocalDateTime executeAt;
    @Column(name = "is_sent", nullable = false)
    private boolean sent;
    // execute_at과의 차이가 곧 지연이라 운영 지표로 쓴다
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    private ScheduledJobJpaEntity(ScheduledJobType jobType, String targetId,
                                  LocalDateTime executeAt) {
        this.jobType = jobType;
        this.targetId = targetId;
        this.executeAt = executeAt;
    }

    public static ScheduledJobJpaEntity create(ScheduledJobType jobType, String targetId,
                                               LocalDateTime executeAt) {
        return new ScheduledJobJpaEntity(jobType, targetId, executeAt);
    }

    // 선점 결과를 되받는다 — 도메인이 짝을 검증했으므로 여기서는 다시 보지 않는다
    public void changeSent(boolean sent, LocalDateTime sentAt) {
        this.sent = sent;
        this.sentAt = sentAt;
    }

}
