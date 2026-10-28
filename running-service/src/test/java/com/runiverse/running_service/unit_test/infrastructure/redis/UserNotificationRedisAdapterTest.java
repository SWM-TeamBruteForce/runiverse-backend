package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.notification.UserNotificationRedisAdapter;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 전파 어댑터 단위 테스트")
class UserNotificationRedisAdapterTest {

    private static final UUID RECIPIENT_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private StringRedisTemplate redisTemplate;

    private UserNotificationRedisAdapter adapter;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        adapter = new UserNotificationRedisAdapter(redisTemplate, JSON);
        log = LogCapture.of(UserNotificationRedisAdapter.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("모든 인스턴스가 듣는 하나의 채널에 받는 사람과 알림 ID만 싣는다")
    void publishesIdsOnSingleChannel() {
        // when
        adapter.publish(new UserId(RECIPIENT_ID), 912L);

        // then -> 알림 내용은 싣지 않는다. 받는 사람이 붙은 인스턴스만 DB에서 읽어 조립한다
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("notification:user"), body.capture());
        JsonNode json = JSON.readTree(body.getValue());
        assertThat(json.get("userId").asString()).isEqualTo(RECIPIENT_ID.toString());
        assertThat(json.get("notificationId").asLong()).isEqualTo(912L);
        assertThat(json.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Redis가 닿지 않으면 던지지 않고 알림 ID와 원인 예외를 담아 ERROR로 남긴다")
    void logsRedisFailure() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(redisTemplate).convertAndSend(anyString(), any());

        // when -> 이미 커밋된 뒤라 되돌릴 것이 없고, 알림은 DB에 있어 다음 목록 조회가 보여 준다
        assertThatCode(() -> adapter.publish(new UserId(RECIPIENT_ID), 912L)).doesNotThrowAnyException();

        // then
        assertThat(log.messages(Level.ERROR)).containsExactly("[알림] 알림 전파 실패: Redis 오류 - notificationId=912");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy().getClassName())
                .isEqualTo(RedisConnectionFailureException.class.getName());
    }
}
