package com.runiverse.running_service.unit_test.legacy.v1_1.match;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.RunningReady;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.legacy.v1_1.match.response.LegacyRoomInfoResponse;
import com.runiverse.running_service.legacy.v1_1.match.sse.LegacySseMatchStreamConnection;
import com.runiverse.running_service.presentation.match.response.RunningReadyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 1.1.x 와이어 계약 — 이벤트 이름과 data가 1.1.1 서버와 같은지 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("1.1.x 매칭 스트림 SSE 전송 단위 테스트")
class LegacySseMatchStreamConnectionTest {

    private static final long ROOM_ID = 130L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 19, 0);
    private static final UUID PLAYER = UuidCreator.getTimeOrderedEpoch();
    private static final UUID INVITEE = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private SseEmitter emitter;

    private LegacySseMatchStreamConnection connection;

    @BeforeEach
    void setUp() {
        connection = new LegacySseMatchStreamConnection("conn-1", emitter);
    }

    @Test
    @DisplayName("확정 이벤트는 1.1.x가 아는 MATCH_STARTED 이름으로 보낸다")
    void confirmedIsSentAsMatchStarted() throws Exception {
        // when
        connection.send(MatchStreamEvent.confirmed(roomOf(RunningRoomType.MATCH, List.of())));

        // then -> 1.2.0 이름(MATCH_CONFIRMED)이면 1.1.x는 확정 화면을 띄우지 못한다
        Set<SseEmitter.DataWithMediaType> sent = sent();
        assertThat(eventName(sent)).isEqualTo("MATCH_STARTED");
        assertThat(body(sent)).isInstanceOf(LegacyRoomInfoResponse.class);
    }

    @Test
    @DisplayName("방 갱신은 같은 이름으로 1.1.1 형식 방 정보를 싣는다")
    void updatedKeepsNameWithLegacyRoom() throws Exception {
        // when
        connection.send(MatchStreamEvent.updated(roomOf(RunningRoomType.MATCH, List.of())));

        // then -> type·invitations는 1.2.0에서 붙은 필드라 싣지 않는다
        Set<SseEmitter.DataWithMediaType> sent = sent();
        assertThat(eventName(sent)).isEqualTo("MATCH_ROOM_UPDATED");
        JsonNode json = JSON.valueToTree(body(sent));
        assertThat(json.propertyNames()).containsExactly(
                "runningRoomId", "status", "scheduledStartAt", "closeAt",
                "targetDistanceMeters", "teamAveragePaceSecondsPerKm", "players");
        assertThat(json.get("players").get(0).propertyNames()).containsExactly(
                "userId", "nickname", "profileImageUrl", "introduction", "averagePaceSecondsPerKm", "isDeleted");
        assertThat(json.get("players").get(0).get("userId").asString()).isEqualTo(PLAYER.toString());
    }

    @Test
    @DisplayName("초대 방 입장·갱신은 MATCH_ROOM_UPDATED로 바꿔 보낸다")
    void inviteRoomEventsFallBackToRoomUpdated() throws Exception {
        // given
        RoomInfo inviteRoom = roomOf(RunningRoomType.INVITE, List.of(new RoomInfo.RoomInvitee(
                INVITEE, "영희", null, RoomInvitationStatus.PENDING, false)));

        // when
        connection.send(MatchStreamEvent.snapshot(inviteRoom, START.minusMinutes(20)));

        // then -> 1.1.x는 INVITE_ROOM_*을 모른다. 남은 시간·초대 현황도 싣지 않는다
        Set<SseEmitter.DataWithMediaType> sent = sent();
        assertThat(eventName(sent)).isEqualTo("MATCH_ROOM_UPDATED");
        JsonNode json = JSON.valueToTree(body(sent));
        assertThat(json.has("startsInMs")).isFalse();
        assertThat(json.has("invitations")).isFalse();
    }

    @Test
    @DisplayName("곧 시작 통지는 이름·data 모두 1.2.0과 같다")
    void runningReadyIsUnchanged() throws Exception {
        // when
        connection.send(MatchStreamEvent.runningReady(new RunningReady(ROOM_ID, START, 30_000L)));

        // then
        Set<SseEmitter.DataWithMediaType> sent = sent();
        assertThat(eventName(sent)).isEqualTo("RUNNING_READY");
        assertThat(body(sent)).isEqualTo(new RunningReadyResponse(ROOM_ID, START, 30_000L));
    }

    @Test
    @DisplayName("방 채팅은 1.1.x가 모르는 이벤트라 보내지 않는다")
    void roomChatIsNotSent() throws Exception {
        // given
        RoomChat chat = new RoomChat(3021L, ROOM_ID, new RoomChat.Sender(PLAYER, "철수", null, false),
                "곧 출발해요", false, null, START.minusMinutes(8));

        // when
        connection.send(MatchStreamEvent.roomChatSent(chat));

        // then -> 아무것도 쓰지 않고 연결도 닫지 않는다
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, never()).complete();
    }

    @Test
    @DisplayName("전송이 실패하면 스트림을 닫는다")
    void closesOnSendFailure() throws Exception {
        // given
        willThrow(new IOException("broken pipe")).given(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // when
        connection.keepAlive();

        // then -> 닫아야 onCompletion이 돌아 레지스트리에서 빠진다
        verify(emitter).complete();
    }

    private static RoomInfo roomOf(RunningRoomType type, List<RoomInfo.RoomInvitee> invitations) {
        return new RoomInfo(ROOM_ID, type, RunningRoomStatus.MATCHING, START, START.minusMinutes(1),
                5_000, 330, List.of(new RoomInfo.RoomPlayer(PLAYER, "철수", null, null, 330, false)),
                invitations);
    }

    private Set<SseEmitter.DataWithMediaType> sent() throws Exception {
        ArgumentCaptor<SseEmitter.SseEventBuilder> captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(captor.capture());
        return captor.getValue().build();
    }

    // SSE 틀 조각("event:이름\ndata:")에서 이벤트 이름을 꺼낸다
    private static String eventName(Set<SseEmitter.DataWithMediaType> sent) {
        String frame = sent.stream()
                .filter(part -> !MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .map(part -> String.valueOf(part.getData()))
                .collect(Collectors.joining());
        return frame.lines()
                .filter(line -> line.startsWith("event:"))
                .map(line -> line.substring("event:".length()))
                .findFirst()
                .orElseThrow();
    }

    private static Object body(Set<SseEmitter.DataWithMediaType> sent) {
        return sent.stream()
                .filter(part -> MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .map(ResponseBodyEmitter.DataWithMediaType::getData)
                .findFirst()
                .orElseThrow();
    }
}
