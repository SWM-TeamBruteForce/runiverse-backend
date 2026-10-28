package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.notification.command.push.PushUserNotificationCommand;
import com.runiverse.running_service.application.notification.port.in.PushUserNotificationUsecase;
import com.runiverse.running_service.infrastructure.redis.notification.UserNotificationListener;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 전파 수신 단위 테스트")
class UserNotificationListenerTest {

    private static final UUID RECIPIENT_ID = UuidCreator.getTimeOrderedEpoch();
    private static final byte[] CHANNEL = "notification:user".getBytes(StandardCharsets.UTF_8);

    @Mock
    private PushUserNotificationUsecase pushUserNotificationUsecase;

    private UserNotificationListener listener;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        listener = new UserNotificationListener(JsonMapper.builder().build(), pushUserNotificationUsecase);
        log = LogCapture.of(UserNotificationListener.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("받은 메시지의 받는 사람과 알림 ID로 푸시를 부른다")
    void pushesFromMessage() {
        // given -> 발행 어댑터가 싣는 모양 그대로다
        String body = "{\"userId\":\"" + RECIPIENT_ID + "\",\"notificationId\":912}";

        // when
        listener.onMessage(new DefaultMessage(CHANNEL, body.getBytes(StandardCharsets.UTF_8)), null);

        // then
        verify(pushUserNotificationUsecase).handle(new PushUserNotificationCommand(RECIPIENT_ID, 912L));
    }

    @Test
    @DisplayName("깨진 메시지는 던지지 않고 채널을 담아 ERROR로 남긴 뒤 건너뛴다")
    void logsBrokenMessage() {
        // when -> 한 건 때문에 이후 수신이 막히면 안 된다
        assertThatCode(() -> listener.onMessage(
                new DefaultMessage(CHANNEL, "not-json".getBytes(StandardCharsets.UTF_8)), null))
                .doesNotThrowAnyException();

        // then
        verifyNoInteractions(pushUserNotificationUsecase);
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[알림] 알림 전파 메시지 파싱 실패: 메시지 형식 불일치 - channel=notification:user");
    }
}
