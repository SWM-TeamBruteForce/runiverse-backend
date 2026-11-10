package com.runiverse.running_service.unit_test.recordcard.domain;

import com.runiverse.running_service.domain.recordcard.exception.InvalidDisplayOrderException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidMusicResultException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidRecordCardIdException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidRecordCardImageIdException;
import com.runiverse.running_service.domain.recordcard.exception.MusicKeyRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.MusicKeyTooLongException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageKeyRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageKeyTooLongException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardMemoRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardMemoTooLongException;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicKey;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordCardVoTest {

    @Nested
    @DisplayName("식별자")
    class Ids {

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("카드·사진 ID는 1보다 작은 값을 거부한다")
        void rejectNonPositive(long value) {
            // when & then
            assertThatThrownBy(() -> new RecordCardId(value))
                    .isInstanceOf(InvalidRecordCardIdException.class);
            assertThatThrownBy(() -> new RecordCardImageId(value))
                    .isInstanceOf(InvalidRecordCardImageIdException.class);
        }

        @Test
        @DisplayName("카드·사진 ID는 null을 거부한다")
        void rejectNull() {
            // when & then -> 저장 전은 애그리거트 필드가 null로 표현한다
            assertThatThrownBy(() -> new RecordCardId(null))
                    .isInstanceOf(InvalidRecordCardIdException.class);
            assertThatThrownBy(() -> new RecordCardImageId(null))
                    .isInstanceOf(InvalidRecordCardImageIdException.class);
        }

        @Test
        @DisplayName("카드·사진 ID는 bigint 최대값까지 담는다")
        void acceptBigintRange() {
            // when & then
            assertThat(new RecordCardId(Long.MAX_VALUE).value()).isEqualTo(Long.MAX_VALUE);
            assertThat(new RecordCardImageId(Long.MAX_VALUE).value()).isEqualTo(Long.MAX_VALUE);
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
            // when & then -> 메모 없음은 값이 아니라 부재로 표현한다
            assertThatThrownBy(() -> new RecordCardMemo(value))
                    .isInstanceOf(RecordCardMemoRequiredException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\n\t"})
        @DisplayName("요청의 빈 메모는 메모 없음이 된다")
        void blankRequestBecomesEmpty(String value) {
            // when & then
            assertThat(RecordCardMemo.fromNullable(value)).isEmpty();
        }

        @Test
        @DisplayName("요청의 메모는 앞뒤 공백을 지우지 않고 그대로 담는다")
        void keepRequestAsIs() {
            // when & then
            assertThat(RecordCardMemo.fromNullable("  한강  "))
                    .contains(new RecordCardMemo("  한강  "));
        }
    }

    @Nested
    @DisplayName("카드 순위")
    class Order {

        @ParameterizedTest
        @ValueSource(ints = {0, -2, Integer.MIN_VALUE})
        @DisplayName("1 이상과 삭제 표시(-1) 밖의 값은 거부한다")
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

        @Test
        @DisplayName("삭제 표시에서는 다음 순위를 셀 수 없다")
        void deletedHasNoNext() {
            // when & then -> -1 + 1 = 0은 순위가 아니다
            assertThatThrownBy(DisplayOrder.DELETED::next)
                    .isInstanceOf(InvalidDisplayOrderException.class);
        }

        @Test
        @DisplayName("-1만 삭제 표시다")
        void onlyMinusOneIsDeleted() {
            // when & then
            assertThat(new DisplayOrder(-1).isDeleted()).isTrue();
            assertThat(DisplayOrder.FIRST.isDeleted()).isFalse();
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
        @EnumSource(value = MusicStatus.class, names = {"READY", "FAILED"})
        @DisplayName("생성 결과는 READY·FAILED를 받는다")
        void acceptResult(MusicStatus result) {
            // when & then
            assertThat(MusicStatus.result(result)).isEqualTo(result);
        }

        @Test
        @DisplayName("생성 결과로 PENDING이나 null은 거부한다")
        void rejectNonResult() {
            // when & then
            assertThatThrownBy(() -> MusicStatus.result(MusicStatus.PENDING))
                    .isInstanceOf(InvalidMusicResultException.class);
            assertThatThrownBy(() -> MusicStatus.result(null))
                    .isInstanceOf(InvalidMusicResultException.class);
        }

        @Test
        @DisplayName("PENDING만 생성 중이다")
        void onlyPendingIsPending() {
            // when & then
            assertThat(MusicStatus.PENDING.isPending()).isTrue();
            assertThat(MusicStatus.READY.isPending()).isFalse();
            assertThat(MusicStatus.FAILED.isPending()).isFalse();
        }
    }
}
