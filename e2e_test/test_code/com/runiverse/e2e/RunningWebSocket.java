package com.runiverse.e2e;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 러닝 WebSocket을 컨테이너 밖에서 두드리는 테스트 클라이언트.
 * STOMP가 아니라 {event, data} 봉투를 그대로 주고받는 순수 텍스트 채널이라
 * 라이브러리 없이 JDK HttpClient만으로 붙는다.
 */
public final class RunningWebSocket implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String ERROR_EVENT = "ERROR";

    private final WebSocket socket;
    private final BlockingQueue<Map<String, Object>> received;

    private RunningWebSocket(WebSocket socket, BlockingQueue<Map<String, Object>> received) {
        this.socket = socket;
        this.received = received;
    }

    static RunningWebSocket connect(HttpClient httpClient, String url, String accessToken) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                .connectTimeout(DEFAULT_TIMEOUT);
        // 핸드셰이크도 SecurityFilterChain을 지난다 — 토큰이 없으면 여기서 401로 끊긴다
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        try {
            WebSocket socket = builder
                    .buildAsync(URI.create(url), new Collector(received))
                    .join();
            return new RunningWebSocket(socket, received);
        } catch (CompletionException e) {
            // 핸드셰이크가 막힌 것과 그 밖의 실패를 테스트가 구분할 수 있어야 한다
            if (e.getCause() instanceof WebSocketHandshakeException handshakeException) {
                throw new HandshakeFailedException(handshakeException.getResponse().statusCode());
            }
            throw e;
        }
    }

    /** 핸드셰이크가 HTTP 단계에서 거절된 경우. 상태 코드가 곧 거절 사유다. */
    public static final class HandshakeFailedException extends RuntimeException {

        HandshakeFailedException(int statusCode) {
            super("러닝 WebSocket 핸드셰이크가 거절되었습니다 — status=" + statusCode);
        }
    }

    public void send(String event, Object data) {
        socket.sendText(toJson(Map.of("event", event, "data", data)), true).join();
    }

    public Map<String, Object> await(String event) {
        return await(event, DEFAULT_TIMEOUT);
    }

    /**
     * 기다리는 이벤트가 올 때까지 나머지는 흘려보낸다 —
     * 좌표를 보내면 진행 통지가 섞여 들어와 순서를 고정할 수 없다.
     * 다만 ERROR는 흘리지 않는다. 흘리면 원인이 타임아웃으로 뭉개진다.
     */
    public Map<String, Object> await(String event, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IllegalStateException("%s 를 %s 안에 받지 못했습니다".formatted(event, timeout));
            }
            Map<String, Object> message = poll(remaining);
            if (message == null) {
                continue;
            }
            String actual = (String) message.get("event");
            if (event.equals(actual)) {
                return message;
            }
            if (ERROR_EVENT.equals(actual)) {
                throw new IllegalStateException(
                        "%s 를 기다리는 중 ERROR를 받았습니다 — %s".formatted(event, message.get("data")));
            }
        }
    }

    /** ERROR 봉투의 data(코드·메시지·sourceType)를 꺼낸다. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> awaitErrorPayload() {
        long deadline = System.nanoTime() + DEFAULT_TIMEOUT.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IllegalStateException("ERROR 를 %s 안에 받지 못했습니다".formatted(DEFAULT_TIMEOUT));
            }
            Map<String, Object> message = poll(remaining);
            if (message != null && ERROR_EVENT.equals(message.get("event"))) {
                return (Map<String, Object>) message.get("data");
            }
        }
    }

    private Map<String, Object> poll(long remainingNanos) {
        try {
            return received.poll(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("메시지 대기가 중단되었습니다", e);
        }
    }

    @Override
    public void close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "테스트 종료").join();
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("메시지 직렬화에 실패했습니다", e);
        }
    }

    /** 프레임이 쪼개져 올 수 있어 last가 올 때까지 이어 붙인 뒤에야 한 건으로 센다. */
    private record Collector(BlockingQueue<Map<String, Object>> received,
                             StringBuilder buffer) implements WebSocket.Listener {

        Collector(BlockingQueue<Map<String, Object>> received) {
            this(received, new StringBuilder());
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                received.add(parse(buffer.toString()));
                buffer.setLength(0);
            }
            // null을 돌려주면 HttpClient가 알아서 다음 메시지를 요청한다
            return null;
        }

        private static Map<String, Object> parse(String payload) {
            try {
                return OBJECT_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {
                });
            } catch (JsonProcessingException e) {
                // 봉투가 아닌 것이 오면 흘리지 않고 대기 쪽에서 보이게 남긴다
                return new HashMap<>(Map.of("event", "UNPARSABLE", "data", payload));
            }
        }
    }
}
