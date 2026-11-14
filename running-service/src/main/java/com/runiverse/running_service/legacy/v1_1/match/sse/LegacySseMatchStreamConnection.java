package com.runiverse.running_service.legacy.v1_1.match.sse;

import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.legacy.v1_1.match.response.LegacyRoomInfoResponse;
import com.runiverse.running_service.presentation.match.response.RunningReadyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

// 1.1.x 앱의 매칭 스트림 연결 — 연결 관리는 1.2.0 SseMatchStreamConnection과 같고 이벤트 이름·data만 1.1.1 서버 형식이다.
// 1.1.x는 MATCH_STARTED·MATCH_ROOM_UPDATED·RUNNING_READY만 안다
@Slf4j
public class LegacySseMatchStreamConnection implements MatchStreamConnection {

    // 1.2.0에서 MATCH_CONFIRMED로 이름을 바꿨다 — 1.1.x는 이 이름으로 확정 화면을 띄운다
    static final String MATCH_STARTED = "MATCH_STARTED";
    static final String MATCH_ROOM_UPDATED = "MATCH_ROOM_UPDATED";
    static final String RUNNING_READY = "RUNNING_READY";

    private final String id;
    private final SseEmitter emitter;
    // keep-alive 스케줄러와 이벤트 발신이 동시에 쓴다.
    // SseEmitter는 동시 전송을 막아주지 않아 프레임이 섞이면 스트림 전체가 깨진다
    private final Object sendLock = new Object();

    public LegacySseMatchStreamConnection(String id, SseEmitter emitter) {
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
    public void closeForAccountDeletion() {
        complete();
    }

    @Override
    public void send(MatchStreamEvent event) {
        SseEmitter.SseEventBuilder frame = switch (event.type()) {
            case MATCH_CONFIRMED -> roomEvent(MATCH_STARTED, event);
            // 1.1.x 앱은 초대 방에 들어갈 수 없어 실제로는 오지 않는다 — switch가 모든 이벤트를 다루게 하려고 둔다.
            // 오더라도 버리면 방 정보가 하나도 오지 않으니 아는 갱신 이벤트로 바꿔 보낸다
            case MATCH_ROOM_ENTRANCE, MATCH_ROOM_UPDATED, INVITE_ROOM_ENTRANCE, INVITE_ROOM_UPDATED ->
                    roomEvent(MATCH_ROOM_UPDATED, event);
            case RUNNING_READY -> SseEmitter.event()
                    .name(RUNNING_READY)
                    .data(RunningReadyResponse.from(event.ready()), MediaType.APPLICATION_JSON);
            // 1.1.x에는 채팅이 없다 — 모르는 이벤트를 보내지 않는다
            case ROOM_CHAT_SENT, ROOM_CHAT_EDITED, ROOM_CHAT_DELETED -> null;
        };
        if (frame != null) {
            send(frame);
        }
    }

    private static SseEmitter.SseEventBuilder roomEvent(String name, MatchStreamEvent event) {
        return SseEmitter.event()
                .name(name)
                .data(LegacyRoomInfoResponse.from(event.room()), MediaType.APPLICATION_JSON);
    }

    private void send(SseEmitter.SseEventBuilder event) {
        synchronized (sendLock) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                // 끊긴 단말이다. 여기서 닫아야 onCompletion이 돌아 레지스트리에서 빠진다
                log.debug("1.1.x 매칭 스트림 전송 실패 — id={}", id);
                complete();
            }
        }
    }

    private void complete() {
        try {
            emitter.complete();
        } catch (RuntimeException e) {
            // 이미 끝난 스트림을 다시 닫는 경우다 — 결과가 같으니 삼킨다
            log.debug("1.1.x 매칭 스트림 종료 실패 — id={}", id);
        }
    }
}
