package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.command.progress.BroadcastRunningProgressCommand;
import com.runiverse.running_service.application.running.command.session.CloseSupersededSessionCommand;
import com.runiverse.running_service.application.running.port.in.BroadcastRunningProgressUsecase;
import com.runiverse.running_service.application.running.port.in.CloseSupersededSessionUsecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningRoomListener implements MessageListener {

    private final JsonMapper jsonMapper;
    private final CloseSupersededSessionUsecase closeSupersededSessionUsecase;
    private final BroadcastRunningProgressUsecase broadcastRunningProgressUsecase;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        RunningRoomMessage envelope;
        try {
            envelope = jsonMapper.readValue(message.getBody(), RunningRoomMessage.class);
        } catch (JacksonException e) {
            // 깨진 메시지 한 건 때문에 이후 수신이 막히면 안 된다
            log.warn("방 채널 메시지 파싱 실패");
            return;
        }
        switch (envelope.type()) {
            case SUPERSEDE -> handleSupersede(envelope.data());
            case PROGRESS -> handleProgress(envelope.data());
        }
    }

    private void handleSupersede(Object data) {
        SupersedeMessage payload = jsonMapper.convertValue(data, SupersedeMessage.class);
        closeSupersededSessionUsecase.handle(
                new CloseSupersededSessionCommand(payload.userId(), payload.winnerSessionId()));
    }

    private void handleProgress(Object data) {
        ProgressMessage payload = jsonMapper.convertValue(data, ProgressMessage.class);
        broadcastRunningProgressUsecase.handle(new BroadcastRunningProgressCommand(
                payload.runningRoomId(), payload.toProgress()));
    }
}
