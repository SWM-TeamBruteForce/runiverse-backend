package com.runiverse.running_service.presentation.match.controller;

import com.runiverse.running_service.application.match.command.stream.CloseMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamCommand;
import com.runiverse.running_service.application.match.exception.ActiveMatchNotFoundException;
import com.runiverse.running_service.application.match.exception.MatchStreamUnavailableException;
import com.runiverse.running_service.application.match.port.in.CloseMatchStreamUsecase;
import com.runiverse.running_service.application.match.port.in.OpenMatchStreamUsecase;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import com.runiverse.running_service.presentation.match.sse.MatchStreamProperties;
import com.runiverse.running_service.presentation.match.sse.SseMatchStreamConnection;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
// 매칭 방·초대 방이 함께 쓰는 방 상태 스트림 — 방 종류는 이벤트 이름(MATCH_*·INVITE_ROOM_*)이 가른다.
@RequestMapping("match-rooms")
@RequiredArgsConstructor
public class MatchStreamController {

    private final OpenMatchStreamUsecase openMatchStreamUsecase;
    private final CloseMatchStreamUsecase closeMatchStreamUsecase;
    private final MatchStreamProperties matchStreamProperties;

    @GetMapping(path = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request)
            throws IOException {
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
            // 이 둘만 여기서 잡는다 — Accept가 text/event-stream이라
            // GlobalExceptionHandler가 JSON 본문을 쓰지 못하고 500으로 뒤집힌다.
            // 본문 없이 상태 코드로만 알린다. 중앙 핸들러를 거치지 않아 메트릭 원인도 여기서 남긴다
            log.info("활성 신청 없이 스트림 연결 시도 — userId={}", userId);
            request.setAttribute(HttpRequestMetricsFilter.REASON, e.getErrorCode().getCode());
            return ResponseEntity.notFound().build();
        } catch (MatchStreamUnavailableException e) {
            // 원인은 구독기가 ERROR로 남겼다. 앱은 404가 아니면 잠시 뒤 다시 연결한다
            request.setAttribute(HttpRequestMetricsFilter.REASON, e.getErrorCode().getCode());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        // 첫 바이트를 써야 응답 헤더가 나간다
        emitter.send(SseEmitter.event().comment("connected"));
        return ResponseEntity.ok(emitter);
    }
}
