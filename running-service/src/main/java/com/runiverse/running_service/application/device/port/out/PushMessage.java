package com.runiverse.running_service.application.device.port.out;

import java.time.Duration;
import java.util.Map;

// data는 눌렀을 때 갈 화면을 앱이 정하는 값이다
public record PushMessage(
        PushType type,
        String title,
        String body,
        Map<String, String> data,
        Duration ttl   // 지나면 전달하지 않는다. null이면 FCM 기본값 — 시각과 무관한 알림이다
) {

    // 이벤트는 커밋 뒤에 읽힌다 — 발행한 뒤 data가 바뀌지 않게 복사한다.
    // type은 FCM 어댑터가 data에 넣는다
    public PushMessage {
        data = Map.copyOf(data);
    }
}
