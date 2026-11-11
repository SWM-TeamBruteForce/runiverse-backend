package com.runiverse.running_service.application.match.command.roomchatsend;

import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomChatAccessPolicy;
import com.runiverse.running_service.application.match.common.RoomChatAssembler;
import com.runiverse.running_service.application.match.port.in.SendRoomChatUsecase;
import com.runiverse.running_service.application.match.port.out.CreateRoomChatPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.port.out.SavedRoomChat;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 판정(방 안 404 → 열림 409)은 정책이, 화면용 모양은 조립기가 맡는다 — 목록과 같은 규칙을 쓴다.
// 방을 잠그지 않아 시작 스케줄러가 STARTED로 바꾸는 바로 그 순간에 보낸 한 건은 통과할 수 있다 — 막을 이유가 없는 경계다
@Service
@RequiredArgsConstructor
@Transactional
public class SendRoomChatHandler implements SendRoomChatUsecase {

    private final RoomChatAccessPolicy roomChatAccessPolicy;
    private final CreateRoomChatPort createRoomChatPort;
    private final RoomChatAssembler roomChatAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public RoomChat handle(SendRoomChatCommand command) {
        UserId senderId = new UserId(command.userId());
        roomChatAccessPolicy.requireOpen(senderId, command.runningRoomId());

        RunningRoomChat chat = RunningRoomChat.send(
                new RunningRoomId(command.runningRoomId()), senderId, command.content());
        SavedRoomChat saved = createRoomChatPort.create(chat);
        // 방금 저장한 행과 같은 모양으로 조립한다 — 목록에서 다시 읽어도 같은 값이 나온다
        RoomChat sent = roomChatAssembler.assemble(new RoomChatRow(
                saved.chatId(),
                command.runningRoomId(),
                command.userId(),
                chat.getContent().value(),
                null,
                null,
                saved.createdAt()));
        // 커밋 후 방의 다른 참가자에게 — 보낸 사람은 이 응답으로 화면을 바꾼다
        eventPublisher.publishEvent(new MatchRoomChangedEvent(MatchStreamEvent.roomChatSent(sent)));
        return sent;
    }
}
