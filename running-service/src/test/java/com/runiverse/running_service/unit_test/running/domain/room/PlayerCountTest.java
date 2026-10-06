package com.runiverse.running_service.unit_test.running.domain.room;

import com.runiverse.running_service.domain.running.room.exception.InvalidPlayerCountException;
import com.runiverse.running_service.domain.running.room.exception.RoomIsEmptyException;
import com.runiverse.running_service.domain.running.room.exception.RoomIsFullException;
import com.runiverse.running_service.domain.running.room.vo.PlayerCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PlayerCountTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @ParameterizedTest
        @CsvSource({"0,1", "1,1", "1,4", "4,4", "2,3"})
        @DisplayName("현재 인원이 정원 이하면 만들 수 있다")
        void createPlayerCountSuccess(int current, int max) {
            // when
            PlayerCount playerCount = new PlayerCount(current, max);

            // then
            assertThat(playerCount.current()).isEqualTo(current);
            assertThat(playerCount.max()).isEqualTo(max);
        }

        @ParameterizedTest
        @CsvSource({"2,1", "5,4", "1,0", "1,-1", "-1,4", "5,5", "1,5"})
        @DisplayName("정원을 넘거나 음수면 예외가 발생한다")
        void createInvalidPlayerCountFails(int current, int max) {
            // when & then -> 정원 초과 상태가 애초에 만들어지지 못하게 막는다
            assertThatThrownBy(() -> new PlayerCount(current, max))
                    .isInstanceOf(InvalidPlayerCountException.class);
        }

        @Test
        @DisplayName("솔로 방은 1인 정원으로 만들어진다")
        void soloRoomHasSingleSeat() {
            // when
            PlayerCount playerCount = PlayerCount.solo();

            // then
            assertThat(playerCount.current()).isEqualTo(1);
            assertThat(playerCount.max()).isEqualTo(1);
            assertThat(playerCount.isFull()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4})
        @DisplayName("방은 언제나 1인으로 열린다")
        void roomOpensWithSinglePlayer(int max) {
            // when
            PlayerCount playerCount = PlayerCount.openWith(max);

            // then -> 신청 즉시 1인 방이 생긴다는 규칙이 여기서 지켜진다
            assertThat(playerCount.current()).isEqualTo(1);
            assertThat(playerCount.max()).isEqualTo(max);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 5, -1})
        @DisplayName("서비스 정원을 벗어난 방은 열 수 없다")
        void openWithInvalidMaxFails(int max) {
            // when & then
            assertThatThrownBy(() -> PlayerCount.openWith(max))
                    .isInstanceOf(InvalidPlayerCountException.class);
        }
    }

    @Nested
    @DisplayName("참가 테스트")
    class JoinTest {

        @Test
        @DisplayName("빈자리가 있으면 인원이 하나 늘어난다")
        void joinIncreasesCurrent() {
            // given
            PlayerCount playerCount = PlayerCount.openWith(4);

            // when
            PlayerCount joined = playerCount.join();

            // then
            assertThat(joined.current()).isEqualTo(2);
            assertThat(joined.max()).isEqualTo(4);
        }

        @Test
        @DisplayName("참가는 원본을 바꾸지 않는다")
        void joinDoesNotMutate() {
            // given
            PlayerCount playerCount = PlayerCount.openWith(4);

            // when
            playerCount.join();

            // then -> 값 객체라 갱신은 새 인스턴스로만 일어난다
            assertThat(playerCount.current()).isEqualTo(1);
        }

        @Test
        @DisplayName("정원까지 채우면 더 들어올 수 없다")
        void joinBeyondCapacityFails() {
            // given
            PlayerCount full = PlayerCount.openWith(4).join().join().join();

            // when & then -> 정원 초과가 여기서 막히지 않으면 5명이 한 방에서 달린다
            assertThat(full.current()).isEqualTo(4);
            assertThat(full.isFull()).isTrue();
            assertThat(full.canJoin()).isFalse();
            assertThatThrownBy(full::join)
                    .isInstanceOf(RoomIsFullException.class);
        }

        @Test
        @DisplayName("솔로 방에는 아무도 들어올 수 없다")
        void soloRoomRejectsJoin() {
            // when & then
            assertThatThrownBy(() -> PlayerCount.solo().join())
                    .isInstanceOf(RoomIsFullException.class);
        }
    }

    @Nested
    @DisplayName("이탈 테스트")
    class LeaveTest {

        @Test
        @DisplayName("이탈하면 인원이 하나 줄어든다")
        void leaveDecreasesCurrent() {
            // given
            PlayerCount playerCount = PlayerCount.openWith(4).join();

            // when
            PlayerCount left = playerCount.leave();

            // then
            assertThat(left.current()).isEqualTo(1);
            assertThat(left.max()).isEqualTo(4);
        }

        @Test
        @DisplayName("마지막 한 명도 이탈할 수 있다")
        void lastPlayerCanLeave() {
            // when
            PlayerCount empty = PlayerCount.openWith(4).leave();

            // then -> 0명은 허용하고, 방을 취소할지는 애그리거트가 판단한다
            assertThat(empty.current()).isZero();
        }

        @Test
        @DisplayName("아무도 없으면 이탈할 수 없다")
        void leaveFromEmptyRoomFails() {
            // given
            PlayerCount empty = PlayerCount.openWith(4).leave();

            // when & then -> 중복 이탈 이벤트가 인원을 음수로 만들지 못하게 막는다
            assertThatThrownBy(empty::leave)
                    .isInstanceOf(RoomIsEmptyException.class);
        }

        @Test
        @DisplayName("참가와 이탈을 반복해도 원래 인원으로 돌아온다")
        void joinAndLeaveRoundTrip() {
            // given
            PlayerCount playerCount = PlayerCount.openWith(4);

            // when
            PlayerCount result = playerCount.join().join().leave().leave();

            // then
            assertThat(result).isEqualTo(playerCount);
        }

        @Test
        @DisplayName("이탈하면 다시 빈자리가 생긴다")
        void leaveOpensSeatAgain() {
            // given
            PlayerCount full = PlayerCount.openWith(2).join();

            // when
            PlayerCount left = full.leave();

            // then
            assertThat(full.canJoin()).isFalse();
            assertThat(left.canJoin()).isTrue();
        }
    }
}
