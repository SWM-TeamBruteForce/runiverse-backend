package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.location.UpdateRunningFinishJudge;
import com.runiverse.running_service.application.running.common.RunningFinisher;
import com.runiverse.running_service.application.running.exception.RunningNotStartableException;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("목표 도달 종료 판정 단위 테스트")
public class UpdateRunningFinishJudgeTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 125L;
    private static final int TARGET_DISTANCE_METERS = 5_000;

    // 종료 확정은 DB·S3·Redis를 한꺼번에 건드린다 — 여기서는 부르는지와 무엇을 넘기는지만 본다
    @Mock
    private RunningFinisher runningFinisher;

    @InjectMocks
    private UpdateRunningFinishJudge judge;

    private boolean judge(Integer targetDistanceMeters, double meters) {
        return judge.judge(ROOM_ID, new UserId(USER_ID), targetDistanceMeters, meters);
    }

    @Test
    @DisplayName("누적이 목표를 넘으면 러닝을 끝낸다")
    void finishesWhenDistanceExceedsTarget() {
        // when -> 5km 방에서 마지막 배치가 5,020m까지 밀었다
        boolean finished = judge(TARGET_DISTANCE_METERS, 5_020.0);

        // then
        assertThat(finished).isTrue();
        verify(runningFinisher).finish(ROOM_ID, USER_ID);
    }

    @Test
    @DisplayName("누적이 목표와 정확히 같아도 러닝을 끝낸다")
    void finishesWhenDistanceEqualsTarget() {
        // when -> 확정도 목표 이상이면 완주다 — 판정 경계를 확정과 맞춘다
        boolean finished = judge(TARGET_DISTANCE_METERS, 5_000.0);

        // then
        assertThat(finished).isTrue();
        verify(runningFinisher).finish(ROOM_ID, USER_ID);
    }

    @Test
    @DisplayName("목표에 못 미치면 끝내지 않는다")
    void doesNotFinishBelowTarget() {
        // when -> 1m 모자란다
        boolean finished = judge(TARGET_DISTANCE_METERS, 4_999.9);

        // then
        assertThat(finished).isFalse();
        verifyNoInteractions(runningFinisher);
    }

    @Test
    @DisplayName("목표 없는 솔로 방은 얼마를 뛰어도 끝내지 않는다")
    void doesNotFinishRoomWithoutGoal() {
        // when -> 솔로는 사용자가 RUNNING_FINISH를 보내야 끝난다
        boolean finished = judge(null, 42_195.0);

        // then
        assertThat(finished).isFalse();
        verifyNoInteractions(runningFinisher);
    }

    @Test
    @DisplayName("이미 목표를 넘은 뒤의 배치도 다시 끝낸다")
    void finishesAgainWhileAboveTarget() {
        // when -> 종료 직후 늦게 도착한 배치다. 넘은 순간만 부르면 첫 종료가 실패했을 때 되살릴 길이 없다
        judge(TARGET_DISTANCE_METERS, 5_020.0);
        boolean finished = judge(TARGET_DISTANCE_METERS, 5_031.0);

        // then -> 두 번째 호출은 종료의 멱등 경로가 받는다
        assertThat(finished).isTrue();
        verify(runningFinisher, times(2)).finish(ROOM_ID, USER_ID);
    }

    @Test
    @DisplayName("종료가 실패하면 삼키지 않고 그대로 던진다")
    void propagatesFinishFailure() {
        // given
        willThrow(new RunningNotStartableException())
                .given(runningFinisher).finish(anyLong(), any());

        // when & then -> 삼키면 클라가 ERROR를 못 받고, 끝난 줄 모른 채 계속 뛴다
        assertThatThrownBy(() -> judge(TARGET_DISTANCE_METERS, 5_020.0))
                .isInstanceOf(RunningNotStartableException.class);
    }
}
