package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.common.RoomChatAccessPolicy;
import com.runiverse.running_service.application.match.common.RoomChatAssembler;
import com.runiverse.running_service.application.match.exception.InvalidRoomChatCursorException;
import com.runiverse.running_service.application.match.exception.RoomChatClosedException;
import com.runiverse.running_service.application.match.exception.RoomChatRoomNotFoundException;
import com.runiverse.running_service.application.match.port.out.LoadRoomChatsPort;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatPage;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.query.roomchatlist.GetRoomChatsHandler;
import com.runiverse.running_service.application.match.query.roomchatlist.GetRoomChatsQuery;
import com.runiverse.running_service.application.match.query.roomchatlist.GetRoomChatsResult;
import com.runiverse.running_service.application.match.query.roomchatlist.RoomChatCursor;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

// 판정은 RoomChatAccessPolicyTest, 지운 메시지·프로필 모양은 RoomChatAssemblerTest가 본다 —
// 여기는 커서 → 판정 → 한 페이지 → 조립 → 다음 커서의 흐름을 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("방 채팅 목록 조회 단위 테스트")
class GetRoomChatsHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final int LIMIT = 50;
    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 11, 18, 52, 10);
    private static final RoomChatRow ROW = new RoomChatRow(3021L, ROOM_ID, CHULSU, "곧 출발해요", null, null, SENT_AT);
    private static final RoomChat CHAT = new RoomChat(3021L, ROOM_ID,
            new RoomChat.Sender(CHULSU, "철수", null, false), "곧 출발해요", false, null, SENT_AT);

    @Mock
    private RoomChatAccessPolicy roomChatAccessPolicy;

    @Mock
    private LoadRoomChatsPort loadRoomChatsPort;

    @Mock
    private RoomChatAssembler roomChatAssembler;

    private GetRoomChatsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetRoomChatsHandler(roomChatAccessPolicy, loadRoomChatsPort, roomChatAssembler);
    }

    private static GetRoomChatsQuery query(String cursor) {
        return new GetRoomChatsQuery(ME, ROOM_ID, cursor, LIMIT);
    }

    @Nested
    @DisplayName("페이지 테스트")
    class PageTest {

        @Test
        @DisplayName("첫 페이지는 기준 없이 읽어 조립하고 다음 페이지 커서를 준다")
        void firstPage() {
            // given -> 더 오래된 메시지가 남아 있다
            given(loadRoomChatsPort.loadPage(ROOM_ID, null, LIMIT)).willReturn(new RoomChatPage(List.of(ROW), 3021L));
            given(roomChatAssembler.assemble(List.of(ROW))).willReturn(List.of(CHAT));

            // when
            GetRoomChatsResult result = handler.handle(query(null));

            // then -> 판정이 먼저다. 방 밖이면 남의 방 채팅을 읽지 않는다
            InOrder order = inOrder(roomChatAccessPolicy, loadRoomChatsPort);
            order.verify(roomChatAccessPolicy).requireOpen(new UserId(ME), ROOM_ID);
            order.verify(loadRoomChatsPort).loadPage(ROOM_ID, null, LIMIT);
            assertThat(result.items()).containsExactly(CHAT);
            assertThat(RoomChatCursor.decode(result.nextCursor()).beforeId()).isEqualTo(3021L);
        }

        @Test
        @DisplayName("커서가 있으면 그 ID보다 오래된 메시지부터 읽는다")
        void nextPage() {
            // given
            given(loadRoomChatsPort.loadPage(ROOM_ID, 3021L, LIMIT)).willReturn(new RoomChatPage(List.of(ROW), null));
            given(roomChatAssembler.assemble(List.of(ROW))).willReturn(List.of(CHAT));

            // when
            GetRoomChatsResult result = handler.handle(query(new RoomChatCursor(3021L).encode()));

            // then -> 방의 첫 메시지까지 다 받았다
            assertThat(result.items()).containsExactly(CHAT);
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("메시지가 하나도 없는 방은 빈 목록과 null 커서다")
        void emptyRoom() {
            // given
            given(loadRoomChatsPort.loadPage(ROOM_ID, null, LIMIT)).willReturn(new RoomChatPage(List.of(), null));
            given(roomChatAssembler.assemble(List.of())).willReturn(List.of());

            // when
            GetRoomChatsResult result = handler.handle(query(null));

            // then
            assertThat(result.items()).isEmpty();
            assertThat(result.nextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("거절 테스트")
    class RejectedTest {

        @Test
        @DisplayName("깨진 커서는 방 판정보다 먼저 400 계열 예외다")
        void brokenCursorComesFirst() {
            // when & then -> 요청 형식 오류라 방에 대해 아무것도 알려 주지 않는다
            assertThatThrownBy(() -> handler.handle(query("!!!")))
                    .isInstanceOf(InvalidRoomChatCursorException.class);
            verifyNoInteractions(roomChatAccessPolicy, loadRoomChatsPort, roomChatAssembler);
        }

        @Test
        @DisplayName("방 밖이면 404를 그대로 던지고 채팅을 읽지 않는다")
        void notInRoom() {
            // given
            willThrow(new RoomChatRoomNotFoundException())
                    .given(roomChatAccessPolicy).requireOpen(new UserId(ME), ROOM_ID);

            // when & then
            assertThatThrownBy(() -> handler.handle(query(null)))
                    .isInstanceOf(RoomChatRoomNotFoundException.class);
            verifyNoInteractions(loadRoomChatsPort, roomChatAssembler);
        }

        @Test
        @DisplayName("러닝이 시작됐으면 409를 그대로 던지고 채팅을 읽지 않는다")
        void closed() {
            // given
            willThrow(new RoomChatClosedException())
                    .given(roomChatAccessPolicy).requireOpen(new UserId(ME), ROOM_ID);

            // when & then
            assertThatThrownBy(() -> handler.handle(query(null)))
                    .isInstanceOf(RoomChatClosedException.class);
            verifyNoInteractions(loadRoomChatsPort, roomChatAssembler);
        }
    }
}
