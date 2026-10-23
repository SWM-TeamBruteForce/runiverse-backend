package com.runiverse.running_service.unit_test.running.application;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.application.running.common.RunningComboArchive;
import com.runiverse.running_service.application.running.common.RunningComboArchiver;
import com.runiverse.running_service.application.running.common.RunningComboWriter;
import com.runiverse.running_service.application.running.port.out.DeleteRunningComboStatePort;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 트랜잭션 동기화가 없는 단위 테스트라 커밋 뒤 작업이 그 자리에서 돈다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 콤보 정리 단위 테스트")
class RunningComboArchiverTest {

    private static final long ROOM_ID = 12L;

    @Mock
    private RunningComboWriter runningComboWriter;

    @Mock
    private DeleteRunningComboStatePort deleteRunningComboStatePort;

    private RunningComboArchiver archiver;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        archiver = new RunningComboArchiver(runningComboWriter, deleteRunningComboStatePort);
        log = LogCapture.of(RunningComboArchiver.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("저장에 성공하면 결과를 INFO로 남기고 그다음 상태를 지운다")
    void archivesThenDeletes() {
        // given
        given(runningComboWriter.write(ROOM_ID)).willReturn(new RunningComboArchive(1, 1, 2));

        // when
        archiver.archiveAfterCommit(ROOM_ID);

        // then -> 먼저 지우면 실패한 방의 콤보가 어디에도 남지 않는다
        InOrder order = inOrder(runningComboWriter, deleteRunningComboStatePort);
        order.verify(runningComboWriter).write(ROOM_ID);
        order.verify(deleteRunningComboStatePort).deleteComboState(ROOM_ID);
        assertThat(log.messages(Level.INFO)).containsExactly(
                "[러닝] 콤보 저장 성공 - roomId=12, closedCount=1, openCount=1, rowCount=2");
    }

    @Test
    @DisplayName("저장에 실패하면 던지지 않고 ERROR로 남기며 상태를 지우지 않는다")
    void keepsStateOnFailure() {
        // given
        given(runningComboWriter.write(ROOM_ID)).willThrow(new IllegalStateException("redis down"));

        // when -> 방 닫힘은 이미 커밋됐다. 던지면 커밋된 종료가 클라에는 실패로 보인다
        assertThatCode(() -> archiver.archiveAfterCommit(ROOM_ID)).doesNotThrowAnyException();

        // then -> TTL까지 남겨 원인을 들여다볼 수 있게 한다
        verify(deleteRunningComboStatePort, never()).deleteComboState(anyLong());
        assertThat(log.messages(Level.ERROR)).containsExactly(
                "[러닝] 콤보 저장 실패: 처리하지 못한 예외 - roomId=12");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }

    @Test
    @DisplayName("CANCELLED 방은 저장하지 않고 상태만 지운다")
    void discardsWithoutWriting() {
        // when
        archiver.discardAfterCommit(ROOM_ID);

        // then
        verify(deleteRunningComboStatePort).deleteComboState(ROOM_ID);
        verify(runningComboWriter, never()).write(anyLong());
    }
}
