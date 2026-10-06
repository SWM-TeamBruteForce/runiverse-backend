package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.accountdeletion.AccountDeletionProperties;
import com.runiverse.running_service.application.user.command.accountdeletion.DeletedUserRedactor;
import com.runiverse.running_service.application.user.command.accountdeletion.RedactDeletedUsersHandler;
import com.runiverse.running_service.application.user.port.out.LoadDeletedUserIdsPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import ch.qos.logback.classic.Level;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("탈퇴 기록 신원 정보 제거 배치 단위 테스트")
class RedactDeletedUsersHandlerTest {

    private static final Duration RETENTION = Duration.ofDays(90);
    private static final AccountDeletionProperties PROPERTIES =
            new AccountDeletionProperties(RETENTION);

    @Mock
    private LoadDeletedUserIdsPort loadDeletedUserIdsPort;
    @Mock
    private DeletedUserRedactor deletedUserRedactor;

    private RedactDeletedUsersHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RedactDeletedUsersHandler(
                loadDeletedUserIdsPort, deletedUserRedactor, PROPERTIES);
    }

    @Test
    @DisplayName("지금으로부터 보관 기간을 뺀 시각을 기준으로 조회한다")
    void loadsRecordsOlderThanRetention() {
        // given
        given(loadDeletedUserIdsPort.loadDeletedBefore(any())).willReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        // when
        handler.redactAfterRetention();

        // then
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(loadDeletedUserIdsPort).loadDeletedBefore(captor.capture());
        assertThat(captor.getValue())
                .isBetween(before.minus(RETENTION), LocalDateTime.now().minus(RETENTION));
    }

    @Test
    @DisplayName("조회된 기록을 하나씩 지운다")
    void redactsEveryLoadedRecord() {
        // given
        UserId first = new UserId(UuidCreator.getTimeOrderedEpoch());
        UserId second = new UserId(UuidCreator.getTimeOrderedEpoch());
        given(loadDeletedUserIdsPort.loadDeletedBefore(any())).willReturn(List.of(first, second));

        // when
        handler.redactAfterRetention();

        // then
        verify(deletedUserRedactor).redact(first);
        verify(deletedUserRedactor).redact(second);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지를 계속 지운다")
    void keepsGoingWhenOneRecordFails() {
        // given -> 실패한 행은 신원 정보가 남아 다음 실행에 다시 걸린다
        UserId failing = new UserId(UuidCreator.getTimeOrderedEpoch());
        UserId next = new UserId(UuidCreator.getTimeOrderedEpoch());
        given(loadDeletedUserIdsPort.loadDeletedBefore(any())).willReturn(List.of(failing, next));
        doThrow(new IllegalStateException("S3 삭제 실패")).when(deletedUserRedactor).redact(failing);

        // when
        handler.redactAfterRetention();

        // then
        verify(deletedUserRedactor).redact(next);
    }

    @Test
    @DisplayName("지울 기록이 없으면 아무것도 하지 않는다")
    void doesNothingWhenNoRecordExpired() {
        // given
        given(loadDeletedUserIdsPort.loadDeletedBefore(any())).willReturn(List.of());

        // when
        handler.redactAfterRetention();

        // then
        verify(deletedUserRedactor, never()).redact(any());
    }

    @Test
    @DisplayName("한 건이 실패하면 그 userId와 원인 예외를 담아 [회원] ERROR로 남긴다")
    void logsFailedRecordAsError() {
        // given -> 스케줄러 스레드라 중앙 예외 핸들러가 없다. 여기서 안 찍으면 흔적이 없다
        LogCapture log = LogCapture.of(RedactDeletedUsersHandler.class);
        UserId failing = new UserId(UuidCreator.getTimeOrderedEpoch());
        given(loadDeletedUserIdsPort.loadDeletedBefore(any())).willReturn(List.of(failing));
        doThrow(new IllegalStateException("S3 삭제 실패")).when(deletedUserRedactor).redact(failing);

        try {
            // when
            handler.redactAfterRetention();

            // then
            assertThat(log.messages(Level.ERROR)).containsExactly(
                    "[회원] 탈퇴 기록 신원 정보 제거 실패: 처리하지 못한 예외 - userId=" + failing.value());
            assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
        } finally {
            log.stop();
        }
    }
}
