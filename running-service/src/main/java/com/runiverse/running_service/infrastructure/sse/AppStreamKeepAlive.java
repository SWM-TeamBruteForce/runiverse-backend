package com.runiverse.running_service.infrastructure.sse;

import com.runiverse.running_service.application.app.port.out.AppStreamConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppStreamKeepAlive {

    // 포트가 아니라 구현체를 받는다 — all()은 연결 유지 전용이라 포트에 두지 않았다
    private final AppStreamRegistryAdapter appStreamRegistryAdapter;

    // fixedRate가 아니라 fixedDelay다 — 연결이 늘어 한 바퀴가 길어져도 실행이 밀려 쌓이지 않는다
    @Scheduled(fixedDelayString = "${app-stream.keep-alive-interval}")
    public void pingAll() {
        for (AppStreamConnection connection : appStreamRegistryAdapter.all()) {
            try {
                // 끊긴 단말은 keepAlive가 스스로 닫고, onCompletion이 레지스트리에서 지운다
                connection.keepAlive();
            } catch (RuntimeException e) {
                // 한 연결의 실패가 나머지 ping을 막으면 그 뒤 연결들이 통째로 프록시에 끊긴다.
                // 끊긴 단말은 연결이 스스로 닫으므로 여기까지 온 예외는 예상 밖이다
                log.error("[앱] 스트림 keep-alive 실패: 처리하지 못한 예외 - connectionId={}", connection.id(), e);
            }
        }
    }
}
