package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;
import com.runiverse.running_service.application.match.port.in.BroadcastMatchEventUsecase;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RunningReady;
import com.runiverse.running_service.infrastructure.redis.match.MatchEventListener;
import com.runiverse.running_service.infrastructure.redis.match.MatchEventRedisAdapter;
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
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 이벤트 리스너 단위 테스트")
class MatchEventListenerTest {

    private static final String CHANNEL = "match:room:42";

    @Mock
    private BroadcastMatchEventUsecase broadcastMatchEventUsecase;

    @Mock
    private StringRedisTemplate redisTemplate;

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
    @DisplayName("발행한 방 채팅이 Redis를 한 바퀴 돌아도 채팅과 받지 않을 사람이 그대로 복원된다")
    void roomChatSurvivesRoundTrip() {
        // given -> 다른 인스턴스가 발행한 메시지다. 보낸 사람이 어느 서버에 붙었는지 받는 쪽은 모른다
        RoomChat chat = new RoomChat(3021L, 42L,
                new RoomChat.Sender(UuidCreator.getTimeOrderedEpoch(), "철수", null, false),
                "곧 출발해요", false, null, LocalDateTime.of(2026, 11, 10, 18, 52, 10));
        MatchStreamEvent sent = MatchStreamEvent.roomChatSent(chat);

        // when
        listener.onMessage(published(sent), null);

        // then -> 빠지면 보낸 사람도 자기 메시지를 이벤트로 한 번 더 받는다
        assertThat(received()).isEqualTo(sent);
    }

    @Test
    @DisplayName("방 상태 이벤트는 Redis를 돌아도 받지 않을 사람이 비어 있다")
    void roomStateRoundTripExcludesNobody() {
        // given
        MatchStreamEvent sent = MatchStreamEvent.runningReady(
                new RunningReady(42L, LocalDateTime.of(2026, 11, 10, 19, 0), 10_000L));

        // when
        listener.onMessage(published(sent), null);

        // then
        MatchStreamEvent received = received();
        assertThat(received).isEqualTo(sent);
        assertThat(received.excludedUserId()).isNull();
        assertThat(received.chat()).isNull();
    }

    // 실제 발행 어댑터가 쓴 JSON을 그대로 받는다 — 두 쪽 필드가 어긋나면 여기서 드러난다
    private Message published(MatchStreamEvent event) {
        new MatchEventRedisAdapter(redisTemplate, JsonMapper.builder().build()).publish(event);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(anyString(), body.capture());
        return new DefaultMessage(CHANNEL.getBytes(StandardCharsets.UTF_8),
                body.getValue().getBytes(StandardCharsets.UTF_8));
    }

    private MatchStreamEvent received() {
        ArgumentCaptor<BroadcastMatchEventCommand> command = ArgumentCaptor.forClass(BroadcastMatchEventCommand.class);
        verify(broadcastMatchEventUsecase).handle(command.capture());
        return command.getValue().event();
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
