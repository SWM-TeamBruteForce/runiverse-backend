package com.runiverse.running_service.application.match.query.roomchatlist;

import com.runiverse.running_service.application.match.common.RoomChatAccessPolicy;
import com.runiverse.running_service.application.match.common.RoomChatAssembler;
import com.runiverse.running_service.application.match.port.in.GetRoomChatsUsecase;
import com.runiverse.running_service.application.match.port.out.LoadRoomChatsPort;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatPage;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 늦게 들어온 사람도 방의 첫 메시지까지 본다 — 들어온 시점으로 자르지 않는다.
// 지운 메시지도 자리를 지키고, 내용은 조립기가 숨긴다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRoomChatsHandler implements GetRoomChatsUsecase {

    private final RoomChatAccessPolicy roomChatAccessPolicy;
    private final LoadRoomChatsPort loadRoomChatsPort;
    private final RoomChatAssembler roomChatAssembler;

    @Override
    public GetRoomChatsResult handle(GetRoomChatsQuery query) {
        // 1. 커서를 먼저 푼다 — 깨진 값은 요청이 틀린 것(400)이라 방 판정보다 앞이다
        Long beforeId = query.cursor() == null ? null : RoomChatCursor.decode(query.cursor()).beforeId();
        // 2. 방 안인가(404) → 채팅이 열려 있나(409) — 보내기와 같은 판정이다
        roomChatAccessPolicy.requireOpen(new UserId(query.userId()), query.runningRoomId());
        // 3. 한 페이지를 읽어 화면용으로 만든다 — 순서는 어댑터가 정한 최신순 그대로다
        RoomChatPage page = loadRoomChatsPort.loadPage(query.runningRoomId(), beforeId, query.limit());
        List<RoomChat> items = roomChatAssembler.assemble(page.rows());
        String nextCursor = page.nextBeforeId() == null ? null : new RoomChatCursor(page.nextBeforeId()).encode();
        return new GetRoomChatsResult(items, nextCursor);
    }
}
