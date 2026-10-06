package com.runiverse.running_service.unit_test.infrastructure.scheduler;

import com.runiverse.running_service.application.user.port.in.RedactDeletedUsersUsecase;
import com.runiverse.running_service.infrastructure.scheduler.DeletedUserRedactionRunner;
import ch.qos.logback.classic.Level;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("탈퇴 기록 신원 정보 제거 스케줄러 단위 테스트")
class DeletedUserRedactionRunnerTest {

    @Mock
    private RedactDeletedUsersUsecase redactDeletedUsersUsecase;

    @InjectMocks
    private DeletedUserRedactionRunner runner;

    @Test
    @DisplayName("실행되면 배치를 부른다")
    void runsTheBatch() {
        // when
        runner.redactOnSchedule();

        // then
        verify(redactDeletedUsersUsecase).redactAfterRetention();
    }

    @Test
    @DisplayName("배치가 실패해도 예외를 밖으로 내보내지 않는다")
    void swallowsFailure() {
        // given -> 스케줄러 스레드로 예외가 새면 다음 실행까지 함께 죽는다
        doThrow(new IllegalStateException("배치 실패"))
                .when(redactDeletedUsersUsecase).redactAfterRetention();

        // when & then
        assertThatCode(() -> runner.redactOnSchedule()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("배치가 실패하면 원인 예외를 담아 [회원] ERROR로 남긴다")
    void logsFailureAsError() {
        // given -> 스케줄러 스레드에는 중앙 예외 핸들러가 없다
        LogCapture log = LogCapture.of(DeletedUserRedactionRunner.class);
        doThrow(new IllegalStateException("배치 실패"))
                .when(redactDeletedUsersUsecase).redactAfterRetention();

        try {
            // when
            runner.redactOnSchedule();

            // then
            assertThat(log.messages(Level.ERROR))
                    .containsExactly("[회원] 탈퇴 기록 신원 정보 제거 실패: 처리하지 못한 예외");
            assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
        } finally {
            log.stop();
        }
    }
}
