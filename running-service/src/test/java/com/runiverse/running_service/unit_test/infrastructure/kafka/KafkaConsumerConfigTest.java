package com.runiverse.running_service.unit_test.infrastructure.kafka;

import com.runiverse.running_service.application.recordcard.exception.RunningRecordNotFoundException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidMusicResultException;
import com.runiverse.running_service.infrastructure.kafka.KafkaConsumerConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import jakarta.persistence.PessimisticLockException;
import org.hibernate.exception.DataException;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.exception.JDBCConnectionException;
import org.hibernate.exception.LockAcquisitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("Kafka 구독 설정 단위 테스트")
class KafkaConsumerConfigTest {

    private static final String JAAS = "org.apache.kafka.common.security.scram.ScramLoginModule required "
            + "username=\"%s\" password=\"%s\";";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("접속 값이 모두 있고 CA 파일을 읽을 수 있으면 만들어진다")
    void createsWithConnection() throws IOException {
        // given
        KafkaProperties properties = properties("12.16.16.198:9092", "dev.running-service", "secret", caFile());

        // when & then
        assertThatCode(() -> new KafkaConsumerConfig(properties)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("켰는데 접속 값이 비어 있으면 빠진 변수 이름을 모두 알리며 기동을 막는다 — 비밀번호 값은 싣지 않는다")
    void rejectsMissingConnection() {
        // given
        KafkaProperties properties = properties("", "", "", "");

        // when & then
        assertThatThrownBy(() -> new KafkaConsumerConfig(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAFKA_BOOTSTRAP_SERVERS")
                .hasMessageContaining("KAFKA_SASL_USERNAME")
                .hasMessageContaining("KAFKA_SASL_PASSWORD")
                .hasMessageContaining("KAFKA_SSL_CA_LOCATION");
    }

    @Test
    @DisplayName("CA 경로가 있어도 파일이 없으면 기동을 막는다 — 마운트를 빠뜨린 경우")
    void rejectsUnreadableCa() {
        // given
        KafkaProperties properties = properties("12.16.16.198:9092", "dev.running-service", "secret",
                tempDir.resolve("missing-ca.pem").toString());

        // when & then
        assertThatThrownBy(() -> new KafkaConsumerConfig(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAFKA_SSL_CA_LOCATION")
                .hasMessageNotContaining("KAFKA_BOOTSTRAP_SERVERS")
                .hasMessageNotContaining("secret");
    }

    @Test
    @DisplayName("업무 예외는 다시 받지 않고 바로 건너뛴다 — domain·application 둘 다")
    void skipsBusinessExceptions() throws IOException {
        // given
        DefaultErrorHandler handler = handler();
        ConsumerRecord<?, ?> record = new ConsumerRecord<>("dev.record-card.music.generated", 0, 3L, "key", "{}");

        // when & then — true면 복구(건너뜀) 처리가 끝났다는 뜻이다
        assertThat(handler.handleOne(new InvalidMusicResultException(), record, mock(Consumer.class),
                runningContainer())).isTrue();
        assertThat(handler.handleOne(new RunningRecordNotFoundException(), record, mock(Consumer.class),
                runningContainer())).isTrue();
    }

    @Test
    @DisplayName("예상하지 못한 예외는 다시 받지 않고 건너뛴다 — 다시 받아도 같아 파티션이 멈춘다")
    void skipsUnexpectedExceptions() throws IOException {
        // given — 본문 JSON null의 NPE, 값 문제(SQLState 22: NUL·길이 초과), 제약 위반(SQLState 23)
        List<Exception> unexpected = List.of(
                new NullPointerException(),
                new DataException("invalid byte sequence", new SQLException("0x00", "22021")),
                new DataIntegrityViolationException("check violation", new SQLException("check", "23514")));

        for (Exception e : unexpected) {
            // when & then — true면 복구(건너뜀) 처리가 끝났다는 뜻이다
            assertThat(handler().handleOne(wrapped(e), record(), mock(Consumer.class), runningContainer()))
                    .as(e.getClass().getSimpleName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("잠깐 생긴 DB 문제는 건너뛰지 않고 다시 받는다 — 연결 끊김·커넥션 풀 대기 초과·잠금 대기·데드락")
    void retriesTransientDatabaseErrors() throws IOException {
        // given — 같은 DB 오류도 나는 자리마다 감싸는 타입이 다르다. 원인의 SQLState로 판정한다
        List<Exception> transientErrors = List.of(
                // 연결 끊김 — 트랜잭션 시작·쿼리 중·커밋 중
                new CannotCreateTransactionException("could not open connection",
                        new JDBCConnectionException("connect", new SQLException("refused", "08001"))),
                new JDBCConnectionException("connection reset", new SQLException("reset", "08006")),
                new TransactionSystemException("commit failed",
                        new DataAccessResourceFailureException("commit", new SQLException("reset", "08006"))),
                // DB 재시작 — 일반 JDBC 예외로 감싸져 온다
                new GenericJDBCException("terminating connection", new SQLException("admin shutdown", "57P01")),
                // 커넥션 풀 대기 초과 — SQLState 없이 온다
                new CannotCreateTransactionException("pool timeout",
                        new SQLTransientConnectionException("Connection is not available, request timed out")),
                // 잠금 대기 초과 · 데드락
                new PessimisticLockException("lock timeout", new SQLException("lock not available", "55P03")),
                new CannotAcquireLockException("deadlock",
                        new LockAcquisitionException("deadlock", new SQLException("deadlock", "40P01"))));

        for (Exception e : transientErrors) {
            // when & then — false면 다시 받도록 되돌렸다는 뜻이다(첫 간격 1초를 기다린다)
            assertThat(handler().handleOne(wrapped(e), record(), mock(Consumer.class), runningContainer()))
                    .as(e.getClass().getSimpleName())
                    .isFalse();
        }
    }

    private static ConsumerRecord<?, ?> record() {
        return new ConsumerRecord<>("dev.record-card.music.generated", 0, 3L, "key", "{}");
    }

    // 리스너에서 난 예외는 컨테이너가 ListenerExecutionFailedException으로 감싸 오류 처리기에 넘긴다
    private static Exception wrapped(Exception cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }

    private DefaultErrorHandler handler() throws IOException {
        KafkaConsumerConfig config = new KafkaConsumerConfig(
                properties("12.16.16.198:9092", "dev.running-service", "secret", caFile()));
        return (DefaultErrorHandler) config.kafkaErrorHandler();
    }

    private static MessageListenerContainer runningContainer() {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        given(container.isRunning()).willReturn(true);
        return container;
    }

    private String caFile() throws IOException {
        Path ca = tempDir.resolve("kafka-ca.pem");
        Files.writeString(ca, "-----BEGIN CERTIFICATE-----\n-----END CERTIFICATE-----\n");
        return ca.toString();
    }

    private static KafkaProperties properties(String bootstrap, String username, String password, String caLocation) {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of(bootstrap));
        properties.getProperties().put("sasl.jaas.config", JAAS.formatted(username, password));
        properties.getProperties().put("ssl.truststore.location", caLocation);
        return properties;
    }
}
