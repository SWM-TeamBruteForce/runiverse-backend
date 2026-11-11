package com.runiverse.running_service.application.match.command.roomchatedit;

import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomChatAccessPolicy;
import com.runiverse.running_service.application.match.common.RoomChatAssembler;
import com.runiverse.running_service.application.match.exception.RoomChatNotFoundException;
import com.runiverse.running_service.application.match.port.in.EditRoomChatUsecase;
import com.runiverse.running_service.application.match.port.out.CreateRoomChatHistoryPort;
import com.runiverse.running_service.application.match.port.out.LoadRoomChatPort;
import com.runiverse.running_service.application.match.port.out.LockedRoomChat;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.port.out.UpdateRoomChatPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.RunningRoomChatHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 판정 순서는 방(404 → 409) → 메시지(404)다 — 러닝이 시작된 방이면 메시지가 남의 것이어도 409다.
// 메시지 행만 잠근다. 잠금은 커밋까지 이어져 이력 INSERT와 내용 UPDATE 사이에 다른 수정이 끼지 못한다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EditRoomChatHandler implements EditRoomChatUsecase {

    private final RoomChatAccessPolicy roomChatAccessPolicy;
    private final LoadRoomChatPort loadRoomChatPort;
    private final CreateRoomChatHistoryPort createRoomChatHistoryPort;
    private final UpdateRoomChatPort updateRoomChatPort;
    private final RoomChatAssembler roomChatAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public RoomChat handle(EditRoomChatCommand command) {
        UserId editorId = new UserId(command.userId());
        roomChatAccessPolicy.requireOpen(editorId, command.runningRoomId());

        // 없음·지움·남의 것을 가르지 않고 404다. 도메인도 같은 조건을 막지만 도메인 예외는 500으로 나간다
        LockedRoomChat locked = loadRoomChatPort.loadForUpdate(command.runningRoomId(), command.chatId())
                .filter(row -> !row.chat().isDeleted())
                .filter(row -> row.chat().isSentBy(editorId))
                .orElseThrow(RoomChatNotFoundException::new);
        RunningRoomChat chat = locked.chat();
        RunningRoomChatHistory history = chat.edit(editorId, command.content(), LocalDateTime.now());
        // 이력이 먼저다 — 저장이 실패하면 내용만 바뀌고 이력이 없는 상태가 남지 않게 함께 롤백된다
        createRoomChatHistoryPort.create(history);
        updateRoomChatPort.update(chat);

        // 고친 행과 같은 모양으로 조립한다 — 목록에서 다시 읽어도 같은 값이 나온다
        RoomChat edited = roomChatAssembler.assemble(new RoomChatRow(
                command.chatId(),
                command.runningRoomId(),
                command.userId(),
                chat.getContent().value(),
                chat.getEditedAt().orElseThrow(),
                null,
                locked.createdAt()));
        // 커밋 후 방의 다른 참가자에게 — 고친 사람은 이 응답으로 화면을 바꾼다
        eventPublisher.publishEvent(new MatchRoomChangedEvent(MatchStreamEvent.roomChatEdited(edited)));
        // 내용은 남기지 않는다 — 원문은 수정 이력 테이블이 갖는다(erd running_room_chat_histories)
        log.info("[매칭] 채팅 수정 성공 - runningRoomId={}, chatId={}", command.runningRoomId(), command.chatId());
        return edited;
    }
}
