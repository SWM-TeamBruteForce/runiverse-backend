package com.runiverse.running_service.unit_test.running.domain.room;

import com.runiverse.running_service.domain.running.room.exception.RunningRoomTypeRequiredException;
import com.runiverse.running_service.domain.running.room.exception.UnsupportedRunningRoomTypeException;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningRoomTypeTest {

    @Nested
    @DisplayName("문자열 변환 테스트")
    class FromTest {

        @ParameterizedTest
        @EnumSource(RunningRoomType.class)
        @DisplayName("모든 종류는 자기 이름으로 변환된다")
        void fromExactNameSuccess(RunningRoomType type) {
            // when & then -> 값이 추가돼도 변환이 빠지지 않게 한다
            assertThat(RunningRoomType.from(type.name())).isEqualTo(type);
        }

        @ParameterizedTest
        @ValueSource(strings = {"solo", "Solo", "sOlO"})
        @DisplayName("대소문자를 가리지 않는다")
        void fromIsCaseInsensitive(String value) {
            // when & then
            assertThat(RunningRoomType.from(value)).isEqualTo(RunningRoomType.SOLO);
        }

        @ParameterizedTest
        @ValueSource(strings = {"  MATCH", "MATCH  ", "\tMATCH\n"})
        @DisplayName("앞뒤 공백은 제거된다")
        void fromTrimsWhitespace(String value) {
            // when & then
            assertThat(RunningRoomType.from(value)).isEqualTo(RunningRoomType.MATCH);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("null이거나 공백뿐이면 예외가 발생한다")
        void fromBlankValueFails(String value) {
            // when & then
            assertThatThrownBy(() -> RunningRoomType.from(value))
                    .isInstanceOf(RunningRoomTypeRequiredException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"RANDOM", "MATCHING", "SOLO_RUN", "1"})
        @DisplayName("정의되지 않은 값이면 예외가 발생한다")
        void fromUnknownValueFails(String value) {
            // when & then -> 클라이언트 오타가 조용히 다른 종류로 저장되지 않게 한다
            assertThatThrownBy(() -> RunningRoomType.from(value))
                    .isInstanceOf(UnsupportedRunningRoomTypeException.class);
        }
    }

    @Nested
    @DisplayName("초기 상태 테스트")
    class InitialStatusTest {

        @Test
        @DisplayName("솔로는 모집 단계 없이 확정 상태로 태어난다")
        void soloStartsMatched() {
            // when & then -> 태어나는 지점만 다르고 채널 입장 이후 흐름은 매칭과 같다
            assertThat(RunningRoomType.SOLO.initialStatus())
                    .isEqualTo(RunningRoomStatus.MATCHED);
        }

        @Test
        @DisplayName("매칭·초대는 모집 중으로 태어난다")
        void othersStartMatching() {
            // when & then
            assertThat(RunningRoomType.MATCH.initialStatus())
                    .isEqualTo(RunningRoomStatus.MATCHING);
            assertThat(RunningRoomType.INVITE.initialStatus())
                    .isEqualTo(RunningRoomStatus.MATCHING);
        }
    }

    @Nested
    @DisplayName("매칭 대상 판별 테스트")
    class MatchableTest {

        @Test
        @DisplayName("랜덤 매칭만 후보 방 스캔 대상이다")
        void onlyMatchTypeIsMatchable() {
            // when & then -> 솔로 방·초대방이 후보에 섞이면 모르는 사람이 남의 방에 들어간다
            assertThat(RunningRoomType.MATCH.isMatchable()).isTrue();
            assertThat(RunningRoomType.SOLO.isMatchable()).isFalse();
            assertThat(RunningRoomType.INVITE.isMatchable()).isFalse();
        }
    }
}
