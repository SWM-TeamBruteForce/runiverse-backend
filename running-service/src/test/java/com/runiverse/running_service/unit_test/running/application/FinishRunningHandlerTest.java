package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.finish.FinishRunningHandler;
import com.runiverse.running_service.application.running.common.RunningFinisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

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
    @DisplayName("조기 종료 의사와 상관없이 같은 확정으로 넘긴다")
    void delegatesRegardlessOfForced(boolean forced) {
        // when
        handler.handle(new FinishRunningCommand(ROOM_ID, USER_ID, forced));

        // then -> forced는 의사일 뿐 최종 상태는 확정 거리가 정한다
        verify(runningFinisher).finish(ROOM_ID, USER_ID);
    }
}
