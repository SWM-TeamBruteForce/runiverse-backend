package com.runiverse.running_service.application.match.command.broadcast;

import com.runiverse.running_service.application.match.port.in.BroadcastMatchEventUsecase;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomMembersPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BroadMatchEventHandler implements BroadcastMatchEventUsecase {

    private final LoadMatchRoomMembersPort loadMatchRoomMembersPort;
    private final MatchStreamPort matchStreamPort;

    @Override
    public void handle(BroadcastMatchEventCommand command) {
        // 방 ID는 이벤트 최상위에 있다 — 페이로드가 RoomInfo가 아닌 이벤트도 있어서다
        loadMatchRoomMembersPort.usersIn(command.event().runningRoomId()).stream()
                .map(matchStreamPort::find)
                .flatMap(Optional::stream)
                .forEach(connection -> send(connection, command.event()));
    }

    private void send(MatchStreamConnection connection, MatchStreamEvent event) {
        try {
            connection.send(event);
        } catch (RuntimeException e) {
            // 한 연결의 실패가 나머지 수신자를 막지 않는다 — 끊긴 단말은 스스로 정리된다
            log.error("[매칭] 이벤트 전송 실패: 처리하지 못한 예외 - connectionId={}", connection.id(), e);
        }
    }
}
