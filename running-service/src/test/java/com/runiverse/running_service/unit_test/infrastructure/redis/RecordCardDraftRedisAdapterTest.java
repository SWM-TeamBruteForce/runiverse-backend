package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.UploadType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.infrastructure.redis.recordcard.RecordCardDraftRedisAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 초안 Redis 어댑터 단위 테스트")
class RecordCardDraftRedisAdapterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID DRAFT_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400d1");
    private static final Duration TTL = Duration.ofMinutes(30);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RecordCardDraftRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        // 잠금 해제·덮어쓰기는 opsForValue를 쓰지 않는다
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        adapter = new RecordCardDraftRedisAdapter(redisTemplate, JSON);
    }

    @Test
    @DisplayName("초안 ID 키에 유효 시간을 걸어 저장한다")
    void saveWithKeyAndTtl() {
        // when
        adapter.save(draft(), TTL);

        // then
        verify(valueOperations).set(eq("record-card:draft:" + DRAFT_ID), anyString(), eq(TTL));
    }

    @Test
    @DisplayName("저장한 JSON을 다시 읽으면 같은 초안이다 — 확정(9-2)이 그대로 읽어 쓴다")
    void savedJsonRoundTrips() {
        // given
        RecordCardDraft draft = draft();

        // when
        adapter.save(draft, TTL);

        // then
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(anyString(), json.capture(), any(Duration.class));
        assertThat(JSON.readValue(json.getValue(), RecordCardDraft.class)).isEqualTo(draft);
    }

    @Test
    @DisplayName("Redis 저장에 실패하면 삼키지 않고 던진다 — 저장 못 한 초안으로 업로드 URL을 주면 안 된다")
    void propagateFailure() {
        // given
        willThrow(new RedisConnectionFailureException("down"))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        // when & then
        assertThatThrownBy(() -> adapter.save(draft(), TTL))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("저장된 초안을 읽고, 없으면(만료 포함) 비어 있다")
    void load() {
        // given
        RecordCardDraft draft = draft();
        when(valueOperations.get("record-card:draft:" + DRAFT_ID)).thenReturn(JSON.writeValueAsString(draft));

        // when & then
        assertThat(adapter.load(DRAFT_ID)).contains(draft);
        assertThat(adapter.load(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("잠금은 초안과 다른 key에 유효 시간을 걸어 키가 없을 때만 쓰고, 잡으면 토큰을 준다")
    void tryLockAcquires() {
        // given
        when(valueOperations.setIfAbsent(eq("record-card:draft:" + DRAFT_ID + ":lock"), anyString(),
                eq(Duration.ofSeconds(30)))).thenReturn(true);

        // when
        Optional<String> token = adapter.tryLock(DRAFT_ID, Duration.ofSeconds(30));

        // then
        assertThat(token).isPresent();
        verify(valueOperations).setIfAbsent("record-card:draft:" + DRAFT_ID + ":lock", token.get(),
                Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("이미 누가 잡고 있으면 기다리지 않고 비어 있다")
    void tryLockFailsWhenHeld() {
        // given
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        // when & then
        assertThat(adapter.tryLock(DRAFT_ID, Duration.ofSeconds(30))).isEmpty();
    }

    @Test
    @DisplayName("풀 때는 잠금 key와 내 토큰을 스크립트에 넘긴다 — 내 토큰일 때만 지운다")
    @SuppressWarnings("unchecked")
    void unlockPassesKeyAndToken() {
        // when
        adapter.unlock(DRAFT_ID, "my-token");

        // then
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(script.capture(), eq(List.of("record-card:draft:" + DRAFT_ID + ":lock")),
                eq("my-token"));
        assertThat(script.getValue().getScriptAsString()).contains("GET").contains("DEL").contains("ARGV[1]");
    }

    @Test
    @DisplayName("덮어쓰기는 있을 때만(XX) 쓰고 남은 유효 시간을 그대로 둔다(KEEPTTL)")
    @SuppressWarnings("unchecked")
    void updateKeepsTtl() throws Exception {
        // given
        RecordCardDraft draft = draft();
        RedisConnection connection = org.mockito.Mockito.mock(RedisConnection.class);
        RedisStringCommands stringCommands = org.mockito.Mockito.mock(RedisStringCommands.class);
        when(connection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.set(any(byte[].class), any(byte[].class), any(Expiration.class), any(SetOption.class)))
                .thenReturn(true);
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenAnswer(invocation -> ((RedisCallback<Boolean>) invocation.getArgument(0)).doInRedis(connection));

        // when
        boolean updated = adapter.update(draft);

        // then
        assertThat(updated).isTrue();
        ArgumentCaptor<byte[]> value = ArgumentCaptor.forClass(byte[].class);
        verify(stringCommands).set(eq(("record-card:draft:" + DRAFT_ID).getBytes(StandardCharsets.UTF_8)),
                value.capture(), eq(Expiration.keepTtl()), eq(SetOption.ifPresent()));
        assertThat(JSON.readValue(new String(value.getValue(), StandardCharsets.UTF_8), RecordCardDraft.class))
                .isEqualTo(draft);
    }

    @Test
    @DisplayName("초안이 그사이 만료돼 없으면 새로 만들지 않고 false")
    @SuppressWarnings("unchecked")
    void updateReturnsFalseWhenGone() {
        // given
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(false);

        // when & then
        assertThat(adapter.update(draft())).isFalse();
    }

    private static RecordCardDraft draft() {
        UUID cardId = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
        UUID userId = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
        return new RecordCardDraft(DRAFT_ID, RecordCardDraft.DraftType.CREATE, cardId, userId, 501L, RecordCardActivityType.TRAVEL,
                "바람이 좋았다", RecordCardVisibility.FRIENDS, List.of(
                new DraftImage(UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d"), "k/1/image.jpg",
                        "image/jpeg", 204800L, "노을", UploadType.SINGLE, null, null, false),
                new DraftImage(UUID.fromString("01a0f2d4-6180-7000-8000-44665544013e"), "k/2/image.png",
                        "image/png", 20_971_521L, null, UploadType.MULTIPART, "s3-upload-id", 3, false)),
                1, false);
    }
}
