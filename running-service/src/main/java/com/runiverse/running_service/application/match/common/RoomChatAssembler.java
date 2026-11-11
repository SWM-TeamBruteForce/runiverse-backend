package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// 저장된 채팅 행을 화면에 그릴 Chat으로 만든다 — 목록·보내기(그리고 이후 수정·삭제·이벤트)가 이 한 곳을 거쳐
// 지운 메시지의 원문이 어느 경로로도 새 나가지 않는다.
// 보낸 사람 프로필은 한 번에 읽는다 — 목록 한 페이지에 같은 사람이 여러 번 나와도 조회는 한 번이다
@Component
@RequiredArgsConstructor
public class RoomChatAssembler {

    private static final String DELETED_NICKNAME = "탈퇴한 사용자";

    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final GenerateViewUrlPort generateViewUrlPort;

    // 입력 순서를 지킨다 — 정렬은 읽은 쪽(어댑터)이 정했다
    public List<RoomChat> assemble(List<RoomChatRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> senderIds = rows.stream().map(RoomChatRow::senderId).distinct().toList();
        Map<UUID, PlayerProfile> profiles = loadPlayerProfilesPort.loadProfiles(senderIds);
        return rows.stream().map(row -> toChat(row, profiles)).toList();
    }

    public RoomChat assemble(RoomChatRow row) {
        return assemble(List.of(row)).getFirst();
    }

    private RoomChat toChat(RoomChatRow row, Map<UUID, PlayerProfile> profiles) {
        return new RoomChat(
                row.chatId(),
                row.runningRoomId(),
                senderOf(row.senderId(), profiles.get(row.senderId())),
                // 지운 메시지는 자리만 남긴다 — 원문은 행에 있지만 API로는 내리지 않는다
                row.isDeleted() ? null : row.content(),
                row.isDeleted(),
                row.editedAt(),
                row.createdAt());
    }

    // 탈퇴하면 users 행이 지워져 프로필 조회에서 빠진다 — 다른 조립과 같은 탈퇴 형식을 낸다
    private RoomChat.Sender senderOf(UUID userId, PlayerProfile profile) {
        if (profile == null) {
            return new RoomChat.Sender(userId, DELETED_NICKNAME, null, true);
        }
        String profileImageUrl = profile.profileImageKey() == null
                ? null
                : generateViewUrlPort.generate(profile.profileImageKey());
        return new RoomChat.Sender(userId, profile.nickname(), profileImageUrl, false);
    }
}
