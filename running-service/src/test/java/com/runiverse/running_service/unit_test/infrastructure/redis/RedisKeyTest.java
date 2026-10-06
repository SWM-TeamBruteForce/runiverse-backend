package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.runiverse.running_service.infrastructure.redis.RedisKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisKeyTest {

    @Test
    @DisplayName("세그먼트를 콜론으로 이어 키를 만든다")
    void ofJoinsSegments() {
        // given
        String userId = "019f9908-d2d1-7e61-96ae-657286acc3bf";

        // when
        String key = RedisKey.USER.of(userId, "refresh_token");

        // then
        assertThat(key).isEqualTo("user:019f9908-d2d1-7e61-96ae-657286acc3bf:refresh_token");
    }

    @Test
    @DisplayName("세그먼트가 하나여도 접두사 뒤에 콜론으로 이어진다")
    void ofWithSingleSegment() {
        // when
        String key = RedisKey.USER.of("abc");

        // then
        assertThat(key).isEqualTo("user:abc");
    }

    @Test
    @DisplayName("같은 세그먼트에 대해 항상 같은 키를 만든다")
    void ofIsDeterministic() {
        // when
        String first = RedisKey.USER.of("abc", "refresh_token");
        String second = RedisKey.USER.of("abc", "refresh_token");

        // then
        assertThat(first).isEqualTo(second);
    }
}
