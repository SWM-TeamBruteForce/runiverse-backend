package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.match.port.out.CreateRoomChatHistoryPort;
import com.runiverse.running_service.application.match.port.out.CreateRoomChatPort;
import com.runiverse.running_service.application.match.port.out.LoadRoomChatPort;
import com.runiverse.running_service.application.match.port.out.LoadRoomChatsPort;
import com.runiverse.running_service.application.match.port.out.LockedRoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatPage;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.port.out.SavedRoomChat;
import com.runiverse.running_service.application.match.port.out.UpdateRoomChatPort;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.RunningRoomChatHistory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

// RoomChatPersistenceAdapter를 대신한다 — running_room_chats·running_room_chat_histories 두 표를 메모리에 든다.
// 잠금은 흉내 내지 않는다(한 스레드). ID·보낸 시각은 DB처럼 저장할 때 채운다
public class InMemoryRoomChatStore implements CreateRoomChatPort, LoadRoomChatsPort,
        LoadRoomChatPort, UpdateRoomChatPort, CreateRoomChatHistoryPort {

    private final Map<Long, Row> rows = new TreeMap<>();
    private final List<History> histories = new ArrayList<>();
    private long nextId = 1;
    private LocalDateTime clock = LocalDateTime.of(2026, 11, 11, 18, 0);

    private record Row(Long chatId, Long runningRoomId, UUID userId, String content,
                       LocalDateTime editedAt, LocalDateTime deletedAt, LocalDateTime createdAt) {
    }

    // 검증 전용 — 고쳐진 메시지와 고치기 직전 내용
    public record History(Long chatId, String content) {
    }

    @Override
    public SavedRoomChat create(RunningRoomChat chat) {
        clock = clock.plusSeconds(1);
        Row row = new Row(nextId++, chat.getRunningRoomId().value(), chat.getUserId().value(),
                chat.getContent().value(), null, null, clock);
        rows.put(row.chatId(), row);
        return new SavedRoomChat(row.chatId(), row.createdAt());
    }

    @Override
    public RoomChatPage loadPage(Long runningRoomId, Long beforeId, int limit) {
        List<RoomChatRow> fetched = rows.values().stream()
                .filter(row -> row.runningRoomId().equals(runningRoomId))
                .filter(row -> beforeId == null || row.chatId() < beforeId)
                .sorted(Comparator.comparing(Row::chatId).reversed())
                .limit(limit + 1L)
                .map(row -> new RoomChatRow(row.chatId(), row.runningRoomId(), row.userId(), row.content(),
                        row.editedAt(), row.deletedAt(), row.createdAt()))
                .toList();
        boolean hasNext = fetched.size() > limit;
        List<RoomChatRow> page = hasNext ? fetched.subList(0, limit) : fetched;
        return new RoomChatPage(page, hasNext ? page.getLast().chatId() : null);
    }

    @Override
    public Optional<LockedRoomChat> loadForUpdate(Long runningRoomId, Long chatId) {
        return Optional.ofNullable(rows.get(chatId))
                .filter(row -> row.runningRoomId().equals(runningRoomId))
                .map(row -> new LockedRoomChat(RunningRoomChat.restore(row.chatId(), row.runningRoomId(),
                        row.userId(), row.content(), row.editedAt(), row.deletedAt()), row.createdAt()));
    }

    @Override
    public void update(RunningRoomChat chat) {
        Row before = rows.get(chat.getRunningRoomChatId().orElseThrow().value());
        rows.put(before.chatId(), new Row(before.chatId(), before.runningRoomId(), before.userId(),
                chat.getContent().value(), chat.getEditedAt().orElse(null), chat.getDeletedAt().orElse(null),
                before.createdAt()));
    }

    @Override
    public void create(RunningRoomChatHistory history) {
        histories.add(new History(history.getRunningRoomChatId().value(), history.getContent().value()));
    }

    // 검증 전용 — 행에 남은 원문(지워도 남는다)
    public String storedContent(Long chatId) {
        return rows.get(chatId).content();
    }

    public List<History> histories() {
        return List.copyOf(histories);
    }
}
