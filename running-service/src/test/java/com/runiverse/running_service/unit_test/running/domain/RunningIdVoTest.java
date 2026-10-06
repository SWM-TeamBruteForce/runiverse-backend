package com.runiverse.running_service.unit_test.running.domain;

import com.runiverse.running_service.domain.running.player.exception.InvalidRunningPlayerIdException;
import com.runiverse.running_service.domain.running.record.exception.InvalidRunningRecordIdException;
import com.runiverse.running_service.domain.running.room.exception.InvalidRunningRoomIdException;
import com.runiverse.running_service.domain.running.record.exception.InvalidSplitIdException;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.record.vo.RunningRecordId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.record.vo.RunningSplitId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningIdVoTest {

    @Test
    @DisplayName("모든 식별자는 0과 음수를 거부한다")
    void allIdsRejectZeroAndNegative() {
        // when & then -> bigserial은 1부터 시작하므로 0은 존재할 수 없는 PK다.
        //                넷 중 하나만 규칙이 달라지면 여기서 걸린다
        assertThatThrownBy(() -> new RunningRoomId(0L))
                .isInstanceOf(InvalidRunningRoomIdException.class);
        assertThatThrownBy(() -> new RunningPlayerId(0L))
                .isInstanceOf(InvalidRunningPlayerIdException.class);
        assertThatThrownBy(() -> new RunningRecordId(0L))
                .isInstanceOf(InvalidRunningRecordIdException.class);
        assertThatThrownBy(() -> new RunningSplitId(0L))
                .isInstanceOf(InvalidSplitIdException.class);
    }

    @Test
    @DisplayName("모든 식별자는 null을 거부한다")
    void allIdsRejectNull() {
        // when & then -> "아직 저장 안 됨"은 애그리거트의 필드가 null로 표현하고,
        //                VO 자체는 언제나 유효한 값만 담는다
        assertThatThrownBy(() -> new RunningRoomId(null))
                .isInstanceOf(InvalidRunningRoomIdException.class);
        assertThatThrownBy(() -> new RunningPlayerId(null))
                .isInstanceOf(InvalidRunningPlayerIdException.class);
        assertThatThrownBy(() -> new RunningRecordId(null))
                .isInstanceOf(InvalidRunningRecordIdException.class);
        assertThatThrownBy(() -> new RunningSplitId(null))
                .isInstanceOf(InvalidSplitIdException.class);
    }

    @Test
    @DisplayName("모든 식별자는 bigint 최대값까지 담는다")
    void allIdsAcceptBigintRange() {
        // when & then -> Integer로 바꾸면 여기서 깨진다
        assertThat(new RunningRoomId(Long.MAX_VALUE).value()).isEqualTo(Long.MAX_VALUE);
        assertThat(new RunningPlayerId(Long.MAX_VALUE).value()).isEqualTo(Long.MAX_VALUE);
        assertThat(new RunningRecordId(Long.MAX_VALUE).value()).isEqualTo(Long.MAX_VALUE);
        assertThat(new RunningSplitId(Long.MAX_VALUE).value()).isEqualTo(Long.MAX_VALUE);
    }

    @Nested
    @DisplayName("러닝방 ID 테스트")
    class RunningRoomIdTest {

        @ParameterizedTest
        @ValueSource(longs = {1L, 125L, 9_999_999_999L})
        @DisplayName("1 이상이면 만들 수 있다")
        void createSuccess(long value) {
            // when & then
            assertThat(new RunningRoomId(value).value()).isEqualTo(value);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
        @DisplayName("1보다 작으면 예외가 발생한다")
        void createFails(long value) {
            // when & then
            assertThatThrownBy(() -> new RunningRoomId(value))
                    .isInstanceOf(InvalidRunningRoomIdException.class);
        }

        @Test
        @DisplayName("같은 값이면 같은 식별자로 취급한다")
        void isValueBased() {
            // when & then -> 세션이 소속 플레이어를 찾을 때 값 동등성에 기댄다
            assertThat(new RunningRoomId(125L)).isEqualTo(new RunningRoomId(125L));
        }
    }

    @Nested
    @DisplayName("러닝 참가자 ID 테스트")
    class RunningPlayerIdTest {

        @ParameterizedTest
        @ValueSource(longs = {1L, 42L})
        @DisplayName("1 이상이면 만들 수 있다")
        void createSuccess(long value) {
            // when & then
            assertThat(new RunningPlayerId(value).value()).isEqualTo(value);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("1보다 작으면 예외가 발생한다")
        void createFails(long value) {
            // when & then
            assertThatThrownBy(() -> new RunningPlayerId(value))
                    .isInstanceOf(InvalidRunningPlayerIdException.class);
        }

        @Test
        @DisplayName("같은 값이면 같은 식별자로 취급한다")
        void isValueBased() {
            // when & then -> RoomSession.isSamePlayer가 이 동등성으로 판별한다
            assertThat(new RunningPlayerId(42L)).isEqualTo(new RunningPlayerId(42L));
        }
    }

    @Nested
    @DisplayName("러닝 기록 ID 테스트")
    class RunningRecordIdTest {

        @ParameterizedTest
        @ValueSource(longs = {1L, 77L})
        @DisplayName("1 이상이면 만들 수 있다")
        void createSuccess(long value) {
            // when & then
            assertThat(new RunningRecordId(value).value()).isEqualTo(value);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("1보다 작으면 예외가 발생한다")
        void createFails(long value) {
            // when & then
            assertThatThrownBy(() -> new RunningRecordId(value))
                    .isInstanceOf(InvalidRunningRecordIdException.class);
        }
    }

    @Nested
    @DisplayName("구간 ID 테스트")
    class RunningSplitIdTest {

        @ParameterizedTest
        @ValueSource(longs = {1L, 300L})
        @DisplayName("1 이상이면 만들 수 있다")
        void createSuccess(long value) {
            // when & then
            assertThat(new RunningSplitId(value).value()).isEqualTo(value);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("1보다 작으면 예외가 발생한다")
        void createFails(long value) {
            // when & then
            assertThatThrownBy(() -> new RunningSplitId(value))
                    .isInstanceOf(InvalidSplitIdException.class);
        }
    }
}
