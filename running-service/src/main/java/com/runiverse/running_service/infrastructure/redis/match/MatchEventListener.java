package com.runiverse.running_service.infrastructure.redis.match;

import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;
import com.runiverse.running_service.application.match.port.in.BroadcastMatchEventUsecase;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEventListener implements MessageListener {

    private final JsonMapper jsonMapper;
    private final BroadcastMatchEventUsecase broadcastMatchEventUsecase;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        MatchEventMessage payload;
        try {
            payload = jsonMapper.readValue(message.getBody(), MatchEventMessage.class);
        } catch (JacksonException e) {
            // 깨진 메시지 한 건 때문에 이후 수신이 막히면 안 된다
            log.error("[매칭] 이벤트 파싱 실패: 메시지 형식 불일치 - channel={}",
                    new String(message.getChannel(), StandardCharsets.UTF_8), e);
            return;
        }
        broadcastMatchEventUsecase.handle(new BroadcastMatchEventCommand(
                new MatchStreamEvent(payload.type(), payload.runningRoomId(),
                        payload.room(), payload.ready())));
    }
}
