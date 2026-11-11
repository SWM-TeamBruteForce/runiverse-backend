package com.runiverse.running_service.integration_test.match;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.match.command.roomchatdelete.DeleteRoomChatCommand;
import com.runiverse.running_service.application.match.command.roomchatdelete.DeleteRoomChatHandler;
import com.runiverse.running_service.application.match.command.roomchatedit.EditRoomChatCommand;
import com.runiverse.running_service.application.match.command.roomchatedit.EditRoomChatHandler;
import com.runiverse.running_service.application.match.command.roomchatsend.SendRoomChatCommand;
import com.runiverse.running_service.application.match.command.roomchatsend.SendRoomChatHandler;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomChatAccessPolicy;
import com.runiverse.running_service.application.match.common.RoomChatAssembler;
import com.runiverse.running_service.application.match.exception.RoomChatClosedException;
import com.runiverse.running_service.application.match.exception.RoomChatNotFoundException;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.query.roomchatlist.GetRoomChatsHandler;
import com.runiverse.running_service.application.match.query.roomchatlist.GetRoomChatsQuery;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryRoomChatStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 대기방 채팅 보내기·수정·삭제·목록을 운영 핸들러·정책·조립기 그대로 엮어 흐름을 본다(api-spec 5-E).
// 바깥 경계만 대신한다 — 채팅 두 표는 메모리 저장소, 방 안 판정의 근거(신청·배정·방 상태)는 고정한 상태 행,
// 프로필은 고정값, 커밋 후 Redis 발행은 이벤트를 쌓아 두는 것으로 본다
@DisplayName("대기방 채팅 통합 테스트")
class RoomChatIntegrationTest {

    private static final long ROOM_ID = 125L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 12, 20, 0);

    private final UUID chulsu = UuidCreator.getTimeOrderedEpoch();
    private final UUID younghee = UuidCreator.getTimeOrderedEpoch();

    private final Map<UUID, UserStatusRow> statuses = new HashMap<>();
    private final List<MatchStreamEvent> published = new ArrayList<>();
    private InMemoryRoomChatStore store;
    private SendRoomChatHandler sendHandler;
    private EditRoomChatHandler editHandler;
    private DeleteRoomChatHandler deleteHandler;
    private GetRoomChatsHandler listHandler;

    @BeforeEach
    void setUp() {
        store = new InMemoryRoomChatStore();
        RoomChatAccessPolicy policy = new RoomChatAccessPolicy(
                userId -> Optional.ofNullable(statuses.get(userId.value())));
        Map<UUID, PlayerProfile> profiles = List.of(
                        new PlayerProfile(chulsu, "철수", null, null),
                        new PlayerProfile(younghee, "영희", null, null)).stream()
                .collect(Collectors.toMap(PlayerProfile::userId, Function.identity()));
        RoomChatAssembler assembler = new RoomChatAssembler(
                userIds -> userIds.stream().filter(profiles::containsKey)
                        .collect(Collectors.toMap(Function.identity(), profiles::get)),
                new FakeViewUrlGenerator());
        org.springframework.context.ApplicationEventPublisher publisher = event -> {
            if (event instanceof MatchRoomChangedEvent changed) {
                published.add(changed.event());
            }
        };
        sendHandler = new SendRoomChatHandler(policy, store, assembler, publisher);
        editHandler = new EditRoomChatHandler(policy, store, store, store, assembler, publisher);
        deleteHandler = new DeleteRoomChatHandler(policy, store, store, assembler, publisher);
        listHandler = new GetRoomChatsHandler(policy, store, assembler);
        inRoom(chulsu, RunningRoomStatus.MATCHING);
        inRoom(younghee, RunningRoomStatus.MATCHING);
    }

    private void inRoom(UUID userId, RunningRoomStatus roomStatus) {
        statuses.put(userId, new UserStatusRow(ROOM_ID, RunningRoomType.MATCH, roomStatus, START, 5_000));
    }

    private RoomChat send(UUID userId, String content) {
        return sendHandler.handle(new SendRoomChatCommand(userId, ROOM_ID, content));
    }

    private List<RoomChat> list(UUID userId) {
        return listHandler.handle(new GetRoomChatsQuery(userId, ROOM_ID, null, 50)).items();
    }

    @Test
    @DisplayName("두 번 고치면 이력이 고친 순서대로 직전 내용을 남기고, 목록·응답·이벤트가 같은 값을 보인다")
    void editTwiceKeepsEveryPreviousContent() {
        // given
        RoomChat sent = send(chulsu, "곧 출발해요");

        // when
        editHandler.handle(new EditRoomChatCommand(chulsu, ROOM_ID, sent.chatId(), "조금 늦어요"));
        RoomChat edited = editHandler.handle(new EditRoomChatCommand(chulsu, ROOM_ID, sent.chatId(), "5분 늦어요"));

        // then -> 이력을 시간순으로 읽고 행의 content를 붙이면 처음부터 끝까지 복원된다(erd)
        assertThat(store.histories()).containsExactly(
                new InMemoryRoomChatStore.History(sent.chatId(), "곧 출발해요"),
                new InMemoryRoomChatStore.History(sent.chatId(), "조금 늦어요"));
        assertThat(edited.content()).isEqualTo("5분 늦어요");
        assertThat(edited.editedAt()).isNotNull();
        assertThat(edited.createdAt()).isEqualTo(sent.createdAt());
        // 영희가 목록으로 받아도 같은 값이다
        assertThat(list(younghee)).containsExactly(edited);
        assertThat(published).extracting(MatchStreamEvent::type).containsExactly(
                MatchEventType.ROOM_CHAT_SENT, MatchEventType.ROOM_CHAT_EDITED, MatchEventType.ROOM_CHAT_EDITED);
        assertThat(published.getLast().chat()).isEqualTo(edited);
        assertThat(published.getLast().excludedUserId()).isEqualTo(new UserId(chulsu));
    }

    @Test
    @DisplayName("지우면 목록에 자리를 지키며 내용 없이 보이고, 원문은 행에 남고, 다시 고치거나 지우면 404다")
    void deleteKeepsPlaceAndBlocksFurtherChanges() {
        // given
        RoomChat sent = send(chulsu, "곧 출발해요");
        send(younghee, "네 좋아요");

        // when
        deleteHandler.handle(new DeleteRoomChatCommand(chulsu, ROOM_ID, sent.chatId()));

        // then -> 지운 메시지도 자리를 지킨다(5-E-1)
        List<RoomChat> items = list(younghee);
        assertThat(items).extracting(RoomChat::chatId).containsExactly(sent.chatId() + 1, sent.chatId());
        RoomChat deleted = items.getLast();
        assertThat(deleted.content()).isNull();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(store.storedContent(sent.chatId())).isEqualTo("곧 출발해요");
        // 이벤트도 목록과 같은 모양이다
        assertThat(published.getLast().type()).isEqualTo(MatchEventType.ROOM_CHAT_DELETED);
        assertThat(published.getLast().chat()).isEqualTo(deleted);

        // then -> 되살리거나 고칠 수 없다
        assertThatThrownBy(() -> editHandler.handle(new EditRoomChatCommand(chulsu, ROOM_ID, sent.chatId(), "다시")))
                .isInstanceOf(RoomChatNotFoundException.class);
        assertThatThrownBy(() -> deleteHandler.handle(new DeleteRoomChatCommand(chulsu, ROOM_ID, sent.chatId())))
                .isInstanceOf(RoomChatNotFoundException.class);
        assertThat(store.histories()).isEmpty();
    }

    @Test
    @DisplayName("남의 메시지·다른 방 메시지는 고치거나 지울 수 없어 404이고 아무것도 바뀌지 않는다")
    void othersAndOtherRoomChatsAreNotFound() {
        // given
        RoomChat sent = send(chulsu, "곧 출발해요");
        int eventsBefore = published.size();

        // when & then -> 남의 것
        assertThatThrownBy(() -> editHandler.handle(new EditRoomChatCommand(younghee, ROOM_ID, sent.chatId(), "바꿈")))
                .isInstanceOf(RoomChatNotFoundException.class);
        assertThatThrownBy(() -> deleteHandler.handle(new DeleteRoomChatCommand(younghee, ROOM_ID, sent.chatId())))
                .isInstanceOf(RoomChatNotFoundException.class);

        // when & then -> 철수가 다른 방에 배정돼 그 방 번호로 이 메시지를 가리킨다
        statuses.put(chulsu, new UserStatusRow(130L, RunningRoomType.MATCH, RunningRoomStatus.MATCHING, START, 5_000));
        assertThatThrownBy(() -> editHandler.handle(new EditRoomChatCommand(chulsu, 130L, sent.chatId(), "바꿈")))
                .isInstanceOf(RoomChatNotFoundException.class);

        // then
        assertThat(store.storedContent(sent.chatId())).isEqualTo("곧 출발해요");
        assertThat(store.histories()).isEmpty();
        assertThat(published).hasSize(eventsBefore);
    }

    @Test
    @DisplayName("러닝이 시작되면 내 메시지라도 고치거나 지울 수 없어 409다")
    void startedRoomClosesEditAndDelete() {
        // given
        RoomChat sent = send(chulsu, "곧 출발해요");

        // when - 시작 스케줄러가 방을 STARTED로 올렸다
        inRoom(chulsu, RunningRoomStatus.STARTED);

        // then
        assertThatThrownBy(() -> editHandler.handle(new EditRoomChatCommand(chulsu, ROOM_ID, sent.chatId(), "바꿈")))
                .isInstanceOf(RoomChatClosedException.class);
        assertThatThrownBy(() -> deleteHandler.handle(new DeleteRoomChatCommand(chulsu, ROOM_ID, sent.chatId())))
                .isInstanceOf(RoomChatClosedException.class);
        assertThat(store.storedContent(sent.chatId())).isEqualTo("곧 출발해요");
    }
}
