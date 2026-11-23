package com.runiverse.running_service.unit_test.infrastructure.kafka;

import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.port.in.CompleteRecordCardMusicUsecase;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.application.recordcard.port.out.RequestRecordCardMusicPort;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.infrastructure.kafka.KafkaNamingProperties;
import com.runiverse.running_service.infrastructure.kafka.KafkaNamingProperties.Env;
import com.runiverse.running_service.infrastructure.kafka.local.LocalAiMusicAdapter;
import com.runiverse.running_service.infrastructure.kafka.outbox.RecordCardMusicOutboxAdapter;
import com.runiverse.running_service.presentation.recordcard.kafka.LocalRecordCardMusicGeneratedListener;
import com.runiverse.running_service.presentation.recordcard.kafka.RecordCardMusicGeneratedMessageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// 스프링 조건(@Profile·@ConditionalOnBooleanProperty)·@Primary·이벤트 매칭은 단위 테스트로는 안 보여서
// 작은 컨텍스트에 두 가짜 빈만 올려 실제로 평가한다 — 앱 전체 컨텍스트·DB는 띄우지 않는다
@DisplayName("로컬 가짜 AI 음악 빈 조건·연결 테스트")
class LocalAiMusicWiringTest {

    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");

    @ParameterizedTest(name = "profile={0}, kafka.consumer.enabled={1}, kafka.env={2}, local-ai={3} → 생성={4}")
    @CsvSource(nullValues = "-", value = {
            // 로컬 개발 — 설정을 주지 않아도(기본 true) 켜진다
            "local, false, dev,  -,     true",
            "local, false, dev,  true,  true",
            // E2E — local 프로필이지만 설정으로 끈다
            "local, false, dev,  false, false",
            // 빈 값은 꺼짐으로 읽힌다 — .env에 값 없이 키만 두면 꺼진다(.env.example 주석의 근거)
            "local, false, dev,  '',    false",
            // 로컬 앱을 test DB에 붙였다 — 가짜 결과로 test DB 카드를 완료하지 않는다
            "local, false, test, true,  false",
            // test·deploy — 프로필이 local이 아니다
            "-,     true,  dev,  true,  false",
            "-,     false, dev,  true,  false",
            // 프로필이 잘못 local로 들어가도 Kafka 구독이 켜진 서버는 만들지 않는다
            "local, true,  dev,  true,  false"
    })
    @DisplayName("네 조건이 모두 맞을 때만 두 가짜 빈이 함께 생긴다")
    void createdOnlyWhenAllConditionsMatch(String profile, boolean kafkaConsumerEnabled, String kafkaEnv,
                                           String localAiEnabled, boolean expected) throws IOException {
        try (AnnotationConfigApplicationContext context =
                     context(profile, kafkaConsumerEnabled, kafkaEnv, localAiEnabled)) {
            assertThat(context.getBeanNamesForType(LocalAiMusicAdapter.class)).hasSize(expected ? 1 : 0);
            assertThat(context.getBeanNamesForType(LocalRecordCardMusicGeneratedListener.class))
                    .hasSize(expected ? 1 : 0);
        }
    }

    @Test
    @DisplayName("로컬에서는 음악 요청 포트에 가짜 어댑터가 꽂힌다(@Primary)")
    void localUsesFakeAdapter() throws IOException {
        try (AnnotationConfigApplicationContext context = context("local", false, "dev", null)) {
            assertThat(context.getBean(RequestRecordCardMusicPort.class)).isInstanceOf(LocalAiMusicAdapter.class);
        }
    }

    @Test
    @DisplayName("local이 아니면 음악 요청 포트는 진짜 아웃박스 어댑터 하나뿐이다")
    void nonLocalUsesOutboxAdapter() throws IOException {
        try (AnnotationConfigApplicationContext context = context(null, true, "dev", "true")) {
            assertThat(context.getBean(RequestRecordCardMusicPort.class))
                    .isInstanceOf(RecordCardMusicOutboxAdapter.class)
                    .isNotInstanceOf(LocalAiMusicAdapter.class);
        }
    }

    @Test
    @DisplayName("요청 → 커밋 → 5초 뒤 예약 실행 → 가짜 수신기 → usecase까지 실제 빈 연결로 이어진다")
    void requestReachesUsecaseThroughEvent() throws IOException {
        try (AnnotationConfigApplicationContext context = context("local", false, "dev", null)) {
            // given
            TransactionSynchronizationManager.initSynchronization();
            try {
                context.getBean(RequestRecordCardMusicPort.class).request(request());

                // when — 커밋하고, 스케줄러에 걸린 작업을 그 자리에서 돌린다
                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit);
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(context.getBean(TaskScheduler.class)).schedule(task.capture(), any(Instant.class));
            task.getValue().run();

            // then
            ArgumentCaptor<CompleteRecordCardMusicCommand> command =
                    ArgumentCaptor.forClass(CompleteRecordCardMusicCommand.class);
            verify(context.getBean(CompleteRecordCardMusicUsecase.class)).handle(command.capture());
            assertThat(command.getValue().recordCardId()).isEqualTo(CARD_ID);
            assertThat(command.getValue().result()).isEqualTo(MusicStatus.COMPLETED);
            assertThat(command.getValue().topic()).isEqualTo("dev.record-card.music.generated");
        }
    }

    private static RecordCardMusicRequest request() {
        return new RecordCardMusicRequest(UUID.randomUUID(), CARD_ID, "한강 한 바퀴", RecordCardActivityType.DAILY,
                new RunningSummary(5020, 1800, 359, null, LocalDateTime.of(2026, 7, 25, 19, 0, 30), 0,
                        new BigDecimal("24.5"), null),
                List.of());
    }

    // 운영과 같은 의존 빈을 올린 뒤 두 가짜를 조건과 함께 등록한다 — 조건은 등록할 때 평가된다
    private static AnnotationConfigApplicationContext context(String profile, boolean kafkaConsumerEnabled,
                                                              String kafkaEnv, String localAiEnabled)
            throws IOException {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (profile != null) {
            context.getEnvironment().setActiveProfiles(profile);
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put("kafka.consumer.enabled", String.valueOf(kafkaConsumerEnabled));
        properties.put("kafka.env", kafkaEnv);
        if (localAiEnabled != null) {
            properties.put("record-card.music.local-ai.enabled", localAiEnabled);
        }
        properties.put("record-card.music.local-ai.music-key", "Night_Walk.wav");
        MutablePropertySources sources = context.getEnvironment().getPropertySources();
        // 설정을 주지 않은 줄은 application.properties의 실제 기본값을 읽는다 — 개발자 셸의 환경 변수·시스템 속성이 끼어들지 않게 뺀다
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("test", properties));
        sources.addLast(new ResourcePropertySource("classpath:application.properties"));

        JsonMapper jsonMapper = JsonMapper.builder().build();
        context.registerBean(RecordCardMusicOutboxAdapter.class, () -> mock(RecordCardMusicOutboxAdapter.class));
        context.registerBean(TaskScheduler.class, () -> mock(TaskScheduler.class));
        context.registerBean(JsonMapper.class, () -> jsonMapper);
        context.registerBean(KafkaNamingProperties.class, () -> new KafkaNamingProperties(Env.DEV));
        context.registerBean(CompleteRecordCardMusicUsecase.class, () -> mock(CompleteRecordCardMusicUsecase.class));
        context.registerBean(RecordCardMusicGeneratedMessageMapper.class,
                () -> new RecordCardMusicGeneratedMessageMapper(jsonMapper));
        context.register(LocalAiMusicAdapter.class, LocalRecordCardMusicGeneratedListener.class);
        context.refresh();
        return context;
    }
}
