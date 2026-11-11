package com.runiverse.running_service.application.match.command.roomchatdelete;

import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomChatAccessPolicy;
import com.runiverse.running_service.application.match.common.RoomChatAssembler;
import com.runiverse.running_service.application.match.exception.RoomChatNotFoundException;
import com.runiverse.running_service.application.match.port.in.DeleteRoomChatUsecase;
import com.runiverse.running_service.application.match.port.out.LoadRoomChatPort;
import com.runiverse.running_service.application.match.port.out.LockedRoomChat;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.port.out.UpdateRoomChatPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 판정 순서는 수정과 같다 — 방(404 → 409) → 메시지(404). 이미 지운 메시지도 404다.
// 이력은 남기지 않는다 — 행에 마지막 내용이 남고 시각은 deleted_at이 말한다(erd running_room_chats)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeleteRoomChatHandler implements DeleteRoomChatUsecase {

    private final RoomChatAccessPolicy roomChatAccessPolicy;
    private final LoadRoomChatPort loadRoomChatPort;
    private final UpdateRoomChatPort updateRoomChatPort;
    private final RoomChatAssembler roomChatAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(DeleteRoomChatCommand command) {
        UserId requesterId = new UserId(command.userId());
        roomChatAccessPolicy.requireOpen(requesterId, command.runningRoomId());

        // 없음·이미 지움·남의 것을 가르지 않고 404다. 도메인도 같은 조건을 막지만 도메인 예외는 500으로 나간다.
        // 잠가 읽어 동시에 온 수정과 겹치지 않는다 — 수정이 먼저 커밋하면 고친 내용 그대로 지워진다
        LockedRoomChat locked = loadRoomChatPort.loadForUpdate(command.runningRoomId(), command.chatId())
                .filter(row -> !row.chat().isDeleted())
                .filter(row -> row.chat().isSentBy(requesterId))
                .orElseThrow(RoomChatNotFoundException::new);
        RunningRoomChat chat = locked.chat();
        chat.delete(requesterId, LocalDateTime.now());
        updateRoomChatPort.update(chat);

        // 지운 행 그대로 조립한다 — 내용을 비우는 건 조립기의 몫이라 목록과 같은 모양(content null, isDeleted true)이 된다
        RoomChat deleted = roomChatAssembler.assemble(new RoomChatRow(
                command.chatId(),
                command.runningRoomId(),
                command.userId(),
                chat.getContent().value(),
                chat.getEditedAt().orElse(null),
                chat.getDeletedAt().orElseThrow(),
                locked.createdAt()));
        // 커밋 후 방의 다른 참가자에게 — 지운 사람은 204를 받고 자기 화면을 바꾼다
        eventPublisher.publishEvent(new MatchRoomChangedEvent(MatchStreamEvent.roomChatDeleted(deleted)));
        log.info("[매칭] 채팅 삭제 성공 - roomId={}, chatId={}", command.runningRoomId(), command.chatId());
    }
}
