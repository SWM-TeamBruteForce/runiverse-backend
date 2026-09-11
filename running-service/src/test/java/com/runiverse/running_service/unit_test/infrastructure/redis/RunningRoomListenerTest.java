package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.runiverse.running_service.application.running.command.combo.BroadcastRunningComboCommand;
import com.runiverse.running_service.application.running.command.progress.BroadcastRunningProgressCommand;
import com.runiverse.running_service.application.running.port.in.BroadcastRunningComboUsecase;
import com.runiverse.running_service.application.running.port.in.BroadcastRunningProgressUsecase;
import com.runiverse.running_service.application.running.port.in.CloseSupersededSessionUsecase;
import com.runiverse.running_service.application.running.port.out.RunningComboRelation;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;
import com.runiverse.running_service.application.running.port.out.RunningProgress;
import com.runiverse.running_service.infrastructure.redis.running.ComboMessage;
import com.runiverse.running_service.infrastructure.redis.running.ProgressMessage;
import com.runiverse.running_service.infrastructure.redis.running.RunningRoomListener;
import com.runiverse.running_service.infrastructure.redis.running.RunningRoomMessage;
import com.runiverse.running_service.infrastructure.redis.running.RunningRoomMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 발행 어댑터가 만드는 것과 똑같은 봉투를 실제로 직렬화해 되먹인다 —
// 콤보 통은 관계 목록을 중첩해 실어서, 평평한 진행 통지와 달리 복원이 깨질 여지가 있다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 방 채널 리스너 단위 테스트")
class RunningRoomListenerTest {

    private static final Long ROOM_ID = 42L;
    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final UUID THIRD = UUID.randomUUID();

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private CloseSupersededSessionUsecase closeSupersededSessionUsecase;

    @Mock
    private BroadcastRunningProgressUsecase broadcastRunningProgressUsecase;

    @Mock
    private BroadcastRunningComboUsecase broadcastRunningComboUsecase;

    private RunningRoomListener listener;

    @BeforeEach
    void setUp() {
        listener = new RunningRoomListener(
                jsonMapper,
                closeSupersededSessionUsecase,
                broadcastRunningProgressUsecase,
                broadcastRunningComboUsecase);
    }

    @Test
    @DisplayName("콤보 통은 방 채널을 건너도 수신자와 관계가 그대로 되살아난다")
    void comboMessageRoundTrips() {
        // given -> 관계 둘, 수신자 셋짜리 통
        RunningComboUpdate update = new RunningComboUpdate(
                Set.of(FIRST, SECOND, THIRD),
                List.of(
                        new RunningComboRelation(FIRST, SECOND, 18, 12, 30),
                        new RunningComboRelation(SECOND, THIRD, -7, 4, 4)));

        // when
        listener.onMessage(published(
                RunningRoomMessageType.COMBO, ComboMessage.of(ROOM_ID, update)), null);

        // then
        ArgumentCaptor<BroadcastRunningComboCommand> captor =
                ArgumentCaptor.forClass(BroadcastRunningComboCommand.class);
        verify(broadcastRunningComboUsecase).handle(captor.capture());
        assertThat(captor.getValue().runningRoomId()).isEqualTo(ROOM_ID);
        assertThat(captor.getValue().update()).isEqualTo(update);
    }

    @Test
    @DisplayName("겹치는 상대가 없는 통도 빈 관계 목록으로 되살아난다")
    void emptyComboMessageRoundTrips() {
        // given -> 목록이 비는 것이 곧 끊김 통지라 중간에 사라지면 안 된다
        RunningComboUpdate update = new RunningComboUpdate(Set.of(FIRST), List.of());

        // when
        listener.onMessage(published(
                RunningRoomMessageType.COMBO, ComboMessage.of(ROOM_ID, update)), null);

        // then
        ArgumentCaptor<BroadcastRunningComboCommand> captor =
                ArgumentCaptor.forClass(BroadcastRunningComboCommand.class);
        verify(broadcastRunningComboUsecase).handle(captor.capture());
        assertThat(captor.getValue().update().relations()).isEmpty();
        assertThat(captor.getValue().update().recipients()).containsExactly(FIRST);
    }

    @Test
    @DisplayName("진행 통지도 같은 봉투로 되살아난다")
    void progressMessageRoundTrips() {
        // given
        RunningProgress progress = new RunningProgress(FIRST, 3_433, 5_000, 345, false);

        // when
        listener.onMessage(published(
                RunningRoomMessageType.PROGRESS, ProgressMessage.of(ROOM_ID, progress)), null);

        // then
        ArgumentCaptor<BroadcastRunningProgressCommand> captor =
                ArgumentCaptor.forClass(BroadcastRunningProgressCommand.class);
        verify(broadcastRunningProgressUsecase).handle(captor.capture());
        assertThat(captor.getValue().progress()).isEqualTo(progress);
    }

    @Test
    @DisplayName("깨진 메시지 한 건은 삼키고 이후 수신을 막지 않는다")
    void swallowsBrokenMessage() {
        // given
        Message broken = new DefaultMessage(
                new byte[0], "{ 이건 JSON이 아니다".getBytes(StandardCharsets.UTF_8));

        // when & then
        assertThatCode(() -> listener.onMessage(broken, null)).doesNotThrowAnyException();
        verifyNoInteractions(broadcastRunningComboUsecase, broadcastRunningProgressUsecase);
    }

    // 발행 어댑터가 채널에 실어 보내는 것과 같은 형태의 메시지
    private Message published(RunningRoomMessageType type, Object payload) {
        String body = jsonMapper.writeValueAsString(new RunningRoomMessage(type, payload));
        return new DefaultMessage(new byte[0], body.getBytes(StandardCharsets.UTF_8));
    }
}
