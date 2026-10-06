package com.runiverse.running_service.presentation.running.websocket;

import com.runiverse.running_service.application.running.port.out.RunningComboPeer;
import com.runiverse.running_service.application.running.port.out.RunningConnection;
import com.runiverse.running_service.application.running.port.out.RunningProgress;
import com.runiverse.running_service.presentation.running.websocket.message.RunningComboUpdatedPayload;
import com.runiverse.running_service.presentation.running.websocket.message.RunningMessageType;
import com.runiverse.running_service.presentation.running.websocket.message.RunningProgressPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

public record WebSocketRunningConnection(WebSocketSession session, JsonMapper jsonMapper) implements RunningConnection {

    private static final Logger log = LoggerFactory.getLogger(WebSocketRunningConnection.class);
    // 이 코드를 받은 클라는 재연결 하지 않는다.
    private static final CloseStatus SUPERSEDED = new CloseStatus(4001, "다른 연결이 이어받았습니다.");

    @Override
    public String id() {
        return session.getId();
    }

    @Override
    public void closeSuperseded() {
        try {
            session.close(SUPERSEDED);
        } catch (IOException e) {
            log.warn("밀려난 러닝 WebSocket 종료 실패 — sessionId={}", session.getId(), e);
        }
    }

    @Override
    public void closeForAccountDeletion() {
        try {
            session.close(CloseStatus.NORMAL);
        } catch (IOException e) {
            log.error("[러닝] 탈퇴 연결 종료 실패: 소켓 오류 - sessionId={}", session.getId(), e);
        }
    }

    @Override
    public void sendProgress(RunningProgress progress) {
        try {
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(
                    RunningMessageType.RUNNING_PROGRESS_UPDATED.message(
                            RunningProgressPayload.from(progress)))));
        } catch (SessionLimitExceededException e) {
            closeUnreliable(e);
        } catch (IOException | RuntimeException e) {
            // 한 명에게 못 보냈다고 나머지 참가자의 브로드캐스트가 멈추면 안 된다
            log.warn("러닝 진행 통지 전송 실패 — sessionId={}", session.getId(), e);
        }
    }

    @Override
    public void sendCombo(List<RunningComboPeer> peers) {
        try {
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(
                    RunningMessageType.RUNNING_COMBO_UPDATED.message(
                            RunningComboUpdatedPayload.from(peers)))));
        } catch (SessionLimitExceededException e) {
            closeUnreliable(e);
        } catch (IOException | RuntimeException e) {
            // 한 명에게 못 보냈다고 나머지 참가자의 브로드캐스트가 멈추면 안 된다
            log.warn("러닝 콤보 통지 전송 실패 — sessionId={}", session.getId(), e);
        }
    }

    // 전송이 한도를 넘겨 밀렸다 — 래퍼가 이후 전송을 조용히 버리므로 닫아서 재연결을 유도한다
    private void closeUnreliable(SessionLimitExceededException e) {
        log.warn("[러닝] 연결 종료: 전송 한도 초과 - sessionId={}", session.getId());
        try {
            session.close(e.getStatus());
        } catch (IOException closeFailure) {
            log.error("[러닝] 연결 종료 실패: 소켓 오류 - sessionId={}", session.getId(), closeFailure);
        }
    }
}
