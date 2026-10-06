package com.runiverse.running_service.unit_test.running.domain.player;

import com.runiverse.running_service.domain.running.player.exception.InvalidDesiredPlayerCountException;
import com.runiverse.running_service.domain.running.player.vo.DesiredPlayerCount;
import com.runiverse.running_service.domain.running.room.vo.PlayerCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DesiredPlayerCountTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @ParameterizedTest
        @ValueSource(ints = {2, 3, 4})
        @DisplayName("2명 이상 방 정원 이하면 만들 수 있다")
        void createSuccess(int value) {
            // when
            DesiredPlayerCount count = new DesiredPlayerCount(value);

            // then
            assertThat(count.value()).isEqualTo(value);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 0, -1, Integer.MIN_VALUE})
        @DisplayName("2명 미만이면 예외가 발생한다")
        void createBelowMinFails(int value) {
            // when & then -> 1명은 매칭이 아니라 솔로다
            assertThatThrownBy(() -> new DesiredPlayerCount(value))
                    .isInstanceOf(InvalidDesiredPlayerCountException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {5, 100, Integer.MAX_VALUE})
        @DisplayName("방 정원을 넘으면 예외가 발생한다")
        void createAboveMaxFails(int value) {
            // when & then -> 방이 못 받는 인원을 희망값으로 저장해두면 배정 때 갈 곳이 없다
            assertThatThrownBy(() -> new DesiredPlayerCount(value))
                    .isInstanceOf(InvalidDesiredPlayerCountException.class);
        }

        @Test
        @DisplayName("같은 값이면 같은 희망 인원으로 취급한다")
        void isValueBased() {
            // when & then
            assertThat(new DesiredPlayerCount(4)).isEqualTo(new DesiredPlayerCount(4));
        }
    }

    @Nested
    @DisplayName("기본값·상한 테스트")
    class BoundaryTest {

        @Test
        @DisplayName("인원을 고르지 않은 신청은 4명으로 저장된다")
        void defaultCountIsFour() {
            // when & then -> 1차에는 입력 UI가 없어 항상 이 값이다
            assertThat(DesiredPlayerCount.defaultCount().value()).isEqualTo(4);
        }

        @Test
        @DisplayName("기본값은 방이 열리는 정원과 같다")
        void defaultCountMatchesRoomCapacity() {
            // when & then -> 인원 선택 UI가 생기면 이 값이 그대로 방의 max_player_count가 된다
            assertThat(DesiredPlayerCount.defaultCount().value())
                    .isEqualTo(PlayerCount.MAX_PLAYER);
        }

        @Test
        @DisplayName("희망 인원의 상한은 방 정원 상한을 그대로 따른다")
        void upperBoundFollowsRoomCapacity() {
            // when & then -> DesiredPlayerCount(player)가 PlayerCount(room)의 상수를 참조하는 유일한 지점이다.
            //                애그리거트를 가로지르는 결합이라 정원이 바뀌면 여기서 먼저 걸린다
            assertThat(new DesiredPlayerCount(PlayerCount.MAX_PLAYER).value())
                    .isEqualTo(PlayerCount.MAX_PLAYER);
            assertThatThrownBy(() -> new DesiredPlayerCount(PlayerCount.MAX_PLAYER + 1))
                    .isInstanceOf(InvalidDesiredPlayerCountException.class);
        }
    }
}
