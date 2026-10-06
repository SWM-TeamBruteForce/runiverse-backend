package com.runiverse.running_service.unit_test.infrastructure.config;

import com.runiverse.running_service.infrastructure.config.JacksonConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonConfigTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .addModule(new SimpleModule()
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(JacksonConfig.SECONDS)))
            .build();

    @Test
    @DisplayName("나노초가 있어도 초 단위까지만 직렬화한다")
    void serializesToSeconds() {
        // given -> 나노초가 남으면 응답마다 자릿수가 달라진다
        LocalDateTime time = LocalDateTime.of(2026, 8, 11, 13, 30, 0, 123_456_000);

        // when
        String json = jsonMapper.writeValueAsString(time);

        // then
        assertThat(json).isEqualTo("\"2026-08-11T13:30:00\"");
    }

    @Test
    @DisplayName("나노초가 0이어도 같은 형식으로 직렬화한다")
    void serializesZeroNanoToSameFormat() {
        // given -> 기본 직렬화는 이 경우 소수점을 생략해 형식이 갈린다
        LocalDateTime time = LocalDateTime.of(2026, 8, 11, 13, 30, 0);

        // when
        String json = jsonMapper.writeValueAsString(time);

        // then
        assertThat(json).isEqualTo("\"2026-08-11T13:30:00\"");
    }

    @Test
    @DisplayName("초 단위 문자열을 역직렬화할 수 있다")
    void deserializesSecondsFormat() {
        // when
        LocalDateTime time = jsonMapper.readValue("\"2026-08-11T13:30:00\"", LocalDateTime.class);

        // then
        assertThat(time).isEqualTo(LocalDateTime.of(2026, 8, 11, 13, 30, 0));
    }
}
