package com.runiverse.running_service.presentation.running.websocket;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationCommand;
import com.runiverse.running_service.application.running.command.session.RegisterRunningSessionCommand;
import com.runiverse.running_service.application.running.command.session.RemoveRunningSessionCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningResult;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.in.RegisterRunningSessionUsecase;
import com.runiverse.running_service.application.running.port.in.RemoveRunningSessionUsecase;
import com.runiverse.running_service.application.running.port.in.StartRunningUsecase;
import com.runiverse.running_service.application.running.port.in.UpdateRunningLocationUsecase;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.presentation.common.security.JwtHandshakeInterceptor;
import com.runiverse.running_service.presentation.common.websocket.WebSocketEnvelope;
import com.runiverse.running_service.presentation.running.websocket.message.ErrorPayload;
import com.runiverse.running_service.presentation.running.websocket.message.RunningFinishRequest;
import com.runiverse.running_service.presentation.running.websocket.message.RunningLocationUpdateRequest;
import com.runiverse.running_service.presentation.running.websocket.message.RunningMessageType;
import com.runiverse.running_service.presentation.running.websocket.message.RunningStartRequest;
import com.runiverse.running_service.presentation.running.websocket.message.RunningWebSocketErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningWebSocketHandler extends TextWebSocketHandler {

    private final JsonMapper jsonMapper;
    private final StartRunningUsecase startRunningUsecase;
    private final RegisterRunningSessionUsecase registerRunningSessionUsecase;
    private final RemoveRunningSessionUsecase removeRunningSessionUsecase;
    private final UpdateRunningLocationUsecase updateRunningLocationUsecase;
    private final FinishRunningUsecase finishRunningUsecase;
    // attribute에 저장할 runningRoomId
    public static final String RUNNING_ROOM_ID = "runningRoomId";
    // 좌표 배치마다 방을 다시 읽지 않으려고 세션에 새겨 둔다 — 시작 뒤 바뀌지 않는 값이다
    public static final String TARGET_DISTANCE_METERS = "targetDistanceMeters";

    // 웹소켓 연결이 성공한 직후 한번 호출
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 연결만으로는 아무것도 등록하지 않는다 — 어느 방인지는 RUNNING_START가 정한다
        log.info("러닝 WebSocket 연결 — userId={}, sessionId={}", userId(session), session.getId());
    }

    // event 메시지 보내서 실제 이벤트 핸들에 도착하기전 메시지
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        WebSocketEnvelope envelope;
        try {
            envelope = jsonMapper.readValue(message.getPayload(), WebSocketEnvelope.class);
        } catch (JacksonException e) {
            log.warn("러닝 WebSocket 봉투 파싱 실패 — userId={}", userId(session));
            sendError(session, RunningWebSocketErrorCode.MALFORMED_MESSAGE, null);
            return;
        }
        // 위치 좌표가 실려 오므로 payload는 개인정보다. INFO로 남기지 않는다.
        log.debug("러닝 WebSocket 수신 — userId={}, payload={}", userId(session), message.getPayload());
        String event = envelope.event();
        // from()은 null에도 empty를 돌려줘 UNSUPPORTED와 구분이 안 된다 — 여기서 먼저 가른다
        if (event == null || event.isBlank()) {
            sendError(session, RunningWebSocketErrorCode.MISSING_MESSAGE_TYPE, null);
            return;
        }
        RunningMessageType type = RunningMessageType.from(event).orElse(null);
        if (type == null) {
            sendError(session, RunningWebSocketErrorCode.UNSUPPORTED_MESSAGE_TYPE, envelope.event());
            return;
        }
        switch (type) {
            case HEALTH_CHECK -> send(session, RunningMessageType.HEALTH_CHECKED.message());
            case RUNNING_START -> handleRunningStart(session, envelope);
            case RUNNING_LOCATION_UPDATE -> handleLocationUpdate(session, envelope);
            case RUNNING_FINISH -> handleRunningFinish(session, envelope);
            // HEALTH_CHECKED·RUNNING_STARTED·ERROR는 S→C 전용 — 클라가 보내면 처리 대상이 아니다.
            default -> sendError(session, RunningWebSocketErrorCode.UNSUPPORTED_MESSAGE_TYPE, event);
        }
    }

    // 채널 등록·재입장·방 시작·참가자 시작을 한 번에 처리한다
    private void handleRunningStart(WebSocketSession session, WebSocketEnvelope envelope)
            throws IOException {
        RunningStartRequest request;
        try {
            request = jsonMapper.convertValue(envelope.data(), RunningStartRequest.class);
        } catch (JacksonException | IllegalArgumentException e) {
            sendError(session, RunningWebSocketErrorCode.INVALID_REQUEST, envelope.event());
            return;
        }
        if (request == null || !request.isValid()) {
            sendError(session, RunningWebSocketErrorCode.INVALID_REQUEST, envelope.event());
            return;
        }
        UserId userId = userId(session);
        StartRunningResult result;
        try {
            result = startRunningUsecase.handle(
                    new StartRunningCommand(userId.value(), request.runningRoomId()));
            registerRunningSessionUsecase.handle(new RegisterRunningSessionCommand(
                    userId.value(), request.runningRoomId(),
                    new WebSocketRunningConnection(session, jsonMapper)));
        } catch (BusinessException e) {
            sendError(session, e.getErrorCode(), envelope.event());
            return;
        }
        session.getAttributes().put(RUNNING_ROOM_ID, request.runningRoomId());
        // 세션 attribute는 ConcurrentHashMap이라 null을 못 담는다.
        // 목표 없는 솔로 방은 키 자체를 비워 두면 읽는 쪽이 null로 받는다
        Integer targetDistanceMeters = result.targetDistanceMeters();
        if (targetDistanceMeters == null) {
            session.getAttributes().remove(TARGET_DISTANCE_METERS);
        } else {
            session.getAttributes().put(TARGET_DISTANCE_METERS, targetDistanceMeters);
        }
        send(session, RunningMessageType.RUNNING_STARTED.message());
    }

    // 위치 배치에는 ack가 없다 — 실패만 ERROR로 돌려준다(api-spec 5-D)
    private void handleLocationUpdate(WebSocketSession session, WebSocketEnvelope envelope)
            throws IOException {
        RunningLocationUpdateRequest request;
        try {
            request = jsonMapper.convertValue(envelope.data(), RunningLocationUpdateRequest.class);
        } catch (JacksonException | IllegalArgumentException e) {
            sendError(session, RunningWebSocketErrorCode.INVALID_REQUEST, envelope.event());
            return;
        }
        if (request == null || !request.isValid()) {
            sendError(session, RunningWebSocketErrorCode.INVALID_REQUEST, envelope.event());
            return;
        }
        Long startedRoomId = (Long) session.getAttributes().get(RUNNING_ROOM_ID);
        // RUNNING_START 없이 온 좌표는 검증된 방이 없다(api-spec 5-C: START가 첫 메시지)
        if (startedRoomId == null) {
            sendError(session, RunningWebSocketErrorCode.RUNNING_NOT_STARTED, envelope.event());
            return;
        }
        try {
            updateRunningLocationUsecase.handle(new UpdateRunningLocationCommand(
                    userId(session).value(), startedRoomId,
                    (Integer) session.getAttributes().get(TARGET_DISTANCE_METERS),
                    toTrackPoints(request)));
        } catch (BusinessException e) {
            // 유스케이스가 튕겨낸 것만 코드로 내보낸다
            sendError(session, e.getErrorCode(), envelope.event());
        }
    }

    private List<TrackPoint> toTrackPoints(RunningLocationUpdateRequest request) {
        return request.locations().stream()
                .map(location -> new TrackPoint(
                        location.sequence(),
                        location.latitude(),
                        location.longitude(),
                        location.altitudeMeters(),          // ← 4번: 선택 그룹
                        location.accuracyMeters(),          // ← 5번: 필수 그룹
                        location.speedMetersPerSecond(),
                        location.headingDegrees(),
                        location.cadenceSpm(),
                        location.currentPaceSecondsPerKm(),
                        location.recordedAt()))
                .toList();
    }

    // 상태가 걸린 요청이라 ack가 있다 — 클라는 이걸 받고 로컬 트랙을 지운 뒤 REST로 결과를 본다(api-spec 5-D)
    private void handleRunningFinish(WebSocketSession session, WebSocketEnvelope envelope)
            throws IOException {
        RunningFinishRequest request;
        try {
            request = jsonMapper.convertValue(envelope.data(), RunningFinishRequest.class);
        } catch (JacksonException | IllegalArgumentException e) {
            sendError(session, RunningWebSocketErrorCode.INVALID_REQUEST, envelope.event());
            return;
        }
        if (request == null || !request.isValid()) {
            sendError(session, RunningWebSocketErrorCode.INVALID_REQUEST, envelope.event());
            return;
        }
        Long startedRoomId = (Long) session.getAttributes().get(RUNNING_ROOM_ID);
        // RUNNING_START 없이 온 종료는 검증된 방이 없다 — 좌표 배치와 같은 규칙이다
        if (startedRoomId == null) {
            sendError(session, RunningWebSocketErrorCode.RUNNING_NOT_STARTED, envelope.event());
            return;
        }
        try {
            finishRunningUsecase.handle(new FinishRunningCommand(
                    startedRoomId, userId(session).value(), request.forced()));
        } catch (BusinessException e) {
            // 유스케이스가 튕겨낸 것만 코드로 내보낸다
            sendError(session, e.getErrorCode(), envelope.event());
            return;
        }
        // 세션의 방은 지우지 않는다 — 지우면 ack를 놓친 클라의 재전송이
        // RUNNING_NOT_STARTED로 걸려 로컬 트랙을 영영 못 지운다. 멱등은 유스케이스가 책임진다
        send(session, RunningMessageType.RUNNING_FINISHED.message());
    }

    private void sendError(
            WebSocketSession session,
            RunningWebSocketErrorCode errorCode,
            String sourceType
    ) throws IOException {
        send(session, RunningMessageType.ERROR.message(ErrorPayload.of(errorCode, sourceType)));
    }

    private void sendError(
            WebSocketSession session,
            ErrorCode errorCode,
            String sourceType
    ) throws IOException {
        send(session, RunningMessageType.ERROR.message(ErrorPayload.of(errorCode, sourceType)));
    }

    private void send(WebSocketSession session, WebSocketEnvelope envelope) throws IOException {
        session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(envelope)));
    }

    // 통신 과정에서 오류가 발생하면 처리
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("러닝 WebSocket 전송 오류 — userId={}", userId(session), exception);
    }

    // 연결이 끊겼을때 처리
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UserId userId = userId(session);
        // 연결 끊김 ≠ 방 나가기 — running_room_sessions.is_connected는 여기서 건드리지 않는다.
        // 명부는 접속 여부라 여기서 지운다
        removeRunningSessionUsecase.handle(
                new RemoveRunningSessionCommand(
                        userId.value(), new WebSocketRunningConnection(session, jsonMapper)));
        log.info("러닝 WebSocket 종료 — userId={}, status={}", userId(session), status);
    }

    private UserId userId(WebSocketSession session) {
        return (UserId) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID);
    }
}
