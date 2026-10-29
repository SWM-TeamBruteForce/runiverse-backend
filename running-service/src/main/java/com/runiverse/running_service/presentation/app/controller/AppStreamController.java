package com.runiverse.running_service.presentation.app.controller;

import com.runiverse.running_service.application.app.command.stream.CloseAppStreamCommand;
import com.runiverse.running_service.application.app.command.stream.OpenAppStreamCommand;
import com.runiverse.running_service.application.app.port.in.CloseAppStreamUsecase;
import com.runiverse.running_service.application.app.port.in.OpenAppStreamUsecase;
import com.runiverse.running_service.application.app.port.out.AppStreamConnection;
import com.runiverse.running_service.presentation.app.sse.AppStreamProperties;
import com.runiverse.running_service.presentation.app.sse.SseAppStreamConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@RestController
// 앱이 켜져 있는 동안 내내 여는 개인 스트림 — 연결 직후 보내는 스냅샷은 없다
@RequestMapping("app")
@RequiredArgsConstructor
public class AppStreamController {

    private final OpenAppStreamUsecase openAppStreamUsecase;
    private final CloseAppStreamUsecase closeAppStreamUsecase;
    private final AppStreamProperties appStreamProperties;

    @GetMapping(path = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) throws IOException {
        UUID userId = UUID.fromString(jwt.getSubject());
        // 30초 컨테이너 기본값 대신 명시한다
        SseEmitter emitter = new SseEmitter(appStreamProperties.timeout().toMillis());
        AppStreamConnection connection =
                new SseAppStreamConnection(UUID.randomUUID().toString(), emitter);
        // 이게 없으면 타임아웃이 예외로 터져 500이 된다. 스스로 complete하지 않는다
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitter.complete());
        // 타임아웃·에러·클라 종료가 전부 여기로 모인다 — 정리는 한 곳에서만 한다
        emitter.onCompletion(() -> closeAppStreamUsecase.handle(
                new CloseAppStreamCommand(userId, connection)));
        openAppStreamUsecase.handle(new OpenAppStreamCommand(userId, connection));
        // 첫 바이트를 써야 응답 헤더가 나간다 — 보낼 스냅샷이 없어 주석 라인으로 연다
        emitter.send(SseEmitter.event().comment("connected"));
        return emitter;
    }
}
