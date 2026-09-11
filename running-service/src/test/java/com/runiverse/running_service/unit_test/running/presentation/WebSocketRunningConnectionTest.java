package com.runiverse.running_service.unit_test.running.presentation;

import com.runiverse.running_service.application.running.port.out.RunningComboPeer;
import com.runiverse.running_service.application.running.port.out.RunningProgress;
import com.runiverse.running_service.presentation.running.websocket.WebSocketRunningConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

// 이벤트 이름과 payload 필드명은 앱과의 계약이다. 상수 이름만 바꿔도 컴파일과
// 나머지 테스트는 전부 통과하므로, 나가는 문자열을 여기서 직접 못박는다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 WebSocket 연결 단위 테스트")
class WebSocketRunningConnectionTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private WebSocketSession session;

    @Test
    @DisplayName("진행 통지는 RUNNING_PROGRESS_UPDATED 이벤트로 나간다")
    void sendProgress_sendsContractEventName() throws IOException {
        // given
        UUID userId = UUID.randomUUID();
        WebSocketRunningConnection connection = new WebSocketRunningConnection(session, jsonMapper);

        // when
        connection.sendProgress(new RunningProgress(userId, 3_433, 5_000, 345, false));

        // then
        JsonNode sent = jsonMapper.readTree(captureSent().getPayload());
        assertThat(sent.get("event").asString()).isEqualTo("RUNNING_PROGRESS_UPDATED");

        JsonNode data = sent.get("data");
        assertThat(data.get("userId").asString()).isEqualTo(userId.toString());
        assertThat(data.get("distanceMeters").asInt()).isEqualTo(3_433);
        assertThat(data.get("targetDistanceMeters").asInt()).isEqualTo(5_000);
        assertThat(data.get("currentPaceSecondsPerKm").asInt()).isEqualTo(345);
        assertThat(data.get("paused").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("목표 없는 솔로 방과 페이스를 못 잰 단말은 null로 나간다")
    void sendProgress_keepsNullableFields() throws IOException {
        // given
        WebSocketRunningConnection connection = new WebSocketRunningConnection(session, jsonMapper);

        // when
        connection.sendProgress(new RunningProgress(UUID.randomUUID(), 1_200, null, null, true));

        // then
        JsonNode data = jsonMapper.readTree(captureSent().getPayload()).get("data");
        assertThat(data.get("targetDistanceMeters").isNull()).isTrue();
        assertThat(data.get("currentPaceSecondsPerKm").isNull()).isTrue();
        assertThat(data.get("paused").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("콤보 통지는 RUNNING_COMBO_UPDATED 이벤트로 나간다")
    void sendCombo_sendsContractEventName() throws IOException {
        // given -> 상대가 18m 앞서 있다
        UUID peerId = UUID.randomUUID();
        WebSocketRunningConnection connection = new WebSocketRunningConnection(session, jsonMapper);

        // when
        connection.sendCombo(List.of(new RunningComboPeer(peerId, 18, 12, 30)));

        // then
        JsonNode sent = jsonMapper.readTree(captureSent().getPayload());
        assertThat(sent.get("event").asString()).isEqualTo("RUNNING_COMBO_UPDATED");

        JsonNode peer = sent.get("data").get("peers").get(0);
        assertThat(peer.get("userId").asString()).isEqualTo(peerId.toString());
        assertThat(peer.get("gapMeters").asInt()).isEqualTo(18);
        assertThat(peer.get("comboCount").asInt()).isEqualTo(12);
        assertThat(peer.get("maxComboCount").asInt()).isEqualTo(30);
    }

    @Test
    @DisplayName("상대가 뒤처져 있으면 gapMeters가 음수로 실린다")
    void sendCombo_carriesNegativeGap() throws IOException {
        // given
        WebSocketRunningConnection connection = new WebSocketRunningConnection(session, jsonMapper);

        // when
        connection.sendCombo(List.of(new RunningComboPeer(UUID.randomUUID(), -18, 3, 9)));

        // then
        JsonNode peer = jsonMapper.readTree(captureSent().getPayload())
                .get("data").get("peers").get(0);
        assertThat(peer.get("gapMeters").asInt()).isEqualTo(-18);
    }

    @Test
    @DisplayName("아무와도 겹치지 않으면 빈 목록으로 나간다")
    void sendCombo_sendsEmptyPeers() throws IOException {
        // given -> 끊긴 상대가 목록에서 빠지는 것이 곧 끊김 통지다.
        // 목록 자체가 생략되면 클라가 화면을 지울 근거를 잃는다
        WebSocketRunningConnection connection = new WebSocketRunningConnection(session, jsonMapper);

        // when
        connection.sendCombo(List.of());

        // then
        JsonNode peers = jsonMapper.readTree(captureSent().getPayload()).get("data").get("peers");
        assertThat(peers.isArray()).isTrue();
        assertThat(peers.size()).isZero();
    }

    @Test
    @DisplayName("전송에 실패해도 던지지 않는다")
    void sendProgress_swallowsSendFailure() throws IOException {
        // given -> 한 명에게 못 보냈다고 나머지 참가자의 브로드캐스트가 멈추면 안 된다
        willThrow(new IOException("closed")).given(session).sendMessage(any());
        WebSocketRunningConnection connection = new WebSocketRunningConnection(session, jsonMapper);

        // when & then
        assertThatCode(() -> connection.sendProgress(
                new RunningProgress(UUID.randomUUID(), 100, 5_000, 300, false)))
                .doesNotThrowAnyException();
    }

    private TextMessage captureSent() throws IOException {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        return captor.getValue();
    }
}
