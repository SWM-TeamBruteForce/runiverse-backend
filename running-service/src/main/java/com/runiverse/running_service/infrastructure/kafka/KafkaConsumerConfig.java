package com.runiverse.running_service.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.ArrayList;
import java.util.List;

// 다른 서비스의 이벤트 구독 설정 — 구독을 켰을 때만 만든다(로컬·E2E는 브로커에 닿지 않아 끈다).
// 접속 값은 빈 기본값이라(끈 환경도 떠야 해서) 켰는데 비어 있으면 여기서 기동을 막는다 — 조용히 구독하지 않는 일을 막는다.
// 처리 실패: 잠깐 생긴 DB 문제(연결 끊김·커넥션 풀 대기 초과·잠금 대기·데드락)만 오프셋을 커밋하지 않고 끝없이 다시 받는다.
// 그 밖의 예외는 모두 ERROR를 남기고 넘어간다 — 다시 받아도 같아서, 파티션이 하나라 멈추면 뒤 이벤트가 모두 밀린다
@Slf4j
@Configuration
@ConditionalOnBooleanProperty("kafka.consumer.enabled")
public class KafkaConsumerConfig {

    private static final long INITIAL_INTERVAL_MS = 1_000;
    private static final double MULTIPLIER = 2.0;
    // max.poll.interval.ms(기본 5분)보다 짧아야 한다 — 기다리는 동안 그룹에서 빠지지 않게
    private static final long MAX_INTERVAL_MS = 60_000;
    // 원인 체인을 따라갈 최대 깊이 — 원인이 순환하는 예외에서 멈추지 않는 일을 막는다
    private static final int MAX_CAUSE_DEPTH = 20;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        requireConnection(kafkaProperties);
    }

    // Spring Boot가 이 빈을 리스너 컨테이너에 건다
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        // 횟수 제한 없이 1초부터 두 배씩, 최대 60초 간격으로 다시 받는다 — DB가 돌아오면 이어서 처리한다
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_INTERVAL_MS, MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        // 넘긴 메시지는 토픽에 남는다(7일) — 이 로그의 위치로 찾아 다시 넣을 수 있다
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, e) -> log.error("[공통] Kafka 메시지 처리 건너뜀: 다시 받지 않는 예외 - topic={}, "
                                + "partition={}, offset={}, key={}",
                        record.topic(), record.partition(), record.offset(), record.key(), e),
                backOff);
        // 잠깐 생긴 DB 문제만 위 간격으로 다시 받고(null = 기본 간격), 나머지는 다시 받지 않고 바로 넘긴다
        handler.setBackOffFunction((record, e) -> isTransientDbError(e) ? null : new FixedBackOff(0L, 0L));
        return handler;
    }

    // 원인 체인에서 SQLException을 찾아 SQLState로 가른다 — 같은 DB 끊김도 나는 자리(트랜잭션 시작·쿼리·커밋)마다
    // 감싸는 예외 타입이 달라, 타입 목록으로는 빠짐없이 고를 수 없다
    static boolean isTransientDbError(Throwable e) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            // 커넥션 풀 대기 초과는 SQLState 없이 온다 — 타입으로 본다
            if (current instanceof SQLTransientException || current instanceof SQLRecoverableException) {
                return true;
            }
            if (current instanceof SQLException sql && isTransientSqlState(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // 08 연결 오류 · 40 데드락·직렬화 실패 · 55P03 잠금 대기 초과 · 57 DB 재시작·관리자 종료·문장 취소
    private static boolean isTransientSqlState(String sqlState) {
        return sqlState != null
                && (sqlState.startsWith("08") || sqlState.startsWith("40")
                || sqlState.startsWith("57") || sqlState.equals("55P03"));
    }

    private static void requireConnection(KafkaProperties properties) {
        List<String> missing = new ArrayList<>();
        if (properties.getBootstrapServers().stream().allMatch(String::isBlank)) {
            missing.add("KAFKA_BOOTSTRAP_SERVERS");
        }
        String jaasConfig = properties.getProperties().getOrDefault("sasl.jaas.config", "");
        if (jaasConfig.contains("username=\"\"")) {
            missing.add("KAFKA_SASL_USERNAME");
        }
        if (jaasConfig.contains("password=\"\"")) {
            missing.add("KAFKA_SASL_PASSWORD");
        }
        String caLocation = properties.getProperties().getOrDefault("ssl.truststore.location", "");
        if (caLocation.isBlank() || !Files.isReadable(Path.of(caLocation))) {
            missing.add("KAFKA_SSL_CA_LOCATION");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("kafka.consumer.enabled=true인데 Kafka 접속 값이 없거나 읽을 수 없습니다: " + missing);
        }
    }
}
