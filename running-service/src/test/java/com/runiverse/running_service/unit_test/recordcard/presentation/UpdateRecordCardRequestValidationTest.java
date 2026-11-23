package com.runiverse.running_service.unit_test.recordcard.presentation;

import com.runiverse.running_service.presentation.recordcard.request.UpdateRecordCardRequest;
import com.runiverse.running_service.presentation.recordcard.request.UpdateRecordCardRequest.Image;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 도메인 예외는 500으로 마스킹되므로 400은 여기서 만든다 — 사진 목록 없이 보낸 썸네일 범위와 최대 크기만 유스케이스가 본다
@DisplayName("기록 카드 수정 요청 DTO 검증 단위 테스트")
class UpdateRecordCardRequestValidationTest {

    private static final UUID KEPT = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("아무 필드도 보내지 않아도 통과한다 — 바뀌는 것이 없을 뿐이다")
    void allowsEmpty() {
        assertThat(messages(new UpdateRecordCardRequest(null, null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("남길 사진과 새 사진을 섞어 보낼 수 있다")
    void allowsMixedImages() {
        assertThat(messages(images(List.of(kept(KEPT, "노을"), added("결승선")), 1))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\u3000"})
    @DisplayName("회고 글은 보내면 비어 있을 수 없다 — 전각 공백만 있어도 비어 있다")
    void rejectsBlankMemo(String memo) {
        assertThat(messages(new UpdateRecordCardRequest(memo, null, null, null, null)))
                .containsExactly("회고 글은 비어 있을 수 없습니다.");
    }

    @Test
    @DisplayName("회고 글은 1000자를 넘을 수 없다")
    void rejectsLongMemo() {
        assertThat(messages(new UpdateRecordCardRequest("가".repeat(1001), null, null, null, null)))
                .containsExactly("회고 글은 1000자 이하여야 합니다.");
        assertThat(messages(new UpdateRecordCardRequest("가".repeat(1000), null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("활동 유형·공개 범위는 정해진 값만 받는다")
    void rejectsUnknownEnums() {
        assertThat(messages(new UpdateRecordCardRequest(null, "WALK", "SECRET", null, null)))
                .containsExactlyInAnyOrder("활동 유형은 DAILY, TRAVEL, RACE 중 하나여야 합니다.",
                        "공개 범위는 FRIENDS, PUBLIC, PRIVATE 중 하나여야 합니다.");
    }

    @Test
    @DisplayName("사진은 5장 이하만 넣을 수 있다")
    void rejectsTooManyImages() {
        assertThat(messages(images(Collections.nCopies(6, added(null)), null)))
                .containsExactly("사진은 5장 이하만 넣을 수 있습니다.");
    }

    @Test
    @DisplayName("한 항목에 기존 사진 ID와 새 사진 정보를 함께 넣거나, 둘 다 없으면 400이다")
    void rejectsMixedKindInOneItem() {
        Image both = new Image(KEPT, "image/jpeg", 10L, 100L, "a.jpg", null);
        Image neither = new Image(null, null, null, 100L, null, "설명만");
        Image partialNew = new Image(null, "image/jpeg", 10L, 100L, null, null);
        for (Image image : List.of(both, neither, partialNew)) {
            assertThat(messages(images(List.of(image), null)))
                    .containsExactly("사진 항목에는 기존 사진 ID와 새 사진 정보 중 하나만 넣어야 합니다.");
        }
    }

    @Test
    @DisplayName("새 사진은 작은 버전(썸네일) 크기가 필수이고, 남길 사진에 썸네일 크기를 넣으면 섞인 항목이다")
    void thumbnailOnlyForNewImage() {
        assertThat(messages(images(List.of(new Image(null, "image/jpeg", 10L, null, "a.jpg", null)), null)))
                .containsExactly("썸네일 크기는 필수입니다.");
        assertThat(messages(images(List.of(new Image(null, "image/jpeg", 10L, 0L, "a.jpg", null)), null)))
                .containsExactly("썸네일 크기는 1바이트 이상이어야 합니다.");
        assertThat(messages(images(List.of(new Image(KEPT, null, null, 100L, null, null)), null)))
                .containsExactly("사진 항목에는 기존 사진 ID와 새 사진 정보 중 하나만 넣어야 합니다.");
    }

    @Test
    @DisplayName("같은 사진을 두 번 넣을 수 없다")
    void rejectsDuplicate() {
        assertThat(messages(images(List.of(kept(KEPT, null), kept(KEPT, "다시")), null)))
                .containsExactly("같은 사진을 두 번 넣을 수 없습니다.");
    }

    @Test
    @DisplayName("설명은 100자를 넘을 수 없다")
    void rejectsLongCaption() {
        assertThat(messages(images(List.of(kept(KEPT, "가".repeat(101))), null)))
                .containsExactly("사진 설명은 100자 이하여야 합니다.");
    }

    @Test
    @DisplayName("사진 목록을 보냈으면 썸네일 위치가 그 범위 안이어야 하고, 음수는 언제나 안 된다")
    void rejectsThumbnailOutOfRange() {
        assertThat(messages(images(List.of(kept(KEPT, null)), 1)))
                .containsExactly("썸네일 위치가 사진 범위를 벗어났습니다.");
        assertThat(messages(images(List.of(), 0))).containsExactly("썸네일 위치가 사진 범위를 벗어났습니다.");
        assertThat(messages(new UpdateRecordCardRequest(null, null, null, null, -1)))
                .containsExactly("썸네일 위치가 사진 범위를 벗어났습니다.");
        // 사진 목록 없이 보낸 위치는 지금 사진 수를 알아야 해서 여기서 막지 않는다
        assertThat(messages(new UpdateRecordCardRequest(null, null, null, null, 4))).isEmpty();
    }

    @Test
    @DisplayName("새 사진은 9-1과 같은 형식·크기·이름 규칙을 따른다")
    void newImageRules() {
        assertThat(messages(images(List.of(new Image(null, "image/gif", 0L, 100L, "../a.gif", null)), null)))
                .containsExactlyInAnyOrder("이미지는 JPEG, PNG, WEBP 형식만 업로드할 수 있습니다.",
                        "파일 크기는 1바이트 이상이어야 합니다.", "파일 이름에 쓸 수 없는 문자가 있습니다.");
    }

    private static List<String> messages(UpdateRecordCardRequest request) {
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
    }

    private static UpdateRecordCardRequest images(List<Image> images, Integer thumbnailIndex) {
        return new UpdateRecordCardRequest(null, null, null, images, thumbnailIndex);
    }

    private static Image kept(UUID id, String caption) {
        return new Image(id, null, null, null, null, caption);
    }

    private static Image added(String caption) {
        return new Image(null, "image/jpeg", 1024L, 100L, "IMG_3001.jpg", caption);
    }
}
