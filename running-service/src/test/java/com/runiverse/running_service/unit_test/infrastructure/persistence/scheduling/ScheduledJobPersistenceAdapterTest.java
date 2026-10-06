package com.runiverse.running_service.unit_test.infrastructure.persistence.scheduling;

import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import com.runiverse.running_service.infrastructure.persistence.scheduling.ScheduledJobJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.scheduling.ScheduledJobPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 어댑터의 분기·변환만 검증한다 —
// JPQL 자체는 실행되지 않는다(UserPersistenceAdapterTest와 같은 한계)
@ExtendWith(MockitoExtension.class)
@DisplayName("예약 작업 저장 어댑터 단위 테스트")
class ScheduledJobPersistenceAdapterTest {

    private static final long JOB_ID = 3L;
    private static final long ROOM_ID = 125L;
    private static final LocalDateTime EXECUTE_AT = LocalDateTime.of(2026, 9, 6, 19, 50);

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<ScheduledJobJpaEntity> query;

    @InjectMocks
    private ScheduledJobPersistenceAdapter scheduledJobPersistenceAdapter;

    @Test
    @DisplayName("같은 대상의 예약이 없으면 새로 저장한다")
    void savesNewJob() {
        // given
        givenQueryResult(Stream.empty());

        // when
        ScheduledJob saved = scheduledJobPersistenceAdapter.save(newJob());

        // then -> 타이머를 걸려면 ID가 필요하다. 커밋까지 기다리지 않고 flush로 채운다
        verify(entityManager).persist(any(ScheduledJobJpaEntity.class));
        verify(entityManager).flush();
        assertThat(saved.getTarget())
                .isEqualTo(new JobTarget(ScheduledJobType.MATCH_CLOSE, "125"));
        assertThat(saved.getExecuteAt()).isEqualTo(EXECUTE_AT);
    }

    @Test
    @DisplayName("같은 대상의 예약이 이미 있으면 그것을 그대로 쓴다")
    void reusesExistingJob() {
        // given -> 바로 INSERT를 치면 UNIQUE 위반이 방 생성 트랜잭션까지 끌고 죽는다
        givenQueryResult(Stream.of(entity(true, EXECUTE_AT)));

        // when
        ScheduledJob saved = scheduledJobPersistenceAdapter.save(newJob());

        // then
        verify(entityManager, never()).persist(any());
        assertThat(saved.getScheduledJobId()).contains(new ScheduledJobId(JOB_ID));
        assertThat(saved.isSent()).isTrue();
    }

    @Test
    @DisplayName("이미 저장된 예약은 저장 포트로 다시 넣지 않는다")
    void rejectsSavingPersistedJob() {
        // given -> 발화 처리는 잠금 + update 경로로만 간다
        // when & then
        assertThatThrownBy(() -> scheduledJobPersistenceAdapter.save(storedJob()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("실행 직전에는 쓰기 잠금으로 읽는다")
    void locksJobForUpdate() {
        // given -> 잠그지 않으면 두 인스턴스가 함께 sent=false를 보고 지나간다
        given(entityManager.find(ScheduledJobJpaEntity.class, JOB_ID,
                LockModeType.PESSIMISTIC_WRITE))
                .willReturn(entity(false, null));

        // when
        Optional<ScheduledJob> locked =
                scheduledJobPersistenceAdapter.lockById(new ScheduledJobId(JOB_ID));

        // then
        assertThat(locked).isPresent();
        assertThat(locked.get().isSent()).isFalse();
        assertThat(locked.get().getScheduledJobId()).contains(new ScheduledJobId(JOB_ID));
    }

    @Test
    @DisplayName("예약이 사라졌으면 비어 있다")
    void returnsEmptyWhenJobIsGone() {
        // given
        given(entityManager.find(ScheduledJobJpaEntity.class, JOB_ID,
                LockModeType.PESSIMISTIC_WRITE))
                .willReturn(null);

        // when & then
        assertThat(scheduledJobPersistenceAdapter.lockById(new ScheduledJobId(JOB_ID))).isEmpty();
    }

    @Test
    @DisplayName("발화 결과를 행에 되돌려 쓴다")
    void writesBackSentState() {
        // given
        ScheduledJobJpaEntity stored = entity(false, null);
        given(entityManager.find(ScheduledJobJpaEntity.class, JOB_ID)).willReturn(stored);
        ScheduledJob job = storedJob();
        job.markSent(EXECUTE_AT.plusSeconds(2));

        // when
        scheduledJobPersistenceAdapter.update(job);

        // then -> execute_at과의 차이가 곧 지연이라 sent_at도 함께 남긴다
        assertThat(stored.isSent()).isTrue();
        assertThat(stored.getSentAt()).isEqualTo(EXECUTE_AT.plusSeconds(2));
    }

    @Test
    @DisplayName("저장되지 않은 예약은 갱신할 수 없다")
    void rejectsUpdatingNewJob() {
        // when & then
        assertThatThrownBy(() -> scheduledJobPersistenceAdapter.update(newJob()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("부팅 복구는 아직 실행되지 않은 예약을 읽는다")
    void loadsPendingJobs() {
        // given -> 시각으로 거르지 않는다. 지난 것은 즉시 실행하고 남은 것은 타이머로 다시 건다
        given(entityManager.createQuery(anyString(), eq(ScheduledJobJpaEntity.class)))
                .willReturn(query);
        given(query.getResultList()).willReturn(List.of(entity(false, null)));

        // when
        List<ScheduledJob> pending = scheduledJobPersistenceAdapter.loadPending();

        // then
        assertThat(pending).singleElement()
                .satisfies(job -> {
                    assertThat(job.isSent()).isFalse();
                    assertThat(job.getExecuteAt()).isEqualTo(EXECUTE_AT);
                    assertThat(job.getTarget().idAsLong()).isEqualTo(ROOM_ID);
                });
    }

    private void givenQueryResult(Stream<ScheduledJobJpaEntity> result) {
        given(entityManager.createQuery(anyString(), eq(ScheduledJobJpaEntity.class)))
                .willReturn(query);
        given(query.setParameter(anyString(), any())).willReturn(query);
        given(query.getResultStream()).willReturn(result);
    }

    private static ScheduledJob newJob() {
        return ScheduledJob.reserve(ScheduledJobType.MATCH_CLOSE, ROOM_ID, EXECUTE_AT);
    }

    private static ScheduledJob storedJob() {
        return ScheduledJob.builder()
                .scheduledJobId(JOB_ID)
                .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID))
                .executeAt(EXECUTE_AT)
                .build();
    }

    // ID는 @GeneratedValue라 세터가 없다 — DB에서 읽어온 행을 흉내 내려면 심어야 한다
    private static ScheduledJobJpaEntity entity(boolean sent, LocalDateTime sentAt) {
        ScheduledJobJpaEntity entity = ScheduledJobJpaEntity.create(
                ScheduledJobType.MATCH_CLOSE, String.valueOf(ROOM_ID), EXECUTE_AT);
        ReflectionTestUtils.setField(entity, "scheduledJobId", JOB_ID);
        entity.changeSent(sent, sentAt);
        return entity;
    }
}
