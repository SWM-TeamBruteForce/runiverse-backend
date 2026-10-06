package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.application.match.port.in.BroadcastMatchEventUsecase;
import com.runiverse.running_service.infrastructure.redis.match.MatchEventListener;
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
@DisplayName("매칭 이벤트 리스너 단위 테스트")
class MatchEventListenerTest {

    private static final String CHANNEL = "match:room:42";

    @Mock
    private BroadcastMatchEventUsecase broadcastMatchEventUsecase;

    private MatchEventListener listener;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        listener = new MatchEventListener(JsonMapper.builder().build(), broadcastMatchEventUsecase);
        log = LogCapture.of(MatchEventListener.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("깨진 메시지는 삼키되 보낸 쪽도 우리 서버라 채널과 원인 예외를 담아 ERROR로 남긴다")
    void logsBrokenMessageAsError() {
        // given
        Message broken = new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                "{ 이건 JSON이 아니다".getBytes(StandardCharsets.UTF_8));

        // when -> 깨진 메시지 한 건 때문에 이후 수신이 막히면 안 된다
        assertThatCode(() -> listener.onMessage(broken, null)).doesNotThrowAnyException();

        // then
        verifyNoInteractions(broadcastMatchEventUsecase);
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[매칭] 이벤트 파싱 실패: 메시지 형식 불일치 - channel=" + CHANNEL);
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }
}
