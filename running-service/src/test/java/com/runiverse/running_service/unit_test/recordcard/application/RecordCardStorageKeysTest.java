package com.runiverse.running_service.unit_test.recordcard.application;

import com.runiverse.running_service.application.recordcard.common.RecordCardStorageKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("기록 카드 S3 key 규칙 단위 테스트")
class RecordCardStorageKeysTest {

    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
    private static final UUID IMAGE_ID = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");

    @Test
    @DisplayName("작은 버전(썸네일)은 원본과 같은 사진 폴더의 thumbnail/ 아래에 원본 파일 이름 그대로다 — 한글·공백도 그대로")
    void thumbnailUnderSameFolder() {
        // given
        String imageKey = RecordCardStorageKeys.imageKey(USER_ID, CARD_ID, IMAGE_ID, "한강 야경.png");

        // when
        String thumbnailKey = RecordCardStorageKeys.thumbnailKeyOf(imageKey);

        // then
        assertThat(imageKey).isEqualTo("record-cards/%s/%s/%s/한강 야경.png".formatted(USER_ID, CARD_ID, IMAGE_ID));
        assertThat(thumbnailKey)
                .isEqualTo("record-cards/%s/%s/%s/thumbnail/한강 야경.png".formatted(USER_ID, CARD_ID, IMAGE_ID));
    }

    @Test
    @DisplayName("원본 이름이 thumbnail.jpg여도 작은 버전과 key가 겹치지 않는다 — 원본 이름에는 /가 없다")
    void neverCollidesWithOriginal() {
        // given
        String imageKey = RecordCardStorageKeys.imageKey(USER_ID, CARD_ID, IMAGE_ID, "thumbnail.jpg");

        // when & then
        assertThat(RecordCardStorageKeys.thumbnailKeyOf(imageKey)).isNotEqualTo(imageKey)
                .endsWith("/%s/thumbnail/thumbnail.jpg".formatted(IMAGE_ID));
    }
}
