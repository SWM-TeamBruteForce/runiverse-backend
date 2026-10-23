package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.finish.FinishRunningHandler;
import com.runiverse.running_service.application.running.common.GoalCheck;
import com.runiverse.running_service.application.running.common.RunningFinisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// 확정 규칙 자체는 RunningFinisherTest가 본다 — 여기서는 RUNNING_FINISH 입구가 그대로 넘기는지만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 종료 요청 단위 테스트")
public class FinishRunningHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 125L;

    @Mock
    private RunningFinisher runningFinisher;

    @InjectMocks
    private FinishRunningHandler handler;

    @ParameterizedTest(name = "forced={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("조기 종료 의사를 그대로 넘기고 확정 결과를 돌려준다")
    void delegatesWithForced(boolean forced) {
        // given
        given(runningFinisher.finish(ROOM_ID, USER_ID, forced)).willReturn(GoalCheck.reached());

        // when
        GoalCheck result = handler.handle(new FinishRunningCommand(ROOM_ID, USER_ID, forced));

        // then -> forced=false면 확정 거리가 모자랄 때 미뤄야 해서 그대로 넘긴다
        assertThat(result.finished()).isTrue();
        verify(runningFinisher).finish(ROOM_ID, USER_ID, forced);
    }

    @Test
    @DisplayName("다 뛰었다고 본 종료가 미뤄지면 남은 거리를 그대로 돌려준다")
    void returnsRemainingWhenDeferred() {
        // given
        given(runningFinisher.finish(ROOM_ID, USER_ID, false)).willReturn(GoalCheck.pending(40));

        // when
        GoalCheck result = handler.handle(new FinishRunningCommand(ROOM_ID, USER_ID, false));

        // then -> presentation이 ack 대신 RUNNING_GOAL_PENDING을 보낸다
        assertThat(result.finished()).isFalse();
        assertThat(result.remainingMeters()).isEqualTo(40);
    }
}
