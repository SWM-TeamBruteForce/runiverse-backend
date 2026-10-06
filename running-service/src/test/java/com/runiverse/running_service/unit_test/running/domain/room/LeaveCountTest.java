package com.runiverse.running_service.unit_test.running.domain.room;

import com.runiverse.running_service.domain.running.room.exception.InvalidLeaveCountException;
import com.runiverse.running_service.domain.running.room.vo.LeaveCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LeaveCountTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 100})
    @DisplayName("0 이상이면 만들 수 있다")
    void createLeaveCountSuccess(int value) {
        // when & then -> 네트워크가 계속 끊기면 수십 번도 오를 수 있어 상한을 두지 않는다
        assertThat(new LeaveCount(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    @DisplayName("음수면 예외가 발생한다")
    void createNegativeLeaveCountFails(int value) {
        // when & then
        assertThatThrownBy(() -> new LeaveCount(value))
                .isInstanceOf(InvalidLeaveCountException.class);
    }

    @Test
    @DisplayName("처음 맺은 관계는 0에서 시작한다")
    void zeroStartsAtNone() {
        // when & then
        assertThat(LeaveCount.zero().value()).isZero();
    }

    @Test
    @DisplayName("끊길 때마다 하나씩 오른다")
    void increaseAddsOne() {
        // given
        LeaveCount count = LeaveCount.zero();

        // when
        LeaveCount increased = count.increase();

        // then
        assertThat(increased.value()).isEqualTo(1);
    }

    @Test
    @DisplayName("증가는 원본을 바꾸지 않는다")
    void increaseDoesNotMutate() {
        // given
        LeaveCount count = LeaveCount.zero();

        // when
        count.increase();

        // then -> 값 객체라 갱신은 새 인스턴스로만 일어난다
        assertThat(count.value()).isZero();
    }

    @Test
    @DisplayName("여러 번 끊겨도 누적된다")
    void increaseAccumulates() {
        // when
        LeaveCount count = LeaveCount.zero().increase().increase().increase();

        // then -> 페널티 판정이 이 누적값을 근거로 삼는다
        assertThat(count.value()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 횟수면 같은 값으로 취급한다")
    void isValueBased() {
        // when & then
        assertThat(new LeaveCount(2)).isEqualTo(LeaveCount.zero().increase().increase());
    }
}
