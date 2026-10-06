package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.application.scheduling.port.out.RegisterJobTimerPort;
import com.runiverse.running_service.infrastructure.redis.scheduling.ScheduledJobListener;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("예약 전파 리스너 단위 테스트")
class ScheduledJobListenerTest {

    private static final String CHANNEL = "schedule:job";

    @Mock
    private RegisterJobTimerPort registerJobTimerPort;

    private ScheduledJobListener listener;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        listener = new ScheduledJobListener(JsonMapper.builder().build(), registerJobTimerPort);
        log = LogCapture.of(ScheduledJobListener.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("깨진 메시지는 타이머를 걸지 않고 채널과 원인 예외를 담아 ERROR로 남긴다")
    void logsBrokenMessageAsError() {
        // given
        Message broken = new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                "{ 이건 JSON이 아니다".getBytes(StandardCharsets.UTF_8));

        // when
        assertThatCode(() -> listener.onMessage(broken, null)).doesNotThrowAnyException();

        // then -> 보낸 쪽도 우리 서버라 사용자 잘못일 수 없다
        verifyNoInteractions(registerJobTimerPort);
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[예약] 예약 전파 메시지 파싱 실패: 메시지 형식 불일치 - channel=" + CHANNEL);
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }
}
