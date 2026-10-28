package com.runiverse.running_service.unit_test.notification.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.notification.command.read.ReadAllUserNotificationsCommand;
import com.runiverse.running_service.application.notification.command.read.ReadAllUserNotificationsHandler;
import com.runiverse.running_service.application.notification.port.out.MarkUserNotificationsReadPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 모두 읽음 단위 테스트")
class ReadAllUserNotificationsHandlerTest {

    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private MarkUserNotificationsReadPort markUserNotificationsReadPort;

    private ReadAllUserNotificationsHandler handler;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        handler = new ReadAllUserNotificationsHandler(markUserNotificationsReadPort);
        log = LogCapture.of(ReadAllUserNotificationsHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("내 안 읽은 알림을 지금 시각으로 읽음 처리하고 바꾼 수를 남긴다")
    void marksAllReadWithNow() {
        // given
        given(markUserNotificationsReadPort.markAllRead(eq(new UserId(ME)), any())).willReturn(3);
        LocalDateTime before = LocalDateTime.now();

        // when
        handler.handle(new ReadAllUserNotificationsCommand(ME));

        // then -> 벌크 갱신은 @UpdateTimestamp를 거치지 않아 시각을 직접 넘긴다
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(markUserNotificationsReadPort).markAllRead(eq(new UserId(ME)), now.capture());
        assertThat(now.getValue()).isBetween(before, LocalDateTime.now());
        assertThat(log.messages(Level.INFO)).containsExactly("[알림] 모두 읽음 성공 - readCount=3");
    }

    @Test
    @DisplayName("안 읽은 알림이 없어도 성공한다")
    void succeedsWithNothingToRead() {
        // given
        given(markUserNotificationsReadPort.markAllRead(eq(new UserId(ME)), any())).willReturn(0);

        // when & then
        assertThatCode(() -> handler.handle(new ReadAllUserNotificationsCommand(ME))).doesNotThrowAnyException();
    }
}
