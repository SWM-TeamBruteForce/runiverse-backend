package com.runiverse.running_service.infrastructure.redis.match;

import com.runiverse.running_service.application.match.command.stream.CloseSupersededMatchStreamCommand;
import com.runiverse.running_service.application.match.port.in.CloseSupersededMatchStreamUsecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

// 러닝은 대체 알림을 방 채널 봉투에 실어 RunningRoomListener가 함께 받는다.
// 매칭은 방을 옮기면 옛 인스턴스가 새 방을 듣지 않아 공용 채널로 보내므로, 그 채널 전용 리스너를 둔다
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchSupersedeListener implements MessageListener {

    private final JsonMapper jsonMapper;
    private final CloseSupersededMatchStreamUsecase closeSupersededMatchStreamUsecase;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        MatchSupersedeMessage payload;
        try {
            payload = jsonMapper.readValue(message.getBody(), MatchSupersedeMessage.class);
        } catch (JacksonException e) {
            // 깨진 메시지 한 건 때문에 이후 수신이 막히면 안 된다
            log.error("[매칭] 이전 스트림 종료 알림 파싱 실패: 메시지 형식 불일치 - channel={}",
                    new String(message.getChannel(), StandardCharsets.UTF_8), e);
            return;
        }
        closeSupersededMatchStreamUsecase.handle(
                new CloseSupersededMatchStreamCommand(payload.userId(), payload.winnerConnectionId()));
    }
}
