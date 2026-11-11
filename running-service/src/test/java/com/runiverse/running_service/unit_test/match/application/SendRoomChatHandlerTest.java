package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.roomchatsend.SendRoomChatCommand;
import com.runiverse.running_service.application.match.command.roomchatsend.SendRoomChatHandler;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomChatAccessPolicy;
import com.runiverse.running_service.application.match.common.RoomChatAssembler;
import com.runiverse.running_service.application.match.exception.RoomChatClosedException;
import com.runiverse.running_service.application.match.exception.RoomChatRoomNotFoundException;
import com.runiverse.running_service.application.match.port.out.CreateRoomChatPort;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.port.out.SavedRoomChat;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 판정은 RoomChatAccessPolicyTest, 화면용 모양은 RoomChatAssemblerTest가 본다 —
// 여기는 판정 → 저장 → 조립 → 발행의 흐름과, 막히면 아무것도 남기지 않는지만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("방 채팅 보내기 단위 테스트")
class SendRoomChatHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final long CHAT_ID = 3021L;
    private static final UUID SENDER = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 11, 18, 52, 10);
    private static final RoomChat ASSEMBLED = new RoomChat(CHAT_ID, ROOM_ID,
            new RoomChat.Sender(SENDER, "철수", "https://cdn/profiles/c.png", false),
            "곧 출발해요", false, null, SENT_AT);

    @Mock
    private RoomChatAccessPolicy roomChatAccessPolicy;

    @Mock
    private CreateRoomChatPort createRoomChatPort;

    @Mock
    private RoomChatAssembler roomChatAssembler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SendRoomChatHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SendRoomChatHandler(roomChatAccessPolicy, createRoomChatPort, roomChatAssembler, eventPublisher);
    }

    private static SendRoomChatCommand command(String content) {
        return new SendRoomChatCommand(SENDER, ROOM_ID, content);
    }

    @Nested
    @DisplayName("보내기 테스트")
    class SendTest {

        @BeforeEach
        void givenSaved() {
            given(createRoomChatPort.create(any())).willReturn(new SavedRoomChat(CHAT_ID, SENT_AT));
            given(roomChatAssembler.assemble(any(RoomChatRow.class))).willReturn(ASSEMBLED);
        }

        @Test
        @DisplayName("판정을 통과하면 저장하고 조립한 채팅을 돌려준다")
        void savesAndReturnsAssembledChat() {
            // when
            RoomChat chat = handler.handle(command("곧 출발해요"));

            // then -> 판정이 먼저다. 막히면 저장하지 않는다
            InOrder order = inOrder(roomChatAccessPolicy, createRoomChatPort);
            order.verify(roomChatAccessPolicy).requireOpen(new UserId(SENDER), ROOM_ID);
            order.verify(createRoomChatPort).create(any());
            assertThat(chat).isEqualTo(ASSEMBLED);
        }

        @Test
        @DisplayName("요청한 방에 보낸 사람이 쓴 그대로 도메인 채팅을 만들어 저장한다")
        void savesDomainChatForRequestedRoom() {
            // when
            handler.handle(command("곧 출발해요"));

            // then -> 저장 전이라 ID가 없다
            ArgumentCaptor<RunningRoomChat> saved = ArgumentCaptor.forClass(RunningRoomChat.class);
            verify(createRoomChatPort).create(saved.capture());
            assertThat(saved.getValue().getRunningRoomId()).isEqualTo(new RunningRoomId(ROOM_ID));
            assertThat(saved.getValue().getUserId()).isEqualTo(new UserId(SENDER));
            assertThat(saved.getValue().getContent().value()).isEqualTo("곧 출발해요");
            assertThat(saved.getValue().getRunningRoomChatId()).isEmpty();
        }

        @Test
        @DisplayName("저장 결과로 새 메시지 행을 만들어 조립기에 넘긴다")
        void assemblesFromSavedRow() {
            // when
            handler.handle(command("곧 출발해요"));

            // then -> ID·보낸 시각은 저장이 만들고, 새 메시지라 고친 적도 지운 적도 없다
            verify(roomChatAssembler).assemble(new RoomChatRow(
                    CHAT_ID, ROOM_ID, SENDER, "곧 출발해요", null, null, SENT_AT));
        }

        @Test
        @DisplayName("조립한 채팅을 ROOM_CHAT_SENT로 발행하고 보낸 사람은 받는 사람에서 뺀다")
        void publishesRoomChatSentExcludingSender() {
            // when
            handler.handle(command("곧 출발해요"));

            // then -> 이벤트 data는 응답과 같은 값이다. 커밋 후 발행은 디스패처가 맡는다
            ArgumentCaptor<MatchRoomChangedEvent> published = ArgumentCaptor.forClass(MatchRoomChangedEvent.class);
            verify(eventPublisher).publishEvent(published.capture());
            MatchStreamEvent event = published.getValue().event();
            assertThat(event.type()).isEqualTo(MatchEventType.ROOM_CHAT_SENT);
            assertThat(event.runningRoomId()).isEqualTo(ROOM_ID);
            assertThat(event.chat()).isEqualTo(ASSEMBLED);
            assertThat(event.excludedUserId()).isEqualTo(new UserId(SENDER));
        }
    }

    @Nested
    @DisplayName("판정에 막힘 테스트")
    class RejectedTest {

        @Test
        @DisplayName("방 밖이면 404를 그대로 던지고 저장·조립·발행을 하지 않는다")
        void notInRoom() {
            // given
            willThrow(new RoomChatRoomNotFoundException())
                    .given(roomChatAccessPolicy).requireOpen(new UserId(SENDER), ROOM_ID);

            // when & then
            assertThatThrownBy(() -> handler.handle(command("안녕하세요")))
                    .isInstanceOf(RoomChatRoomNotFoundException.class);
            verifyNoInteractions(createRoomChatPort, roomChatAssembler, eventPublisher);
        }

        @Test
        @DisplayName("채팅이 닫혔으면 409를 그대로 던지고 저장·조립·발행을 하지 않는다")
        void closed() {
            // given
            willThrow(new RoomChatClosedException())
                    .given(roomChatAccessPolicy).requireOpen(new UserId(SENDER), ROOM_ID);

            // when & then
            assertThatThrownBy(() -> handler.handle(command("안녕하세요")))
                    .isInstanceOf(RoomChatClosedException.class);
            verifyNoInteractions(createRoomChatPort, roomChatAssembler, eventPublisher);
        }
    }
}
