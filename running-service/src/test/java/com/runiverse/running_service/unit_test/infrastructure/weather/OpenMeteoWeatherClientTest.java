package com.runiverse.running_service.infrastructure.weather;

import com.runiverse.running_service.application.running.port.out.Weather;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Open-Meteo 날씨 조회 단위 테스트")
public class OpenMeteoWeatherClientTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 29, 13, 40, 12);

    private static final String SUCCESS_RESPONSE =
            """
                    {
                      "latitude": 37.5,
                      "longitude": 127.0,
                      "hourly_units": {"temperature_2m": "°C"},
                      "hourly": {
                        "time": ["2026-08-29T13:00"],
                        "weather_code": [1],
                        "temperature_2m": [27.8]
                      }
                    }
                    """;

    // 요청한 시각에 관측이 없으면 배열은 오되 원소가 null이다
    private static final String NULL_VALUE_RESPONSE =
            """
                    {
                      "hourly": {
                        "time": ["2026-08-29T13:00"],
                        "weather_code": [null],
                        "temperature_2m": [null]
                      }
                    }
                    """;

    private HttpServer server;
    private String capturedQuery;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("정상 응답이면 WMO 코드와 기온을 그대로 옮긴다")
    void 정상_응답을_변환한다() throws IOException {
        // given
        OpenMeteoWeatherClient client = createClient(200, SUCCESS_RESPONSE);

        // when
        Optional<Weather> weather = client.fetch(37.5, 127.0, AT);

        // then
        assertThat(weather).contains(new Weather(1, new BigDecimal("27.8")));
    }

    @Test
    @DisplayName("조회 시각을 변환 없이 정시로 잘라 보낸다 -> timezone 파라미터와 기준이 어긋나지 않는다")
    void 조회_시각을_정시로_보낸다() throws IOException {
        // given
        OpenMeteoWeatherClient client = createClient(200, SUCCESS_RESPONSE);

        // when
        client.fetch(37.5, 127.0, AT);

        // then -> 13:40:12 요청이 13:00으로만 잘리고 존 변환은 일어나지 않는다
        String query = URLDecoder.decode(capturedQuery, StandardCharsets.UTF_8);
        assertThat(query).contains("start_hour=2026-08-29T13:00");
        assertThat(query).contains("end_hour=2026-08-29T13:00");
        assertThat(query).contains("hourly=weather_code,temperature_2m");
        // past_days는 start_hour와 함께 쓸 수 없다 — 붙으면 API가 요청 자체를 거부한다
        assertThat(query).doesNotContain("past_days");
    }

    @Test
    @DisplayName("hourly가 없는 응답이면 비어 있는 결과를 준다")
    void hourly가_없으면_비운다() throws IOException {
        // given
        OpenMeteoWeatherClient client = createClient(200, "{}");

        // when
        Optional<Weather> weather = client.fetch(37.5, 127.0, AT);

        // then
        assertThat(weather).isEmpty();
    }

    @Test
    @DisplayName("값이 null인 응답이면 비어 있는 결과를 준다 -> 반쪽 값을 기록에 넣지 않는다")
    void 값이_null이면_비운다() throws IOException {
        // given
        OpenMeteoWeatherClient client = createClient(200, NULL_VALUE_RESPONSE);

        // when
        Optional<Weather> weather = client.fetch(37.5, 127.0, AT);

        // then
        assertThat(weather).isEmpty();
    }

    @Test
    @DisplayName("400 응답은 예외로 던진다 -> 재시도 여부는 어댑터가 정한다")
    void 요청이_거부되면_예외를_던진다() throws IOException {
        // given -> 잘못된 좌표·파라미터에 Open-Meteo가 주는 응답이다
        OpenMeteoWeatherClient client = createClient(400, "{\"error\":true,\"reason\":\"...\"}");

        // when & then
        assertThatThrownBy(() -> client.fetch(999.0, 127.0, AT))
                .isInstanceOf(HttpClientErrorException.class);
    }

    // 실제 네트워크 없이 Open-Meteo를 흉내낸다.
    // MockRestServiceServer는 못 쓴다 — 생성자가 requestFactory를 덮어써서 바인딩이 풀린다
    private OpenMeteoWeatherClient createClient(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/forecast", exchange -> {
            capturedQuery = exchange.getRequestURI().getQuery();
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        WeatherProperties properties = new WeatherProperties(
                0, new BigDecimal("15.0"),
                "http://localhost:" + server.getAddress().getPort(),
                3, Duration.ofSeconds(2));
        return new OpenMeteoWeatherClient(RestClient.builder(), properties);
    }
}
