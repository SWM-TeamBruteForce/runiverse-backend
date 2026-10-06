package com.runiverse.running_service.unit_test.running.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.combo.RunningComboProperties;
import com.runiverse.running_service.application.running.command.combo.RunningComboReader;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 콤보 조회기 단위 테스트")
class RunningComboReaderTest {

    private static final Long ROOM_ID = 42L;
    private static final UserId RECIPIENT = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private LoadRunningComboSnapshotsPort loadRunningComboSnapshotsPort;

    @Mock
    private LoadRunningComboPairsPort loadRunningComboPairsPort;

    private RunningComboReader reader;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        reader = new RunningComboReader(loadRunningComboSnapshotsPort, loadRunningComboPairsPort,
                new RunningComboProperties(30, 30, Duration.ofSeconds(19), Duration.ofSeconds(10), 1));
        log = LogCapture.of(RunningComboReader.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("조회가 실패하면 빈 목록을 주고 userId 값을 담아 ERROR로 남긴다")
    void logsFailureAndReturnsEmpty() {
        // given
        willThrow(new IllegalStateException("redis down"))
                .given(loadRunningComboSnapshotsPort).loadSnapshots(ROOM_ID);

        // when -> 콤보를 못 읽었다고 RUNNING_START ack가 실패하면 러닝을 시작하지 못한다
        assertThat(reader.read(ROOM_ID, RECIPIENT)).isEmpty();

        // then
        assertThat(log.messages(Level.ERROR)).containsExactly(
                "[러닝] 콤보 조회 실패: 처리하지 못한 예외 - roomId=" + ROOM_ID + ", userId=" + RECIPIENT.value());
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }
}
