package com.runiverse.running_service.presentation.match.controller;

import com.runiverse.running_service.application.match.command.stream.CloseMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamCommand;
import com.runiverse.running_service.application.match.exception.ActiveMatchNotFoundException;
import com.runiverse.running_service.application.match.port.in.CloseMatchStreamUsecase;
import com.runiverse.running_service.application.match.port.in.OpenMatchStreamUsecase;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.presentation.match.sse.MatchStreamProperties;
import com.runiverse.running_service.presentation.match.sse.SseMatchStreamConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("running-matches")
@RequiredArgsConstructor
public class MatchStreamController {

    private final OpenMatchStreamUsecase openMatchStreamUsecase;
    private final CloseMatchStreamUsecase closeMatchStreamUsecase;
    private final MatchStreamProperties matchStreamProperties;

    @GetMapping(path = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal Jwt jwt) throws IOException {
        UUID userId = UUID.fromString(jwt.getSubject());
        // 30초 컨테이너 기본값 대신 명시한다
        SseEmitter emitter = new SseEmitter(matchStreamProperties.timeout().toMillis());
        MatchStreamConnection connection =
                new SseMatchStreamConnection(UUID.randomUUID().toString(), emitter);
        // 이게 없으면 타임아웃이 예외로 터져 500이 된다. 스스로 complete하지 않는다
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitter.complete());
        // 타임아웃·에러·클라 종료가 전부 여기로 모인다 — 정리는 한 곳에서만 한다
        emitter.onCompletion(() -> closeMatchStreamUsecase.handle(
                new CloseMatchStreamCommand(userId, connection)));
        try {
            openMatchStreamUsecase.handle(new OpenMatchStreamCommand(userId, connection));
        } catch (ActiveMatchNotFoundException e) {
            // 이 하나만 여기서 잡는다 — Accept가 text/event-stream이라
            // GlobalExceptionHandler가 JSON 본문을 쓰지 못하고 500으로 뒤집힌다.
            // 본문 없이 상태 코드로만 알린다
            log.info("활성 신청 없이 스트림 연결 시도 — userId={}", userId);
            return ResponseEntity.notFound().build();
        }
        // 첫 바이트를 써야 응답 헤더가 나간다
        emitter.send(SseEmitter.event().comment("connected"));
        return ResponseEntity.ok(emitter);
    }
}
