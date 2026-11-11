package com.runiverse.running_service.unit_test.running.domain.room;

import com.runiverse.running_service.domain.running.room.exception.InvalidRunningRoomChatIdException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatContentRequiredException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatContentTooLongException;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatContent;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunningRoomChatVoTest {

    @Nested
    @DisplayName("채팅 ID")
    class Id {

        @ParameterizedTest
        @NullSource
        @ValueSource(longs = {0L, -1L})
        @DisplayName("비었거나 1보다 작으면 거부한다")
        void rejectInvalid(Long value) {
            // when & then
            assertThatThrownBy(() -> new RunningRoomChatId(value))
                    .isInstanceOf(InvalidRunningRoomChatIdException.class);
        }

        @Test
        @DisplayName("1 이상이면 받는다")
        void acceptPositive() {
            // when & then
            assertThat(new RunningRoomChatId(1L).value()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("채팅 내용")
    class Content {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\n\t"})
        @DisplayName("비었거나 공백만 있으면 거부한다")
        void rejectBlank(String value) {
            // when & then
            assertThatThrownBy(() -> new RunningRoomChatContent(value))
                    .isInstanceOf(RunningRoomChatContentRequiredException.class);
        }

        @Test
        @DisplayName("200자까지 받고 넘으면 거부한다")
        void limitLength() {
            // given
            String max = "가".repeat(200);
            String over = "가".repeat(201);

            // when & then
            assertThat(new RunningRoomChatContent(max).value()).isEqualTo(max);
            assertThatThrownBy(() -> new RunningRoomChatContent(over))
                    .isInstanceOf(RunningRoomChatContentTooLongException.class);
        }

        @Test
        @DisplayName("앞뒤 공백을 지우지 않고 쓴 그대로 둔다")
        void keepAsWritten() {
            // when & then
            assertThat(new RunningRoomChatContent("  곧 출발해요 ").value()).isEqualTo("  곧 출발해요 ");
        }
    }
}
