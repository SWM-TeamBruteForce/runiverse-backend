package com.runiverse.running_service.unit_test.recordcard.domain;

import com.runiverse.running_service.domain.recordcard.exception.InvalidDisplayOrderException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidMusicResultException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidRecordCardIdException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidRecordCardImageIdException;
import com.runiverse.running_service.domain.recordcard.exception.MusicKeyRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.MusicKeyTooLongException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageCaptionEmptyException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageCaptionTooLongException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageKeyRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageKeyTooLongException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardMemoRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardMemoTooLongException;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicKey;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageCaption;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageKey;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardMemo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordCardVoTest {

    @Nested
    @DisplayName("식별자")
    class Ids {

        @Test
        @DisplayName("카드·사진 ID는 null을 거부한다")
        void rejectNull() {
            // when & then -> ID는 작성 요청이 정하므로 저장 전에도 있다
            assertThatThrownBy(() -> new RecordCardId(null))
                    .isInstanceOf(InvalidRecordCardIdException.class);
            assertThatThrownBy(() -> new RecordCardImageId(null))
                    .isInstanceOf(InvalidRecordCardImageIdException.class);
        }

        @Test
        @DisplayName("카드·사진 ID는 받은 UUID를 그대로 담는다")
        void keepUuid() {
            // given
            UUID value = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");

            // when & then
            assertThat(new RecordCardId(value).value()).isEqualTo(value);
            assertThat(new RecordCardImageId(value).value()).isEqualTo(value);
        }
    }

    @Nested
    @DisplayName("메모")
    class Memo {

        @Test
        @DisplayName("500자까지 받는다")
        void acceptMaxLength() {
            // given
            String value = "가".repeat(500);

            // when
            RecordCardMemo memo = new RecordCardMemo(value);

            // then
            assertThat(memo.value()).isEqualTo(value);
        }

        @Test
        @DisplayName("500자를 넘으면 거부한다")
        void rejectTooLong() {
            // when & then
            assertThatThrownBy(() -> new RecordCardMemo("가".repeat(501)))
                    .isInstanceOf(RecordCardMemoTooLongException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\n\t"})
        @DisplayName("비어 있으면 값으로 만들 수 없다")
        void rejectBlank(String value) {
            // when & then -> 음악 생성이 회고 글을 읽으므로 비어 있을 수 없다
            assertThatThrownBy(() -> new RecordCardMemo(value))
                    .isInstanceOf(RecordCardMemoRequiredException.class);
        }

        @Test
        @DisplayName("앞뒤 공백을 지우지 않고 그대로 담는다")
        void keepAsIs() {
            // when & then
            assertThat(new RecordCardMemo("  한강  ").value()).isEqualTo("  한강  ");
        }
    }

    @Nested
    @DisplayName("카드 순위")
    class Order {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -2, Integer.MIN_VALUE})
        @DisplayName("1보다 작은 순위는 거부한다 — 지운 카드는 행이 사라져 -1 표시가 없다")
        void rejectOutOfRange(int value) {
            // when & then
            assertThatThrownBy(() -> new DisplayOrder(value))
                    .isInstanceOf(InvalidDisplayOrderException.class);
        }

        @Test
        @DisplayName("다음 순위는 1 늘어난다")
        void nextIncrements() {
            // when & then
            assertThat(DisplayOrder.FIRST.next()).isEqualTo(new DisplayOrder(2));
        }

    }

    @Nested
    @DisplayName("사진 설명")
    class Caption {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("비웠으면 설명 없음으로 본다")
        void treatBlankAsAbsent(String value) {
            // when & then
            assertThat(RecordCardImageCaption.ofNullable(value)).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("빈 설명을 값으로 만들면 거부한다")
        void rejectBlankValue(String value) {
            // when & then
            assertThatThrownBy(() -> new RecordCardImageCaption(value))
                    .isInstanceOf(RecordCardImageCaptionEmptyException.class);
        }

        @Test
        @DisplayName("100자까지 받고 넘으면 거부한다")
        void limitLength() {
            // given
            String max = "노".repeat(100);
            String over = "노".repeat(101);

            // when & then
            assertThat(RecordCardImageCaption.ofNullable(max)).contains(new RecordCardImageCaption(max));
            assertThatThrownBy(() -> RecordCardImageCaption.ofNullable(over))
                    .isInstanceOf(RecordCardImageCaptionTooLongException.class);
        }
    }

    @Nested
    @DisplayName("S3 키")
    class Keys {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("사진·음악 키는 비어 있으면 거부한다")
        void rejectBlank(String value) {
            // when & then
            assertThatThrownBy(() -> new RecordCardImageKey(value))
                    .isInstanceOf(RecordCardImageKeyRequiredException.class);
            assertThatThrownBy(() -> new MusicKey(value))
                    .isInstanceOf(MusicKeyRequiredException.class);
        }

        @Test
        @DisplayName("사진·음악 키는 255자까지 받고 넘으면 거부한다")
        void limitLength() {
            // given
            String max = "k".repeat(255);
            String over = "k".repeat(256);

            // when & then
            assertThat(new RecordCardImageKey(max).value()).isEqualTo(max);
            assertThat(new MusicKey(max).value()).isEqualTo(max);
            assertThatThrownBy(() -> new RecordCardImageKey(over))
                    .isInstanceOf(RecordCardImageKeyTooLongException.class);
            assertThatThrownBy(() -> new MusicKey(over))
                    .isInstanceOf(MusicKeyTooLongException.class);
        }

        @Test
        @DisplayName("사진·음악 키는 앞뒤 공백을 지운다")
        void trim() {
            // when & then
            assertThat(new RecordCardImageKey(" record-cards/a.jpg ").value()).isEqualTo("record-cards/a.jpg");
            assertThat(new MusicKey(" record-cards/music/a ").value()).isEqualTo("record-cards/music/a");
        }
    }

    @Nested
    @DisplayName("음악 생성 상태")
    class Status {

        @ParameterizedTest
        @EnumSource(value = MusicStatus.class, names = {"COMPLETED", "FAILED"})
        @DisplayName("생성 결과는 COMPLETED·FAILED를 받는다")
        void acceptResult(MusicStatus result) {
            // when & then
            assertThat(MusicStatus.result(result)).isEqualTo(result);
        }

        @Test
        @DisplayName("생성 결과로 GENERATING이나 null은 거부한다")
        void rejectNonResult() {
            // when & then
            assertThatThrownBy(() -> MusicStatus.result(MusicStatus.GENERATING))
                    .isInstanceOf(InvalidMusicResultException.class);
            assertThatThrownBy(() -> MusicStatus.result(null))
                    .isInstanceOf(InvalidMusicResultException.class);
        }

        @Test
        @DisplayName("GENERATING만 생성 중이다")
        void onlyPendingIsPending() {
            // when & then
            assertThat(MusicStatus.GENERATING.isGenerating()).isTrue();
            assertThat(MusicStatus.COMPLETED.isGenerating()).isFalse();
            assertThat(MusicStatus.FAILED.isGenerating()).isFalse();
        }
    }
}
