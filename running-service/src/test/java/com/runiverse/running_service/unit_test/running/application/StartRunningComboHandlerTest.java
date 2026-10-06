package com.runiverse.running_service.unit_test.running.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.combo.StartRunningComboCommand;
import com.runiverse.running_service.application.running.command.combo.StartRunningComboHandler;
import com.runiverse.running_service.application.running.command.combo.UpdateRunningComboJudge;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 콤보 시작 판정 단위 테스트")
class StartRunningComboHandlerTest {

    private static final Long ROOM_ID = 42L;
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private LoadRunningComboSnapshotsPort loadRunningComboSnapshotsPort;

    @Mock
    private LoadRunningDistancePort loadRunningDistancePort;

    @Mock
    private UpdateRunningComboJudge updateRunningComboJudge;

    private StartRunningComboHandler handler;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        handler = new StartRunningComboHandler(
                loadRunningComboSnapshotsPort, loadRunningDistancePort, updateRunningComboJudge);
        log = LogCapture.of(StartRunningComboHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("시작 판정이 실패해도 러닝 시작을 막지 않고 userId 값을 담아 ERROR로 남긴다")
    void logsFailureWithoutBlockingStart() {
        // given
        willThrow(new IllegalStateException("redis down"))
                .given(loadRunningComboSnapshotsPort).loadSnapshots(ROOM_ID);

        // when -> 콤보는 곁가지라 첫 좌표 배치가 도착하면 원래 경로로 붙는다
        assertThatCode(() -> handler.handle(new StartRunningComboCommand(ROOM_ID, USER_ID)))
                .doesNotThrowAnyException();

        // then -> UserId[value=...]가 아니라 값만 찍혀야 userId로 검색된다
        assertThat(log.messages(Level.ERROR)).containsExactly(
                "[러닝] 콤보 시작 판정 실패: 처리하지 못한 예외 - roomId=" + ROOM_ID + ", userId=" + USER_ID);
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }
}
