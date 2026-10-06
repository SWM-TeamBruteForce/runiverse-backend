package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;

import java.util.ArrayList;
import java.util.List;

// SseMatchStreamConnection을 대신한다 — 실제 SSE 프레임 대신 받은 이벤트를 쌓아둔다.
// 와이어 형식(이벤트 이름·JSON)은 여기서 보지 않는다. 그건 presentation의 몫이다
public class RecordingMatchStreamConnection implements MatchStreamConnection {

    private final String id;
    private final List<MatchStreamEvent> received = new ArrayList<>();
    private boolean closed;

    public RecordingMatchStreamConnection(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void keepAlive() {
        // 이 테스트의 주제가 아니다
    }

    @Override
    public void closeSuperseded() {
        closed = true;
    }

    // 닫는 이유를 구분하지 않는다 — 실제 구현도 둘 다 complete()로 끝난다
    @Override
    public void closeForAccountDeletion() {
        closed = true;
    }

    @Override
    public void send(MatchStreamEvent event) {
        received.add(event);
    }

    public List<MatchStreamEvent> received() {
        return List.copyOf(received);
    }

    public boolean isClosed() {
        return closed;
    }
}
