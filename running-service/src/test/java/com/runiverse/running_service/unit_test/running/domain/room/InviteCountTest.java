package com.runiverse.running_service.unit_test.running.domain.room;

import com.runiverse.running_service.domain.running.room.exception.InvalidInviteCountException;
import com.runiverse.running_service.domain.running.room.vo.InviteCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InviteCountTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 100})
    @DisplayName("1 이상이면 만들 수 있다")
    void createInviteCountSuccess(int value) {
        // when & then -> 상한은 운영 설정이라 VO가 막지 않는다
        assertThat(new InviteCount(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    @DisplayName("1 미만이면 예외가 발생한다")
    void createInviteCountUnderOneFails(int value) {
        // when & then -> 행은 보낼 때 생기므로 0번 보낸 행은 없다
        assertThatThrownBy(() -> new InviteCount(value))
                .isInstanceOf(InvalidInviteCountException.class);
    }

    @Test
    @DisplayName("처음 보낸 행은 1에서 시작한다")
    void firstStartsAtOne() {
        // when & then
        assertThat(InviteCount.first().value()).isEqualTo(1);
    }

    @Test
    @DisplayName("다시 보낼 때마다 하나씩 오르고 원본은 바뀌지 않는다")
    void increaseAddsOne() {
        // given
        InviteCount count = InviteCount.first();

        // when
        InviteCount increased = count.increase();

        // then
        assertThat(increased.value()).isEqualTo(2);
        assertThat(count.value()).isEqualTo(1);
    }

    @Test
    @DisplayName("상한과 같거나 넘으면 닿은 것이다")
    void hasReachedAtOrAboveMax() {
        // when & then
        assertThat(new InviteCount(4).hasReached(5)).isFalse();
        assertThat(new InviteCount(5).hasReached(5)).isTrue();
        assertThat(new InviteCount(6).hasReached(5)).isTrue();   // 상한을 낮춘 운영 변경
    }
}
