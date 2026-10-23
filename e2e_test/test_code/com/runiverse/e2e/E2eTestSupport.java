package com.runiverse.e2e;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 배포될 도커 이미지를 그대로 띄운 뒤 컨테이너 밖에서 HTTP로만 검증하는 E2E의 공통 뼈대.
 * 프로덕션 모듈과 분리된 프로젝트라 앱 클래스를 참조할 수 없고, 그래서 블랙박스가 강제된다.
 */
public abstract class E2eTestSupport {

    // run-e2e.sh가 export한 값을 그대로 받는다
    private static final String BASE_URL =
            System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:8080/api/v1");
    private static final String APP_CONTAINER =
            System.getenv().getOrDefault("E2E_APP_CONTAINER", "runiverse-e2e-app");
    // compose가 container_name 없이 붙이는 기본 이름이다 — compose.yml의 프로젝트명·서비스명과 같아야 한다
    private static final String S3_CONTAINER =
            System.getenv().getOrDefault("E2E_S3_CONTAINER", "runiverse-e2e-localstack-1");
    // 핸들러가 등록된 경로(application.properties의 websocket.running-endpoint)를 컨텍스트 경로 뒤에 붙인다
    private static final String RUNNING_WEBSOCKET_URL =
            BASE_URL.replaceFirst("^http", "ws") + "/ws/running";
    private static final String MAIL_LOG_MARKER = "[메일 발송 생략]";
    private static final Pattern VERIFICATION_CODE = Pattern.compile("\\d{6}");
    private static final int CODE_LOOKUP_RETRIES = 10;
    private static final int MAIL_BODY_LINES = 5;
    // 서버가 앱 버전 헤더가 없는 요청을 거절한다 — 따로 넘기지 않으면 지원하는 앱처럼 싣는다.
    // 값은 sql/app-versions.sql의 안드로이드 최신 버전이다
    private static final Map<String, String> SUPPORTED_APP_VERSION =
            Map.of("X-App-Platform", "ANDROID", "X-App-Version", "1.2.0");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public record Response(int status, Map<String, Object> body) {

        public String text(String field) {
            return (String) body.get(field);
        }

        // JSON 숫자는 크기에 따라 Integer·Long·Double로 흩어져 온다 — 읽는 쪽에서 캐스팅하지 않게 모은다
        public Integer number(String field) {
            Object value = body.get(field);
            return value == null ? null : ((Number) value).intValue();
        }

        public Boolean bool(String field) {
            return (Boolean) body.get(field);
        }

        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> objects(String field) {
            return (List<Map<String, Object>>) body.get(field);
        }

        // routes처럼 객체가 아니라 [위도, 경도] 배열이 담긴 목록은 원소 타입을 못 박지 않는다
        public List<?> list(String field) {
            return (List<?>) body.get(field);
        }
    }

    /** 가입·온보딩까지 마친 사용자. 러닝처럼 온보딩이 전제인 흐름은 전부 여기서 출발한다. */
    public record TestUser(String userId, String email, String password, String nickname,
                           String accessToken) {

    }

    protected Response post(String path, Map<String, ?> request) {
        return post(path, request, null);
    }

    // DB를 비울 수 없으므로 테스트마다 겹치지 않는 값을 쓴다
    protected String uniqueEmail() {
        return "runner-" + shortId() + "@runiverse.test";
    }

    // 닉네임은 2~16자에 한글·영문·숫자·_ 만 허용된다
    protected String uniqueNickname() {
        return "runner" + shortId();
    }

    private Response exchange(String method, String path, Map<String, ?> request,
                              String accessToken, Map<String, String> headers) {
        // 본문 없는 메서드에 빈 문자열을 실으면 Content-Type만 남아 서버가 파싱을 시도한다
        HttpRequest.BodyPublisher body = request == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(toJson(request), StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body);
        if (request != null) {
            builder.header("Content-Type", "application/json");
        }
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        headers.forEach(builder::header);
        HttpResponse<String> response = send(builder.build());
        return new Response(response.statusCode(), parse(response.body()));
    }

    protected Response get(String path, String accessToken) {
        return exchange("GET", path, null, accessToken, SUPPORTED_APP_VERSION);
    }

    /** 기본 앱 버전 헤더 대신 이 헤더만 싣는다 — 빈 맵이면 앱 버전 헤더 없이 보낸다. */
    protected Response get(String path, String accessToken, Map<String, String> headers) {
        return exchange("GET", path, null, accessToken, headers);
    }

    protected Response post(String path, Map<String, ?> request, String accessToken) {
        return exchange("POST", path, request, accessToken, SUPPORTED_APP_VERSION);
    }

    protected Response patch(String path, Map<String, ?> request, String accessToken) {
        return exchange("PATCH", path, request, accessToken, SUPPORTED_APP_VERSION);
    }

    protected Response delete(String path, String accessToken) {
        return exchange("DELETE", path, null, accessToken, SUPPORTED_APP_VERSION);
    }

    /** 러닝 WebSocket에 붙는다. 토큰이 null이면 핸드셰이크가 401로 막히는지 확인하는 용도다. */
    protected RunningWebSocket connectRunningWebSocket(String accessToken) {
        return RunningWebSocket.connect(HTTP_CLIENT, RUNNING_WEBSOCKET_URL, accessToken, SUPPORTED_APP_VERSION);
    }

    /** 기본 앱 버전 헤더 대신 이 헤더만 싣는다 — 빈 맵이면 앱 버전 헤더 없이 연결한다. */
    protected RunningWebSocket connectRunningWebSocket(String accessToken, Map<String, String> headers) {
        return RunningWebSocket.connect(HTTP_CLIENT, RUNNING_WEBSOCKET_URL, accessToken, headers);
    }

    protected MatchStream openMatchStream(String accessToken) {
        return MatchStream.open(HTTP_CLIENT, BASE_URL + "/running-matches/stream", accessToken,
                SUPPORTED_APP_VERSION);
    }

    /** 메일 인증 → 가입까지만 한다. 액세스 토큰을 돌려준다. */
    protected String signUp(String email, String password) {
        post("/auth/email/verifications", Map.of("email", email));
        Response verified = post("/auth/email/verifications/confirm",
                Map.of("email", email, "code", sentVerificationCode(email)));
        Response signedUp = post("/auth/signup", Map.of(
                "verificationTicket", verified.text("verificationTicket"),
                "password", password));
        return signedUp.text("accessToken");
    }

    /**
     * 메일 인증 → 가입 → 온보딩까지 한 번에 끝낸다.
     * 러닝은 온보딩의 평균 페이스·몸무게가 없으면 시작조차 못 해 대부분의 흐름이 여기서 출발한다.
     */
    protected TestUser signUpAndOnboard() {
        String email = uniqueEmail();
        String password = "Password123!";
        String accessToken = signUp(email, password);
        String nickname = uniqueNickname();
        post("/users/onboarding", Map.of(
                "nickname", nickname,
                "gender", "MALE",
                "birthday", "1998-03-21",
                "averagePaceSecondsPerKm", 330,
                "weightKg", new BigDecimal("68.5"),
                "heightCm", new BigDecimal("176.2")
        ), accessToken);
        // userId는 토큰을 파싱하지 않고 API로 받는다 — 서명 검증 없이 sub를 믿을 이유가 없다
        String userId = get("/users/me", accessToken).text("userId");
        return new TestUser(userId, email, password, nickname, accessToken);
    }

    /**
     * 인증 코드는 Redis에 해시로만 남아 되돌릴 수 없다.
     * local 프로필의 LoggingEmailAdapter가 본문째로 찍은 로그에서 회수한다.
     */
    protected String sentVerificationCode(String email) {
        for (int attempt = 0; attempt < CODE_LOOKUP_RETRIES; attempt++) {
            String code = findCode(containerLogs(), email);
            if (code != null) {
                return code;
            }
            sleepBriefly();
        }
        throw new IllegalStateException("앱 로그에서 %s 의 인증 코드를 찾지 못했습니다".formatted(email));
    }

    private static String findCode(String logs, String email) {
        String[] lines = logs.split("\\R");
        // 같은 이메일로 여러 번 보냈다면 가장 마지막 발송을 쓴다
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].contains(MAIL_LOG_MARKER) || !lines[i].contains("to=" + email)) {
                continue;
            }
            // 코드는 마커 다음 줄들의 본문에 있다
            for (int j = i + 1; j < Math.min(i + MAIL_BODY_LINES, lines.length); j++) {
                Matcher matcher = VERIFICATION_CODE.matcher(lines[j]);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }
        return null;
    }

    /**
     * 사용자 자산 버킷에 객체를 직접 넣는다. presigned URL의 호스트는 컨테이너 안에서만 풀려
     * 테스트가 그 URL로 올릴 수 없다 — 대신 localstack 안에서 같은 key·형식·크기로 올린다.
     * 서버가 반영 때 보는 것은 HeadObject의 크기와 Content-Type뿐이라 판정에는 차이가 없다.
     */
    protected void uploadUserAsset(String key, String contentType, int sizeBytes) {
        // key는 셸 문자열에 섞지 않고 위치 인자로 넘긴다
        String script = "head -c \"$3\" /dev/zero > /tmp/e2e-upload"
                + " && awslocal s3api put-object --bucket \"$E2E_USER_ASSET_BUCKET\""
                + " --key \"$1\" --content-type \"$2\" --body /tmp/e2e-upload > /dev/null";
        try {
            Process process = new ProcessBuilder("docker", "exec", S3_CONTAINER,
                    "sh", "-c", script, "sh", key, contentType, String.valueOf(sizeBytes))
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException("S3 업로드에 실패했습니다: " + output);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("S3 업로드가 중단되었습니다", e);
        }
    }

    private static String containerLogs() {
        try {
            Process process = new ProcessBuilder("docker", "logs", APP_CONTAINER)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("앱 로그를 읽지 못했습니다", e);
        }
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("요청이 중단되었습니다", e);
        }
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("요청 직렬화에 실패했습니다", e);
        }
    }

    private static Map<String, Object> parse(String body) {
        // 204처럼 본문이 없는 응답도 있다
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("응답이 JSON이 아닙니다: " + body, e);
        }
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
