package com.runiverse.running_service.infrastructure.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    public static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // 응답 시각을 초 단위로 통일한다 — 필드에 @JsonFormat을 붙이면 이 설정을 덮어쓴다
    @Bean
    JsonMapperBuilderCustomizer localDateTimeSecondsFormat() {
        SimpleModule module = new SimpleModule()
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(SECONDS));
        return builder -> builder.addModule(module);
    }
}
