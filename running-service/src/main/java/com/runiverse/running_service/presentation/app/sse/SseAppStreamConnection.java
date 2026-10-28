package com.runiverse.running_service.presentation.app.sse;

import com.runiverse.running_service.application.app.port.out.AppStreamConnection;
import com.runiverse.running_service.application.app.port.out.AppStreamEvent;
import com.runiverse.running_service.presentation.notification.response.NotificationEventResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
public class SseAppStreamConnection implements AppStreamConnection {

    private final String id;
    private final SseEmitter emitter;
    // keep-alive 스케줄러와 알림 푸시(Redis 리스너 스레드)가 동시에 쓴다.
    // SseEmitter는 동시 전송을 막아주지 않아 프레임이 섞이면 스트림 전체가 깨진다
    private final Object sendLock = new Object();

    public SseAppStreamConnection(String id, SseEmitter emitter) {
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

    @Override
    public void send(AppStreamEvent event) {
        // 와이어 계약은 presentation이 갖는다 — application 모델을 그대로 흘리지 않는다.
        // 이벤트 이름마다 data 형태가 다르므로 여기서 가른다
        send(SseEmitter.event()
                .name(event.type().name())
                .data(bodyOf(event), MediaType.APPLICATION_JSON));
    }

    // 알림은 목록(2-4) 항목에서 type만 뺀 모양이다 — 종류는 event 이름이 말한다
    private Object bodyOf(AppStreamEvent event) {
        return switch (event.type()) {
            case FRIEND_REQUESTED, FRIEND_ACCEPTED, RUNNING_INVITED ->
                    NotificationEventResponse.from(event.notification());
        };
    }

    private void send(SseEmitter.SseEventBuilder event) {
        synchronized (sendLock) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                // 끊긴 단말이다. 여기서 닫아야 onCompletion이 돌아 레지스트리에서 빠진다
                log.debug("[앱] 스트림 전송 실패: 끊긴 단말 - connectionId={}", id);
                complete();
            }
        }
    }

    private void complete() {
        try {
            emitter.complete();
        } catch (RuntimeException e) {
            // 이미 끝난 스트림을 다시 닫는 경우다 — 결과가 같으니 삼킨다
            log.debug("[앱] 스트림 종료 건너뜀: 이미 닫힘 - connectionId={}", id);
        }
    }
}
