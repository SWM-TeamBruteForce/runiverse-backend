package com.runiverse.running_service.application.match.command.stream;

import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.ActiveMatchNotFoundException;
import com.runiverse.running_service.application.match.port.in.OpenMatchStreamUsecase;
import com.runiverse.running_service.application.match.port.out.MatchRoomMembershipPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
// 스냅샷을 읽는 동안 커넥션을 잡고 있어야 한다 —
// 어댑터가 getResultStream()으로 게으르게 읽어서, 트랜잭션이 없으면 커서가 먼저 닫힌다
@Transactional(readOnly = true)
public class OpenMatchStreamHandler implements OpenMatchStreamUsecase {

    private final MatchStreamPort matchStreamPort;
    private final MatchRoomMembershipPort matchRoomMembershipPort;
    private final RoomInfoAssembler roomInfoAssembler;

    @Override
    public void handle(OpenMatchStreamCommand command) {
        UserId userId = new UserId(command.userId());
        // 활성 신청이 없으면 붙을 방이 없다 — 열어줘도 어느 방도 구독하지 못해
        // 그 뒤에 신청해도 이벤트가 영영 오지 않는 연결이 된다. 열기 전에 막는다.
        // 레지스트리를 건드리기 전에 검사해야 실패한 연결이 흔적을 남기지 않는다
        RoomInfo room = roomInfoAssembler.assembleFor(userId)
                .orElseThrow(ActiveMatchNotFoundException::new);
        MatchStreamConnection connection = command.connection();
        // 앱 재시작처럼 끊긴 줄 모르고 남아 있는 옛 연결이 있다 — 마지막 연결만 남긴다
        matchStreamPort.register(userId, connection)
                .ifPresent(MatchStreamConnection::closeSuperseded);
        // 구독을 먼저 건다 — 스냅샷을 보내는 사이에 온 갱신을 놓치지 않는다
        matchRoomMembershipPort.join(userId, room.runningRoomId());
        // 재연결이 곧 스냅샷 재수신이다 — 놓친 이벤트를 되짚을 필요가 없다
        connection.send(MatchStreamEvent.updated(room));
    }
}
