package com.runiverse.running_service.unit_test.match.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.presentation.match.response.InviteRoomEntranceResponse;
import com.runiverse.running_service.presentation.match.response.RoomInfoResponse;
import com.runiverse.running_service.presentation.match.sse.SseMatchStreamConnection;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

// 와이어 계약 — 이벤트 이름마다 data 형태가 다르다. 무엇을 실어 보내는지만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 스트림 SSE 전송 단위 테스트")
class SseMatchStreamConnectionTest {

    private static final long ROOM_ID = 130L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 27, 19, 0);
    private static final UUID INVITEE = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private SseEmitter emitter;

    private SseMatchStreamConnection connection;

    @BeforeEach
    void setUp() {
        connection = new SseMatchStreamConnection("conn-1", emitter);
    }

    private static RoomInfo roomOf(RunningRoomType type, List<RoomInfo.RoomInvitee> invitations) {
        return new RoomInfo(ROOM_ID, type, RunningRoomStatus.MATCHING, START, START.minusMinutes(1),
                7_000, 330, List.of(), invitations);
    }

    // 보낸 이벤트에서 JSON 본문 조각만 꺼낸다 — 나머지는 "event:"·"data:" 같은 SSE 틀이다
    private Object sentBody() throws Exception {
        ArgumentCaptor<SseEmitter.SseEventBuilder> sent = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(sent.capture());
        return sent.getValue().build().stream()
                .filter(part -> MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .map(ResponseBodyEmitter.DataWithMediaType::getData)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("초대 방 입장은 방 정보와 같은 깊이에 남은 시간을 더 싣는다")
    void entranceCarriesStartsInMsFlat() throws Exception {
        // given
        RoomInfo room = roomOf(RunningRoomType.INVITE, List.of(new RoomInfo.RoomInvitee(
                INVITEE, "영희", null, RoomInvitationStatus.PENDING, false)));

        // when
        connection.send(MatchStreamEvent.snapshot(room, START.minusMinutes(20)));

        // then
        Object body = sentBody();
        assertThat(body).isInstanceOf(InviteRoomEntranceResponse.class);
        JsonNode json = JSON.valueToTree(body);
        assertThat(json.get("startsInMs").asLong()).isEqualTo(1_200_000L);
        assertThat(json.get("type").asString()).isEqualTo("INVITE");
        assertThat(json.get("runningRoomId").asLong()).isEqualTo(ROOM_ID);
        assertThat(json.get("invitations").get(0).get("status").asString()).isEqualTo("PENDING");
        assertThat(json.get("invitations").get(0).get("isDeleted").asBoolean()).isFalse();
        assertThat(json.has("room")).isFalse();   // 감싸지 않는다
    }

    @Test
    @DisplayName("초대 방 갱신은 방 정보만 싣고 남은 시간 키가 없다")
    void inviteUpdatedHasNoStartsInMs() throws Exception {
        // when
        connection.send(MatchStreamEvent.updated(roomOf(RunningRoomType.INVITE, List.of())));

        // then
        Object body = sentBody();
        assertThat(body).isInstanceOf(RoomInfoResponse.class);
        assertThat(JSON.valueToTree(body).has("startsInMs")).isFalse();
    }

    @Test
    @DisplayName("매칭 방 갱신도 방 종류와 빈 초대 현황을 싣는다")
    void matchUpdatedCarriesTypeAndEmptyInvitations() throws Exception {
        // when
        connection.send(MatchStreamEvent.updated(roomOf(RunningRoomType.MATCH, List.of())));

        // then -> 매칭 방은 invitations가 항상 빈 배열이다(null이 아니다)
        JsonNode json = JSON.valueToTree(sentBody());
        assertThat(json.get("type").asString()).isEqualTo("MATCH");
        assertThat(json.get("invitations").isArray()).isTrue();
        assertThat(json.get("invitations")).isEmpty();
        assertThat(json.has("startsInMs")).isFalse();
    }
}
