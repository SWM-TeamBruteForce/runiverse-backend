package com.runiverse.running_service.unit_test.running.domain.room;

import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.RunningRoomChatHistory;
import com.runiverse.running_service.domain.running.room.exception.NotRunningRoomChatSenderException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatChangedAtRequiredException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatContentRequiredException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatDeletedException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatNotSavedException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatRoomRequiredException;
import com.runiverse.running_service.domain.running.room.exception.RunningRoomChatSenderRequiredException;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatContent;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunningRoomChatTest {

    private static final UUID SENDER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
    private static final UUID OTHER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440013");
    private static final UserId SENDER = new UserId(SENDER_ID);
    private static final UserId OTHER = new UserId(OTHER_ID);
    private static final RunningRoomId ROOM = new RunningRoomId(125L);
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 7, 25, 18, 52);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 25, 18, 55);

    @Nested
    @DisplayName("보내기")
    class Send {

        @Test
        @DisplayName("저장 전 채팅은 ID가 없고, 고친 적도 지운 적도 없다")
        void sendNew() {
            // when
            RunningRoomChat chat = RunningRoomChat.send(ROOM, SENDER, "곧 출발해요");

            // then
            assertThat(chat.getRunningRoomChatId()).isEmpty();
            assertThat(chat.getRunningRoomId()).isEqualTo(ROOM);
            assertThat(chat.getUserId()).isEqualTo(SENDER);
            assertThat(chat.getContent()).isEqualTo(new RunningRoomChatContent("곧 출발해요"));
            assertThat(chat.isEdited()).isFalse();
            assertThat(chat.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("방이나 보낸 사람이 없으면 거부한다")
        void rejectMissingRoomOrSender() {
            // when & then
            assertThatThrownBy(() -> RunningRoomChat.send(null, SENDER, "곧 출발해요"))
                    .isInstanceOf(RunningRoomChatRoomRequiredException.class);
            assertThatThrownBy(() -> RunningRoomChat.send(ROOM, null, "곧 출발해요"))
                    .isInstanceOf(RunningRoomChatSenderRequiredException.class);
        }

        @Test
        @DisplayName("내용이 공백뿐이면 거부한다")
        void rejectBlankContent() {
            // when & then
            assertThatThrownBy(() -> RunningRoomChat.send(ROOM, SENDER, "  "))
                    .isInstanceOf(RunningRoomChatContentRequiredException.class);
        }
    }

    @Nested
    @DisplayName("복원")
    class Restore {

        @Test
        @DisplayName("저장된 수정·삭제 시각을 그대로 되살린다")
        void restoreTimes() {
            // when
            RunningRoomChat chat = RunningRoomChat.restore(3021L, 125L, SENDER_ID, "곧 출발해요", SENT_AT, NOW);

            // then
            assertThat(chat.getRunningRoomChatId()).contains(new RunningRoomChatId(3021L));
            assertThat(chat.getEditedAt()).contains(SENT_AT);
            assertThat(chat.getDeletedAt()).contains(NOW);
            assertThat(chat.isEdited()).isTrue();
            assertThat(chat.isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("수정")
    class Edit {

        @Test
        @DisplayName("내용과 수정 시각을 바꾸고, 바꾸기 직전 내용을 이력으로 돌려준다")
        void editAndKeepPrevious() {
            // given
            RunningRoomChat chat = saved();

            // when
            RunningRoomChatHistory history = chat.edit(SENDER, "5분 늦어요", NOW);

            // then
            assertThat(chat.getContent()).isEqualTo(new RunningRoomChatContent("5분 늦어요"));
            assertThat(chat.getEditedAt()).contains(NOW);
            assertThat(history.getRunningRoomChatId()).isEqualTo(new RunningRoomChatId(3021L));
            assertThat(history.getContent()).isEqualTo(new RunningRoomChatContent("곧 출발해요"));
        }

        @Test
        @DisplayName("연달아 고치면 이력마다 그때의 직전 내용이 담긴다")
        void editTwice() {
            // given
            RunningRoomChat chat = saved();

            // when
            RunningRoomChatHistory first = chat.edit(SENDER, "5분 늦어요", NOW);
            RunningRoomChatHistory second = chat.edit(SENDER, "10분 늦어요", NOW.plusMinutes(1));

            // then
            assertThat(first.getContent().value()).isEqualTo("곧 출발해요");
            assertThat(second.getContent().value()).isEqualTo("5분 늦어요");
            assertThat(chat.getContent().value()).isEqualTo("10분 늦어요");
            assertThat(chat.getEditedAt()).contains(NOW.plusMinutes(1));
        }

        @Test
        @DisplayName("같은 내용으로 고쳐도 수정으로 친다")
        void editWithSameContent() {
            // given
            RunningRoomChat chat = saved();

            // when
            RunningRoomChatHistory history = chat.edit(SENDER, "곧 출발해요", NOW);

            // then
            assertThat(history.getContent().value()).isEqualTo("곧 출발해요");
            assertThat(chat.getEditedAt()).contains(NOW);
        }

        @Test
        @DisplayName("보낸 사람이 아니면 거부하고 내용을 바꾸지 않는다")
        void rejectOtherUser() {
            // given
            RunningRoomChat chat = saved();

            // when & then
            assertThatThrownBy(() -> chat.edit(OTHER, "5분 늦어요", NOW))
                    .isInstanceOf(NotRunningRoomChatSenderException.class);
            assertThat(chat.getContent().value()).isEqualTo("곧 출발해요");
            assertThat(chat.isEdited()).isFalse();
        }

        @Test
        @DisplayName("지운 채팅은 고칠 수 없다")
        void rejectDeleted() {
            // given
            RunningRoomChat chat = saved();
            chat.delete(SENDER, NOW);

            // when & then
            assertThatThrownBy(() -> chat.edit(SENDER, "5분 늦어요", NOW.plusMinutes(1)))
                    .isInstanceOf(RunningRoomChatDeletedException.class);
        }

        @Test
        @DisplayName("새 내용이 공백뿐이면 거부하고 내용을 바꾸지 않는다")
        void rejectBlankContent() {
            // given
            RunningRoomChat chat = saved();

            // when & then
            assertThatThrownBy(() -> chat.edit(SENDER, " ", NOW))
                    .isInstanceOf(RunningRoomChatContentRequiredException.class);
            assertThat(chat.getContent().value()).isEqualTo("곧 출발해요");
            assertThat(chat.isEdited()).isFalse();
        }

        @Test
        @DisplayName("저장 전 채팅이나 수정 시각이 없으면 거부한다")
        void rejectUnsavedOrNoTime() {
            // given
            RunningRoomChat unsaved = RunningRoomChat.send(ROOM, SENDER, "곧 출발해요");

            // when & then
            assertThatThrownBy(() -> unsaved.edit(SENDER, "5분 늦어요", NOW))
                    .isInstanceOf(RunningRoomChatNotSavedException.class);
            assertThatThrownBy(() -> saved().edit(SENDER, "5분 늦어요", null))
                    .isInstanceOf(RunningRoomChatChangedAtRequiredException.class);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("지운 시각만 찍고 내용은 남긴다")
        void softDelete() {
            // given
            RunningRoomChat chat = saved();

            // when
            chat.delete(SENDER, NOW);

            // then
            assertThat(chat.isDeleted()).isTrue();
            assertThat(chat.getDeletedAt()).contains(NOW);
            assertThat(chat.getContent().value()).isEqualTo("곧 출발해요");
        }

        @Test
        @DisplayName("이미 지운 채팅을 다시 지우면 거부하고 처음 지운 시각을 지킨다")
        void rejectDeleteTwice() {
            // given
            RunningRoomChat chat = saved();
            chat.delete(SENDER, NOW);

            // when & then
            assertThatThrownBy(() -> chat.delete(SENDER, NOW.plusMinutes(1)))
                    .isInstanceOf(RunningRoomChatDeletedException.class);
            assertThat(chat.getDeletedAt()).contains(NOW);
        }

        @Test
        @DisplayName("보낸 사람이 아니면 거부한다")
        void rejectOtherUser() {
            // given
            RunningRoomChat chat = saved();

            // when & then
            assertThatThrownBy(() -> chat.delete(OTHER, NOW))
                    .isInstanceOf(NotRunningRoomChatSenderException.class);
            assertThat(chat.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("저장 전 채팅이나 삭제 시각이 없으면 거부한다")
        void rejectUnsavedOrNoTime() {
            // given
            RunningRoomChat unsaved = RunningRoomChat.send(ROOM, SENDER, "곧 출발해요");

            // when & then
            assertThatThrownBy(() -> unsaved.delete(SENDER, NOW))
                    .isInstanceOf(RunningRoomChatNotSavedException.class);
            assertThatThrownBy(() -> saved().delete(SENDER, null))
                    .isInstanceOf(RunningRoomChatChangedAtRequiredException.class);
        }
    }

    @Test
    @DisplayName("보낸 사람인지 가린다")
    void isSentBy() {
        // given
        RunningRoomChat chat = saved();

        // when & then
        assertThat(chat.isSentBy(SENDER)).isTrue();
        assertThat(chat.isSentBy(OTHER)).isFalse();
    }

    private static RunningRoomChat saved() {
        return RunningRoomChat.restore(3021L, 125L, SENDER_ID, "곧 출발해요", null, null);
    }
}
