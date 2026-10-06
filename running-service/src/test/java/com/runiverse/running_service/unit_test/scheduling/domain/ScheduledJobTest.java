package com.runiverse.running_service.unit_test.scheduling.domain;

import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.exception.ExecuteAtRequiredException;
import com.runiverse.running_service.domain.scheduling.exception.InvalidJobTargetException;
import com.runiverse.running_service.domain.scheduling.exception.InvalidScheduledJobIdException;
import com.runiverse.running_service.domain.scheduling.exception.InvalidSentAtException;
import com.runiverse.running_service.domain.scheduling.exception.ScheduledJobAlreadySentException;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("예약 작업 도메인 단위 테스트")
class ScheduledJobTest {

    private static final long ROOM_ID = 125L;
    private static final LocalDateTime EXECUTE_AT = LocalDateTime.of(2026, 9, 6, 19, 50);

    @Nested
    @DisplayName("예약 생성")
    class Reserve {

        @Test
        @DisplayName("대상과 실행 시각을 담아 아직 발화하지 않은 예약을 만든다")
        void createsPendingJob() {
            // when
            ScheduledJob job = ScheduledJob.reserve(
                    ScheduledJobType.MATCH_CLOSE, ROOM_ID, EXECUTE_AT);

            // then -> 저장 전이라 ID가 없다. 타이머는 저장 후에 건다
            assertThat(job.isNew()).isTrue();
            assertThat(job.isSent()).isFalse();
            assertThat(job.getSentAt()).isEmpty();
            assertThat(job.getTarget()).isEqualTo(new JobTarget(ScheduledJobType.MATCH_CLOSE, "125"));
            assertThat(job.getExecuteAt()).isEqualTo(EXECUTE_AT);
        }

        @Test
        @DisplayName("실행 시각이 없으면 예약할 수 없다")
        void rejectsMissingExecuteAt() {
            // given -> 시각이 없으면 언제 깨울지 알 수 없다
            // when & then
            assertThatThrownBy(() ->
                    ScheduledJob.reserve(ScheduledJobType.MATCH_CLOSE, ROOM_ID, null))
                    .isInstanceOf(ExecuteAtRequiredException.class);
        }

        @Test
        @DisplayName("대상이 없으면 예약할 수 없다")
        void rejectsMissingTarget() {
            // when & then
            assertThatThrownBy(() ->
                    ScheduledJob.reserve(ScheduledJobType.MATCH_CLOSE, null, EXECUTE_AT))
                    .isInstanceOf(InvalidJobTargetException.class);
        }
    }

    // 이미 발화한 채로 DB에서 복원된 예약
    private static ScheduledJob sentJob() {
        return ScheduledJob.builder()
                .scheduledJobId(1L)
                .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID))
                .executeAt(EXECUTE_AT)
                .sent(true)
                .sentAt(EXECUTE_AT)
                .build();
    }

    @Nested
    @DisplayName("발화 선점")
    class MarkSent {

        @Test
        @DisplayName("발화하면 시각과 함께 굳는다")
        void marksSentWithTime() {
            // given
            ScheduledJob job = ScheduledJob.reserve(
                    ScheduledJobType.MATCH_CLOSE, ROOM_ID, EXECUTE_AT);

            // when
            job.markSent(EXECUTE_AT.plusSeconds(1));

            // then -> execute_at과의 차이가 곧 지연이라 운영 지표로 쓴다
            assertThat(job.isSent()).isTrue();
            assertThat(job.getSentAt()).contains(EXECUTE_AT.plusSeconds(1));
        }

        @Test
        @DisplayName("이미 발화한 예약은 두 번 발화하지 않는다")
        void rejectsSecondSend() {
            // given -> 인스턴스 여럿이 같은 예약을 들고 있어 두 번 깰 수 있다.
            //          잠그고 읽은 뒤 여기서 하나만 통과한다
            ScheduledJob job = sentJob();

            // when & then
            assertThatThrownBy(() -> job.markSent(EXECUTE_AT.plusMinutes(1)))
                    .isInstanceOf(ScheduledJobAlreadySentException.class);
        }

        @Test
        @DisplayName("발화 시각 없이 발화 처리할 수 없다")
        void rejectsSendWithoutTime() {
            // given -> 발화 여부와 발화 시각은 짝이다
            ScheduledJob job = ScheduledJob.reserve(
                    ScheduledJobType.MATCH_CLOSE, ROOM_ID, EXECUTE_AT);

            // when & then
            assertThatThrownBy(() -> job.markSent(null))
                    .isInstanceOf(InvalidSentAtException.class);
            assertThat(job.isSent()).isFalse();
        }
    }

    @Nested
    @DisplayName("복원")
    class Restore {

        @Test
        @DisplayName("발화 여부와 발화 시각이 어긋난 행은 복원하지 않는다")
        void rejectsMismatchedSentState() {
            // given & when & then -> 발화했다면서 시각이 없는 행
            assertThatThrownBy(() -> ScheduledJob.builder()
                    .scheduledJobId(1L)
                    .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID))
                    .executeAt(EXECUTE_AT)
                    .sent(true)
                    .build())
                    .isInstanceOf(InvalidSentAtException.class);

            // 반대로 발화하지 않았다면서 시각이 있는 행
            assertThatThrownBy(() -> ScheduledJob.builder()
                    .scheduledJobId(1L)
                    .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID))
                    .executeAt(EXECUTE_AT)
                    .sentAt(EXECUTE_AT)
                    .build())
                    .isInstanceOf(InvalidSentAtException.class);
        }

        @Test
        @DisplayName("저장된 예약은 ID를 갖는다")
        void restoredJobHasId() {
            // when
            ScheduledJob job = sentJob();

            // then
            assertThat(job.isNew()).isFalse();
            assertThat(job.getScheduledJobId()).contains(new ScheduledJobId(1L));
        }
    }

    @Nested
    @DisplayName("실행 시점 판정")
    class IsDue {

        @Test
        @DisplayName("실행 시각을 지났으면 즉시 실행 대상이다")
        void dueAfterExecuteAt() {
            // given -> 부팅 복구가 이걸로 "즉시 실행"과 "타이머 재등록"을 가른다
            ScheduledJob job = ScheduledJob.reserve(
                    ScheduledJobType.MATCH_CLOSE, ROOM_ID, EXECUTE_AT);

            // when & then
            assertThat(job.isDue(EXECUTE_AT.plusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("정각도 실행 대상이다")
        void dueExactlyAtExecuteAt() {
            // given -> 마감 정각부터 확정 구간이다. 유예는 두지 않는다(feature-spec)
            ScheduledJob job = ScheduledJob.reserve(
                    ScheduledJobType.MATCH_CLOSE, ROOM_ID, EXECUTE_AT);

            // when & then
            assertThat(job.isDue(EXECUTE_AT)).isTrue();
        }

        @Test
        @DisplayName("아직 안 왔으면 실행 대상이 아니다")
        void notDueBeforeExecuteAt() {
            // given
            ScheduledJob job = ScheduledJob.reserve(
                    ScheduledJobType.MATCH_CLOSE, ROOM_ID, EXECUTE_AT);

            // when & then
            assertThat(job.isDue(EXECUTE_AT.minusSeconds(1))).isFalse();
        }
    }

    @Nested
    @DisplayName("대상 식별자")
    class Target {

        @Test
        @DisplayName("숫자 식별자를 되돌려 대상 애그리거트를 찾는다")
        void convertsIdBackToLong() {
            // when & then
            assertThat(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID).idAsLong())
                    .isEqualTo(ROOM_ID);
        }

        @Test
        @DisplayName("숫자가 아닌 식별자는 되돌릴 수 없다")
        void rejectsNonNumericId() {
            // given -> FK가 없어 문자열로 두는 대가다. 깨진 값은 여기서 걸린다
            JobTarget target = new JobTarget(ScheduledJobType.MATCH_CLOSE, "room-125");

            // when & then
            assertThatThrownBy(target::idAsLong).isInstanceOf(InvalidJobTargetException.class);
        }

        @Test
        @DisplayName("빈 식별자는 대상이 될 수 없다")
        void rejectsBlankId() {
            // when & then
            assertThatThrownBy(() -> new JobTarget(ScheduledJobType.MATCH_CLOSE, " "))
                    .isInstanceOf(InvalidJobTargetException.class);
        }

        @Test
        @DisplayName("예약 ID는 1 이상이다")
        void rejectsInvalidJobId() {
            // when & then
            assertThatThrownBy(() -> new ScheduledJobId(0L))
                    .isInstanceOf(InvalidScheduledJobIdException.class);
        }
    }
}
