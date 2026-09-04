package com.runiverse.running_service.presentation.match.sse;

import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
public class SseMatchStreamConnection implements MatchStreamConnection {

    private final String id;
    private final SseEmitter emitter;
    // keep-alive 스케줄러와 이벤트 발신이 동시에 쓴다.
    // SseEmitter는 동시 전송을 막아주지 않아 프레임이 섞이면 스트림 전체가 깨진다
    private final Object sendLock = new Object();

    public SseMatchStreamConnection(String id, SseEmitter emitter) {
        this.id = id;
        this.emitter = emitter;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void keepAlive() {
        send(SseEmitter.event().comment("ping"));
    }

    @Override
    public void closeSuperseded() {
        complete();
    }

    private void send(SseEmitter.SseEventBuilder event) {
        synchronized (sendLock) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                // 끊긴 단말이다. 여기서 닫아야 onCompletion이 돌아 레지스트리에서 빠진다
                log.debug("매칭 스트림 전송 실패 — id={}", id);
                complete();
            }
        }
    }

    private void complete() {
        try {
            emitter.complete();
        } catch (RuntimeException e) {
            // 이미 끝난 스트림을 다시 닫는 경우다 — 결과가 같으니 삼킨다
            log.debug("매칭 스트림 종료 실패 — id={}", id);
        }
    }
}
