package com.runiverse.running_service.unit_test.recordcard.presentation;

import com.runiverse.running_service.presentation.recordcard.request.CreateRecordCardDraftRequest;
import com.runiverse.running_service.presentation.recordcard.request.CreateRecordCardDraftRequest.Image;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 도메인 예외는 500으로 마스킹되므로 400은 여기서만 만들어진다 — 최대 크기(운영값)만 유스케이스가 본다
@DisplayName("기록 카드 작성 요청 DTO 검증 단위 테스트")
class CreateRecordCardDraftRequestValidationTest {

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
    @DisplayName("사진 없이도, 사진을 생략해도 통과한다")
    void allowsNoImages() {
        assertThat(messages(request(List.of(), null))).isEmpty();
        assertThat(messages(request(null, null))).isEmpty();
    }

    @Test
    @DisplayName("한글·공백이 든 파일 이름과 대문자 형식은 통과한다")
    void allowsKoreanFileNameAndUpperMime() {
        assertThat(messages(request(List.of(new Image("IMAGE/PNG", 1L, "한강 야경 01.png", null)), 0)))
                .isEmpty();
    }

    @Test
    @DisplayName("100MB를 넘어도 여기서는 막지 않는다 — 운영값이라 유스케이스가 본다")
    void doesNotCheckMaxSize() {
        assertThat(messages(request(List.of(image(Long.MAX_VALUE)), null))).isEmpty();
    }

    @Test
    @DisplayName("필수 필드가 없으면 명세 문구로 거절한다")
    void rejectMissingFields() {
        // when
        List<String> messages = messages(new CreateRecordCardDraftRequest(null, null, null, null, null, null));

        // then
        assertThat(messages).containsExactlyInAnyOrder(
                "러닝 기록 ID는 필수입니다.", "활동 유형은 필수입니다.", "회고 글은 필수입니다.", "공개 범위는 필수입니다.");
    }

    @Test
    @DisplayName("기록 ID가 1보다 작으면 거절한다")
    void rejectNonPositiveRecordId() {
        assertThat(messages(new CreateRecordCardDraftRequest(0L, "DAILY", "글", "PUBLIC", null, null)))
                .containsExactly("입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("허용하지 않는 활동 유형·공개 범위는 거절한다")
    void rejectUnknownEnums() {
        assertThat(messages(new CreateRecordCardDraftRequest(1L, "daily", "글", "ALL", null, null)))
                .containsExactlyInAnyOrder(
                        "활동 유형은 DAILY, TRAVEL, RACE 중 하나여야 합니다.",
                        "공개 범위는 FRIENDS, PUBLIC, PRIVATE 중 하나여야 합니다.");
    }

    @Test
    @DisplayName("회고 글이 공백뿐이면 필수, 500자를 넘으면 길이 문구로 거절한다")
    void rejectBadMemo() {
        assertThat(messages(new CreateRecordCardDraftRequest(1L, "DAILY", "   ", "PUBLIC", null, null)))
                .containsExactly("회고 글은 필수입니다.");
        assertThat(messages(new CreateRecordCardDraftRequest(1L, "DAILY", "가".repeat(501), "PUBLIC", null, null)))
                .containsExactly("회고 글은 500자 이하여야 합니다.");
        assertThat(messages(new CreateRecordCardDraftRequest(1L, "DAILY", "가".repeat(500), "PUBLIC", null, null)))
                .isEmpty();
    }

    @Test
    @DisplayName("사진은 10장까지 받고 11장이면 거절한다")
    void limitImageCount() {
        assertThat(messages(request(Collections.nCopies(10, image(1L)), null))).isEmpty();
        assertThat(messages(request(Collections.nCopies(11, image(1L)), null)))
                .containsExactly("사진은 10장 이하만 넣을 수 있습니다.");
    }

    @Test
    @DisplayName("사진 항목이 null이면 거절한다")
    void rejectNullImage() {
        List<Image> images = new ArrayList<>();
        images.add(null);
        assertThat(messages(request(images, null))).containsExactly("입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("사진 형식·크기가 없거나 틀리면 명세 문구로 거절한다")
    void rejectBadImageFormatAndSize() {
        assertThat(messages(request(List.of(new Image(null, null, "a.jpg", null)), null)))
                .containsExactlyInAnyOrder("이미지 형식은 필수입니다.", "파일 크기는 필수입니다.");
        assertThat(messages(request(List.of(new Image("image/gif", 0L, "a.gif", null)), null)))
                .containsExactlyInAnyOrder(
                        "이미지는 JPEG, PNG, WEBP 형식만 업로드할 수 있습니다.", "파일 크기는 1바이트 이상이어야 합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("파일 이름이 없거나 비면 필수 문구로 거절한다")
    void rejectMissingFileName(String fileName) {
        assertThat(messages(request(List.of(new Image("image/jpeg", 1L, fileName, null)), null)))
                .contains("파일 이름은 필수입니다.");
    }

    @Test
    @DisplayName("파일 이름은 100자까지 받고 넘으면 거절한다")
    void limitFileNameLength() {
        assertThat(messages(request(List.of(new Image("image/jpeg", 1L, "a".repeat(100), null)), null))).isEmpty();
        assertThat(messages(request(List.of(new Image("image/jpeg", 1L, "a".repeat(101), null)), null)))
                .containsExactly("파일 이름은 100자 이하여야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../a.jpg", "a/b.jpg", "a\\b.jpg", ".", "..", "a\nb.jpg", "a\u0000.jpg"})
    @DisplayName("폴더를 벗어나는 문자나 . · ..만인 이름은 거절한다")
    void rejectUnsafeFileName(String fileName) {
        // when & then -> 이름이 사진 key의 마지막 부분에 그대로 들어간다
        assertThat(messages(request(List.of(new Image("image/jpeg", 1L, fileName, null)), null)))
                .containsExactly("파일 이름에 쓸 수 없는 문자가 있습니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"...", ".hidden.jpg", "a..b.jpg"})
    @DisplayName("점이 섞였어도 . · ..만 아니면 받는다")
    void allowDotsInName(String fileName) {
        assertThat(messages(request(List.of(new Image("image/jpeg", 1L, fileName, null)), null))).isEmpty();
    }

    @Test
    @DisplayName("사진 설명은 100자까지 받고, 공백뿐이어도 거절하지 않는다")
    void captionRules() {
        assertThat(messages(request(List.of(new Image("image/jpeg", 1L, "a.jpg", "   ")), null))).isEmpty();
        assertThat(messages(request(List.of(new Image("image/jpeg", 1L, "a.jpg", "가".repeat(101))), null)))
                .containsExactly("사진 설명은 100자 이하여야 합니다.");
    }

    @Test
    @DisplayName("썸네일 위치가 사진 범위를 벗어나거나, 사진이 없는데 보내면 거절한다")
    void rejectThumbnailOutOfRange() {
        String message = "썸네일 위치가 사진 범위를 벗어났습니다.";
        assertThat(messages(request(List.of(image(1L), image(1L)), 1))).isEmpty();
        assertThat(messages(request(List.of(image(1L), image(1L)), 2))).containsExactly(message);
        assertThat(messages(request(List.of(image(1L)), -1))).containsExactly(message);
        assertThat(messages(request(List.of(), 0))).containsExactly(message);
        assertThat(messages(request(null, 0))).containsExactly(message);
    }

    private static List<String> messages(CreateRecordCardDraftRequest request) {
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
    }

    private static CreateRecordCardDraftRequest request(List<Image> images, Integer thumbnailIndex) {
        return new CreateRecordCardDraftRequest(501L, "DAILY", "바람이 좋았다", "PUBLIC", images, thumbnailIndex);
    }

    private static Image image(long fileSizeBytes) {
        return new Image("image/jpeg", fileSizeBytes, "IMG_0001.jpg", null);
    }
}
