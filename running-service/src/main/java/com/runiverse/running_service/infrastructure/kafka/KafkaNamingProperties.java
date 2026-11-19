package com.runiverse.running_service.infrastructure.kafka;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Kafka 이름(토픽·컨슈머 그룹)의 환경 접두어 — 개발·테스트 서버(로컬은 dev)가 Kafka 하나를 같이 써서 이름 앞에 환경을 붙인다.
// 이름마다 변수를 두지 않고 KAFKA_ENV 하나로 만든다 — 하나만 바꾸다 환경이 섞이지 않게.
// dev·test·prod가 아니면 enum 바인딩이 실패해 기동하지 않는다
@ConfigurationProperties(prefix = "kafka")
@Validated
public record KafkaNamingProperties(
        @NotNull Env env
) {

    // 이름은 Spring Boot의 KafkaProperties(spring.kafka)와 겹치지 않게 했다 — 완료 구독 때 spring-kafka가 들어온다
    public enum Env {
        DEV, TEST, PROD;

        String prefix() {
            return name().toLowerCase();
        }
    }

    // record-card.music.requested → test.record-card.music.requested
    public String topic(String name) {
        return env.prefix() + "." + name;
    }
}
