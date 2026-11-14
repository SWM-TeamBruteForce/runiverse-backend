package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.stream.CloseSupersededMatchStreamCommand;
import com.runiverse.running_service.application.match.port.in.CloseSupersededMatchStreamUsecase;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.match.MatchChannel;
import com.runiverse.running_service.infrastructure.redis.match.MatchSupersedeListener;
import com.runiverse.running_service.infrastructure.redis.match.MatchSupersedeRedisAdapter;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 스트림 대체 알림 수신 리스너 단위 테스트")
class MatchSupersedeListenerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final String WINNER_CONNECTION = "conn-winner";

    @Mock
    private CloseSupersededMatchStreamUsecase closeSupersededMatchStreamUsecase;

    @Mock
    private StringRedisTemplate redisTemplate;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private MatchSupersedeListener listener;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        listener = new MatchSupersedeListener(jsonMapper, closeSupersededMatchStreamUsecase);
        log = LogCapture.of(MatchSupersedeListener.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    private static DefaultMessage message(String body) {
        return new DefaultMessage(MatchChannel.SUPERSEDE.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("발행 어댑터가 보낸 본문을 그대로 받아 사용자와 이긴 연결 ID를 넘긴다")
    void roundTripsFromPublisher() {
        // given -> 발행 쪽이 실제로 보내는 본문을 쓴다 — 두 쪽 모양이 어긋나면 여기서 깨진다
        new MatchSupersedeRedisAdapter(redisTemplate, jsonMapper).publish(new UserId(USER_ID), WINNER_CONNECTION);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(MatchChannel.SUPERSEDE), body.capture());

        // when
        listener.onMessage(message(body.getValue()), null);

        // then
        verify(closeSupersededMatchStreamUsecase)
                .handle(new CloseSupersededMatchStreamCommand(USER_ID, WINNER_CONNECTION));
    }

    @Test
    @DisplayName("깨진 메시지는 던지지 않고 채널을 담아 ERROR로 남긴 뒤 넘긴다")
    void skipsBrokenMessage() {
        // when -> 던지면 리스너 컨테이너의 이후 수신까지 흔들린다
        assertThatCode(() -> listener.onMessage(message("{not json"), null)).doesNotThrowAnyException();

        // then
        verifyNoInteractions(closeSupersededMatchStreamUsecase);
        assertThat(log.messages(Level.ERROR)).containsExactly(
                "[매칭] 이전 스트림 종료 알림 파싱 실패: 메시지 형식 불일치 - channel=" + MatchChannel.SUPERSEDE);
    }
}
