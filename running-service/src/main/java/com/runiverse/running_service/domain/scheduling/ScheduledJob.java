package com.runiverse.running_service.domain.scheduling;

import com.runiverse.running_service.domain.scheduling.exception.ExecuteAtRequiredException;
import com.runiverse.running_service.domain.scheduling.exception.InvalidJobTargetException;
import com.runiverse.running_service.domain.scheduling.exception.InvalidSentAtException;
import com.runiverse.running_service.domain.scheduling.exception.ScheduledJobAlreadySentException;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Optional;

// 정해진 시각에 한 번만 실행되는 예약. 무엇을 언제 할지만 알고 무엇을 하는지는 모른다 —
// 그건 타입별 유스케이스의 몫이다.
// 여러 인스턴스가 같은 예약을 메모리 타이머로 들고 있으므로, 실행 직전에 잠그고
// markSent()로 하나만 통과시킨다. 중복은 낭비가 아니라 이중화다
@Getter
public class ScheduledJob {

    private final Long scheduledJobId;
    private final JobTarget target;
    private final LocalDateTime executeAt;
    private boolean sent;
    private LocalDateTime sentAt;

    @Builder
    private ScheduledJob(Long scheduledJobId, JobTarget target, LocalDateTime executeAt,
                         boolean sent, LocalDateTime sentAt) {
        if (target == null) {
            throw new InvalidJobTargetException();
        }
        if (executeAt == null) {
            throw new ExecuteAtRequiredException();
        }
        validateSentAt(sent, sentAt);
        this.scheduledJobId = scheduledJobId;
        this.target = target;
        this.executeAt = executeAt;
        this.sent = sent;
        this.sentAt = sentAt;
    }

    public static ScheduledJob reserve(ScheduledJobType type, Long targetId,
                                       LocalDateTime executeAt) {
        return ScheduledJob.builder()
                .target(JobTarget.of(type, targetId))
                .executeAt(executeAt)
                .build();
    }

    // 실행 직전에 선점한다 — 잠그고 읽은 뒤에 부르지 않으면 두 인스턴스가 함께 통과한다.
    // 이미 발화한 예약이면 던져서 호출자가 빠지게 한다
    public void markSent(LocalDateTime sentAt) {
        if (sent) {
            throw new ScheduledJobAlreadySentException();
        }
        validateSentAt(true, sentAt);
        this.sent = true;
        this.sentAt = sentAt;
    }

    // 부팅 복구가 쓴다 — 이미 지난 예약은 타이머를 걸지 않고 즉시 실행한다
    public boolean isDue(LocalDateTime now) {
        return !now.isBefore(executeAt);
    }

    public boolean isNew() {
        return scheduledJobId == null;
    }

    public Optional<ScheduledJobId> getScheduledJobId() {
        return Optional.ofNullable(scheduledJobId).map(ScheduledJobId::new);
    }

    public Optional<LocalDateTime> getSentAt() {
        return Optional.ofNullable(sentAt);
    }

    // 발화 여부와 발화 시각은 짝이다 — 한쪽만 있는 행은 복원 단계에서 걸러낸다
    private static void validateSentAt(boolean sent, LocalDateTime sentAt) {
        if (sent != (sentAt != null)) {
            throw new InvalidSentAtException();
        }
    }
}
