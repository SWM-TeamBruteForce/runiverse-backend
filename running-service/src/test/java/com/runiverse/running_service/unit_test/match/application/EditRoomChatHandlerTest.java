package com.runiverse.running_service.unit_test.match.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.roomchatedit.EditRoomChatCommand;
import com.runiverse.running_service.application.match.command.roomchatedit.EditRoomChatHandler;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomChatAccessPolicy;
import com.runiverse.running_service.application.match.common.RoomChatAssembler;
import com.runiverse.running_service.application.match.exception.RoomChatClosedException;
import com.runiverse.running_service.application.match.exception.RoomChatNotFoundException;
import com.runiverse.running_service.application.match.port.out.CreateRoomChatHistoryPort;
import com.runiverse.running_service.application.match.port.out.LoadRoomChatPort;
import com.runiverse.running_service.application.match.port.out.LockedRoomChat;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.port.out.UpdateRoomChatPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.RunningRoomChatHistory;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 방 판정은 RoomChatAccessPolicyTest, 화면용 모양은 RoomChatAssemblerTest, 바꾸기 규칙은 도메인 테스트가 본다 —
// 여기는 방 판정 → 잠가 읽기 → 메시지 404 → 이력 → 쓰기 → 발행의 흐름과, 막히면 아무것도 남기지 않는지만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("방 채팅 수정 단위 테스트")
class EditRoomChatHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final long CHAT_ID = 3021L;
    private static final UUID SENDER = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OTHER = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 11, 18, 52, 10);
    private static final RoomChat ASSEMBLED = new RoomChat(CHAT_ID, ROOM_ID,
            new RoomChat.Sender(SENDER, "철수", null, false),
            "조금 늦어요", false, SENT_AT.plusMinutes(2), SENT_AT);

    @Mock
    private RoomChatAccessPolicy roomChatAccessPolicy;

    @Mock
    private LoadRoomChatPort loadRoomChatPort;

    @Mock
    private CreateRoomChatHistoryPort createRoomChatHistoryPort;

    @Mock
    private UpdateRoomChatPort updateRoomChatPort;

    @Mock
    private RoomChatAssembler roomChatAssembler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EditRoomChatHandler handler;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        handler = new EditRoomChatHandler(roomChatAccessPolicy, loadRoomChatPort, createRoomChatHistoryPort,
                updateRoomChatPort, roomChatAssembler, eventPublisher);
        log = LogCapture.of(EditRoomChatHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    private static EditRoomChatCommand command(UUID editor) {
        return new EditRoomChatCommand(editor, ROOM_ID, CHAT_ID, "조금 늦어요");
    }

    private static LockedRoomChat locked(LocalDateTime deletedAt) {
        return new LockedRoomChat(
                RunningRoomChat.restore(CHAT_ID, ROOM_ID, SENDER, "곧 출발해요", null, deletedAt), SENT_AT);
    }

    @Nested
    @DisplayName("성공 테스트")
    class SuccessTest {

        @Test
        @DisplayName("직전 내용을 이력으로 먼저 남기고 내용을 쓴 뒤 고친 채팅을 발행하고 돌려준다")
        void recordsHistoryThenUpdatesThenPublishes() {
            // given
            given(loadRoomChatPort.loadForUpdate(ROOM_ID, CHAT_ID)).willReturn(Optional.of(locked(null)));
            given(roomChatAssembler.assemble(any(RoomChatRow.class))).willReturn(ASSEMBLED);

            // when
            RoomChat result = handler.handle(command(SENDER));

            // then -> 이력이 먼저다. 같은 트랜잭션이라 어느 쪽이 실패해도 함께 롤백된다
            InOrder order = inOrder(roomChatAccessPolicy, loadRoomChatPort, createRoomChatHistoryPort,
                    updateRoomChatPort, eventPublisher);
            order.verify(roomChatAccessPolicy).requireOpen(new UserId(SENDER), ROOM_ID);
            order.verify(loadRoomChatPort).loadForUpdate(ROOM_ID, CHAT_ID);
            ArgumentCaptor<RunningRoomChatHistory> history = ArgumentCaptor.forClass(RunningRoomChatHistory.class);
            order.verify(createRoomChatHistoryPort).create(history.capture());
            ArgumentCaptor<RunningRoomChat> updated = ArgumentCaptor.forClass(RunningRoomChat.class);
            order.verify(updateRoomChatPort).update(updated.capture());
            ArgumentCaptor<MatchRoomChangedEvent> published = ArgumentCaptor.forClass(MatchRoomChangedEvent.class);
            order.verify(eventPublisher).publishEvent(published.capture());

            assertThat(history.getValue().getContent().value()).isEqualTo("곧 출발해요");
            assertThat(updated.getValue().getContent().value()).isEqualTo("조금 늦어요");
            assertThat(updated.getValue().isEdited()).isTrue();
            assertThat(published.getValue().event().type()).isEqualTo(MatchEventType.ROOM_CHAT_EDITED);
            assertThat(published.getValue().event().chat()).isEqualTo(ASSEMBLED);
            assertThat(result).isEqualTo(ASSEMBLED);
        }

        @Test
        @DisplayName("고친 행 그대로 조립한다 — 보낸 시각은 잠가 읽은 행에서, 수정 시각은 방금 찍은 값이다")
        void assemblesEditedRow() {
            // given
            given(loadRoomChatPort.loadForUpdate(ROOM_ID, CHAT_ID)).willReturn(Optional.of(locked(null)));
            given(roomChatAssembler.assemble(any(RoomChatRow.class))).willReturn(ASSEMBLED);
            LocalDateTime before = LocalDateTime.now();

            // when
            handler.handle(command(SENDER));

            // then
            ArgumentCaptor<RoomChatRow> row = ArgumentCaptor.forClass(RoomChatRow.class);
            verify(roomChatAssembler).assemble(row.capture());
            assertThat(row.getValue().chatId()).isEqualTo(CHAT_ID);
            assertThat(row.getValue().runningRoomId()).isEqualTo(ROOM_ID);
            assertThat(row.getValue().senderId()).isEqualTo(SENDER);
            assertThat(row.getValue().content()).isEqualTo("조금 늦어요");
            assertThat(row.getValue().editedAt()).isAfterOrEqualTo(before);
            assertThat(row.getValue().deletedAt()).isNull();
            assertThat(row.getValue().createdAt()).isEqualTo(SENT_AT);
        }

        @Test
        @DisplayName("성공하면 방과 메시지 ID만 남기고 내용은 남기지 않는다")
        void logsWithoutContent() {
            // given
            given(loadRoomChatPort.loadForUpdate(ROOM_ID, CHAT_ID)).willReturn(Optional.of(locked(null)));
            given(roomChatAssembler.assemble(any(RoomChatRow.class))).willReturn(ASSEMBLED);

            // when
            handler.handle(command(SENDER));

            // then -> 원문은 수정 이력 테이블이 갖는다
            assertThat(log.messages(Level.INFO)).containsExactly(
                    "[매칭] 채팅 수정 성공 - roomId=" + ROOM_ID + ", chatId=" + CHAT_ID);
        }
    }

    @Nested
    @DisplayName("실패 테스트")
    class FailureTest {

        @Test
        @DisplayName("방이 닫혔으면 메시지를 잠그기 전에 409로 멈춘다")
        void stopsBeforeLockWhenRoomClosed() {
            // given -> 러닝이 시작된 방이면 메시지가 남의 것이어도 409다
            willThrow(new RoomChatClosedException())
                    .given(roomChatAccessPolicy).requireOpen(new UserId(SENDER), ROOM_ID);

            // when & then
            assertThatThrownBy(() -> handler.handle(command(SENDER))).isInstanceOf(RoomChatClosedException.class);
            verifyNoInteractions(loadRoomChatPort, createRoomChatHistoryPort, updateRoomChatPort, eventPublisher);
        }

        @Test
        @DisplayName("이 방에 그 메시지가 없으면 404이고 아무것도 남기지 않는다")
        void notFoundWhenAbsent() {
            // given
            given(loadRoomChatPort.loadForUpdate(ROOM_ID, CHAT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> handler.handle(command(SENDER))).isInstanceOf(RoomChatNotFoundException.class);
            verifyNoInteractions(createRoomChatHistoryPort, updateRoomChatPort, eventPublisher);
        }

        @Test
        @DisplayName("이미 지운 메시지면 404이고 아무것도 남기지 않는다 — 지운 메시지는 고칠 수 없다")
        void notFoundWhenDeleted() {
            // given
            given(loadRoomChatPort.loadForUpdate(ROOM_ID, CHAT_ID))
                    .willReturn(Optional.of(locked(SENT_AT.plusMinutes(1))));

            // when & then -> 도메인 예외(500)가 아니라 404다
            assertThatThrownBy(() -> handler.handle(command(SENDER))).isInstanceOf(RoomChatNotFoundException.class);
            verifyNoInteractions(createRoomChatHistoryPort, updateRoomChatPort, eventPublisher);
        }

        @Test
        @DisplayName("남의 메시지면 404이고 아무것도 남기지 않는다 — 있는지 없는지를 알려 주지 않는다")
        void notFoundWhenNotSender() {
            // given
            given(loadRoomChatPort.loadForUpdate(ROOM_ID, CHAT_ID)).willReturn(Optional.of(locked(null)));

            // when & then
            assertThatThrownBy(() -> handler.handle(command(OTHER))).isInstanceOf(RoomChatNotFoundException.class);
            verifyNoInteractions(createRoomChatHistoryPort, updateRoomChatPort, eventPublisher);
        }
    }
}
