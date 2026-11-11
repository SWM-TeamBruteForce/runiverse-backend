package com.runiverse.running_service.application.match.command.roomchatsend;

import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.exception.RoomChatClosedException;
import com.runiverse.running_service.application.match.exception.RoomChatRoomNotFoundException;
import com.runiverse.running_service.application.match.port.in.SendRoomChatUsecase;
import com.runiverse.running_service.application.match.port.out.CreateRoomChatPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.SavedRoomChat;
import com.runiverse.running_service.application.running.port.out.LoadUserStatusPort;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// 판정 순서는 명세 5-E를 따른다 — 방 안인가(404) → 채팅이 열려 있나(409). 먼저 걸린 이유 하나만 알린다.
// 방을 잠그지 않는다 — 채팅은 방 상태를 바꾸지 않아 지킬 불변식이 없고,
// 채팅마다 방 행을 잡으면 참가·이탈과 서로 기다린다(RunningRoomChat 주석).
// 그래서 시작 스케줄러가 STARTED로 바꾸는 바로 그 순간에 보낸 한 건은 통과할 수 있다 — 막을 이유가 없는 경계다
@Service
@RequiredArgsConstructor
@Transactional
public class SendRoomChatHandler implements SendRoomChatUsecase {

    private static final String DELETED_NICKNAME = "탈퇴한 사용자";

    private final LoadUserStatusPort loadUserStatusPort;
    private final CreateRoomChatPort createRoomChatPort;
    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final GenerateViewUrlPort generateViewUrlPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public RoomChat handle(SendRoomChatCommand command) {
        UserId senderId = new UserId(command.userId());
        // 1. 이 방에 배정돼 있나 — 배정이 없거나, 다른 방에 배정됐거나, 솔로 방이면 셋 다 404다.
        //    배정은 유저당 하나라 "내 배정 방 = 요청한 방"이면 방 존재도 함께 확인된다
        UserStatusRow status = loadUserStatusPort.loadStatus(senderId)
                .filter(row -> row.runningRoomId().equals(command.runningRoomId()))
                .filter(row -> row.type() != RunningRoomType.SOLO)
                .orElseThrow(RoomChatRoomNotFoundException::new);
        // 2. 채팅이 열려 있나 — 모집 중·확정 후 대기만 된다. 이 동안의 활성 신청은 늘 JOINED라 따로 보지 않는다
        if (!isChatOpen(status.roomStatus())) {
            throw new RoomChatClosedException();
        }
        RunningRoomChat chat = RunningRoomChat.send(
                new RunningRoomId(command.runningRoomId()), senderId, command.content());
        SavedRoomChat saved = createRoomChatPort.create(chat);
        RoomChat sent = new RoomChat(
                saved.chatId(),
                command.runningRoomId(),
                senderOf(command.userId()),
                chat.getContent().value(),
                false,
                null,
                saved.createdAt());
        // 커밋 후 방의 다른 참가자에게 — 보낸 사람은 이 응답으로 화면을 바꾼다
        eventPublisher.publishEvent(new MatchRoomChangedEvent(MatchStreamEvent.roomChatSent(sent)));
        return sent;
    }

    private static boolean isChatOpen(RunningRoomStatus roomStatus) {
        return roomStatus == RunningRoomStatus.MATCHING || roomStatus == RunningRoomStatus.MATCHED;
    }

    // 방금 인증을 통과한 사람이지만 탈퇴 처리와 겹칠 수 있다 — 다른 조립과 같은 규칙으로 탈퇴 형식을 낸다
    private RoomChat.Sender senderOf(UUID userId) {
        PlayerProfile profile = loadPlayerProfilesPort.loadProfiles(List.of(userId)).get(userId);
        if (profile == null) {
            return new RoomChat.Sender(userId, DELETED_NICKNAME, null, true);
        }
        String profileImageUrl = profile.profileImageKey() == null
                ? null
                : generateViewUrlPort.generate(profile.profileImageKey());
        return new RoomChat.Sender(userId, profile.nickname(), profileImageUrl, false);
    }
}
