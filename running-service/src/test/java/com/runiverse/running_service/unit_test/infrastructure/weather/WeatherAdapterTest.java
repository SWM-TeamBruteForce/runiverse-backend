package com.runiverse.running_service.infrastructure.weather;

import com.runiverse.running_service.application.running.port.out.Weather;
import ch.qos.logback.classic.Level;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("날씨 어댑터 단위 테스트")
public class WeatherAdapterTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final Weather FALLBACK = new Weather(0, new BigDecimal("15.0"));
    private static final Weather OBSERVED = new Weather(61, new BigDecimal("18.4"));
    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 29, 13, 40);

    @Mock
    private OpenMeteoWeatherClient client;

    private WeatherAdapter adapter;
    private LogCapture log;

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @BeforeEach
    void setUp() {
        log = LogCapture.of(WeatherAdapter.class);
        WeatherProperties properties = new WeatherProperties(
                FALLBACK.code(), FALLBACK.temperature(),
                "http://localhost", MAX_ATTEMPTS, Duration.ofSeconds(1));
        adapter = new WeatherAdapter(client, properties);
    }

    @Test
    @DisplayName("조회에 성공하면 관측값을 그대로 준다")
    void 관측값을_그대로_준다() {
        // given
        when(client.fetch(anyDouble(), anyDouble(), any())).thenReturn(Optional.of(OBSERVED));

        // when
        Weather weather = adapter.load(37.5, 127.0, AT);

        // then
        assertThat(weather).isEqualTo(OBSERVED);
        verify(client, times(1)).fetch(anyDouble(), anyDouble(), any());
    }

    @Test
    @DisplayName("통신에 실패하면 정해진 횟수만큼 재시도한다")
    void 통신_실패는_재시도한다() {
        // given -> 앞의 두 번은 끊기고 마지막에 붙는다
        when(client.fetch(anyDouble(), anyDouble(), any()))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(Optional.of(OBSERVED));

        // when
        Weather weather = adapter.load(37.5, 127.0, AT);

        // then
        assertThat(weather).isEqualTo(OBSERVED);
        verify(client, times(MAX_ATTEMPTS)).fetch(anyDouble(), anyDouble(), any());
    }

    @Test
    @DisplayName("끝내 실패하면 기본값을 준다 -> 조회 실패가 러닝 종료를 막지 않는다")
    void 끝내_실패하면_기본값을_준다() {
        // given
        when(client.fetch(anyDouble(), anyDouble(), any()))
                .thenThrow(new ResourceAccessException("timeout"));

        // when
        Weather weather = adapter.load(37.5, 127.0, AT);

        // then
        assertThat(weather).isEqualTo(FALLBACK);
        verify(client, times(MAX_ATTEMPTS)).fetch(anyDouble(), anyDouble(), any());
    }

    @Test
    @DisplayName("요청이 거부되면 재시도하지 않고 기본값을 준다 -> 같은 요청을 되풀이할 이유가 없다")
    void 거부된_요청은_재시도하지_않는다() {
        // given
        when(client.fetch(anyDouble(), anyDouble(), any()))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        // when
        Weather weather = adapter.load(37.5, 127.0, AT);

        // then
        assertThat(weather).isEqualTo(FALLBACK);
        verify(client, times(1)).fetch(anyDouble(), anyDouble(), any());
    }

    @Test
    @DisplayName("값이 없는 응답은 재시도하지 않고 기본값을 준다 -> 다시 물어도 같은 답이다")
    void 값이_없으면_재시도하지_않는다() {
        // given
        when(client.fetch(anyDouble(), anyDouble(), any())).thenReturn(Optional.empty());

        // when
        Weather weather = adapter.load(37.5, 127.0, AT);

        // then
        assertThat(weather).isEqualTo(FALLBACK);
        verify(client, times(1)).fetch(anyDouble(), anyDouble(), any());
    }

    @Test
    @DisplayName("재시도가 남은 통신 실패는 스택트레이스 없이 WARN으로 남기고 성공하면 ERROR를 남기지 않는다")
    void 재시도_중간_실패는_WARN이다() {
        // given
        when(client.fetch(anyDouble(), anyDouble(), any()))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(Optional.of(OBSERVED));

        // when
        adapter.load(37.5, 127.0, AT);

        // then -> 다음 시도에서 붙을 수 있어 아직 실패가 아니다
        assertThat(log.messages(Level.WARN)).containsExactly(
                "[러닝] 날씨 조회 실패: 날씨 API 통신 오류 - attempt=1, maxAttempts=3, cause=ResourceAccessException");
        assertThat(log.events(Level.WARN).getFirst().getThrowableProxy()).isNull();
        assertThat(log.events(Level.ERROR)).isEmpty();
    }

    @Test
    @DisplayName("재시도를 모두 소진하면 마지막 실패만 원인 예외를 담아 ERROR로 남긴다")
    void 재시도_소진은_ERROR다() {
        // given
        when(client.fetch(anyDouble(), anyDouble(), any()))
                .thenThrow(new ResourceAccessException("timeout"));

        // when
        adapter.load(37.5, 127.0, AT);

        // then -> 스택트레이스는 한 번만 남는다
        assertThat(log.messages(Level.WARN)).hasSize(MAX_ATTEMPTS - 1);
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[러닝] 날씨 조회 실패: 재시도 소진 - maxAttempts=3");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy().getClassName())
                .isEqualTo(ResourceAccessException.class.getName());
    }

    @Test
    @DisplayName("요청이 거부되면 status만 남기고 응답 본문에 되돌아온 좌표는 남기지 않는다")
    void 거부_로그에_좌표가_없다() {
        // given -> Open-Meteo는 400 본문에 요청 값을 되돌려 준다
        when(client.fetch(anyDouble(), anyDouble(), any()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                        "{\"error\":true,\"reason\":\"Latitude must be in range of -90 to 90°. Given: 91.0.\"}"
                                .getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        // when
        adapter.load(91.0, 127.0, AT);

        // then
        assertThat(log.messages(Level.WARN))
                .containsExactly("[러닝] 날씨 조회 실패: 날씨 API 요청 거부 - status=400");
        assertThat(log.events(Level.WARN).getFirst().getThrowableProxy()).isNull();
        assertThat(log.messages(Level.WARN)).noneMatch(message -> message.contains("91.0"));
    }
}
