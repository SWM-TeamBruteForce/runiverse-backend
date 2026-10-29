package com.runiverse.running_service.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final int PUSH_THREADS = 4;
    private static final int PUSH_QUEUE_CAPACITY = 100;
    private static final int PUSH_SHUTDOWN_WAIT_SECONDS = 10;

    // FCM 응답을 요청·예약 스레드가 기다리지 않게 푸시는 이 스레드들이 보낸다.
    // 발송이 DB·Redis를 읽으므로 그 둘보다 먼저 닫혀야 종료 때 대기 줄을 마저 보낼 수 있다.
    // 이름으로만 쓰이게 둔다 — 실행기를 모두 주입받는 JPA 설정이 이 풀에 매이면 JPA가 먼저 닫힌다
    @Bean(defaultCandidate = false)
    @DependsOn({"entityManagerFactory", "redisConnectionFactory"})
    public ThreadPoolTaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(PUSH_THREADS);
        executor.setMaxPoolSize(PUSH_THREADS);
        executor.setQueueCapacity(PUSH_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("push-");
        // 넘치면 버린다 — 호출한 스레드가 대신 보내면 요청·예약 스레드가 다시 FCM을 기다린다
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                log.warn("[기기] 푸시 발송 건너뜀: 종료 중");
                return;
            }
            log.warn("[기기] 푸시 발송 건너뜀: 발송 대기 줄 가득 참 - queueCapacity={}", PUSH_QUEUE_CAPACITY);
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(PUSH_SHUTDOWN_WAIT_SECONDS);
        return executor;
    }
}
