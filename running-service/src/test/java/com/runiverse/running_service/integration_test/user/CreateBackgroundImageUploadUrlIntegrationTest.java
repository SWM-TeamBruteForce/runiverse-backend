package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.backgroundimage.CreateBackgroundImageUploadUrlCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.CreateBackgroundImageUploadUrlHandler;
import com.runiverse.running_service.application.user.command.backgroundimage.CreateBackgroundImageUploadUrlResult;
import com.runiverse.running_service.application.user.common.ImageUploadUrlIssuer;
import com.runiverse.running_service.integration_test.fake.FakeImageIdGenerator;
import com.runiverse.running_service.integration_test.fake.FakeUploadUrlGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("배경 이미지 업로드 URL 발급 통합 테스트")
public class CreateBackgroundImageUploadUrlIntegrationTest {

    private static final String JPEG = "image/jpeg";
    private static final long FILE_SIZE_BYTES = 20_480L;

    private UUID userId;
    private FakeImageIdGenerator imageIdGenerator;
    private FakeUploadUrlGenerator uploadUrlGenerator;
    private CreateBackgroundImageUploadUrlHandler handler;

    @BeforeEach
    void setUp() {
        // DB를 쓰지 않는 유스케이스라 저장소 fake 없이 포트 두 개만 엮는다
        userId = UuidCreator.getTimeOrderedEpoch();
        imageIdGenerator = new FakeImageIdGenerator();
        uploadUrlGenerator = new FakeUploadUrlGenerator();
        handler = new CreateBackgroundImageUploadUrlHandler(
                new ImageUploadUrlIssuer(
                        imageIdGenerator,   // GenerateImageIdPort
                        uploadUrlGenerator  // GenerateUploadUrlPort
                )
        );
    }

    private CreateBackgroundImageUploadUrlCommand command(String mimeType, long fileSizeBytes) {
        return new CreateBackgroundImageUploadUrlCommand(userId, mimeType, fileSizeBytes);
    }

    @Test
    @DisplayName("발급하면 서버가 만든 key와 그 key의 업로드 URL을 돌려준다")
    void createUploadUrlSuccess() {
        // when
        CreateBackgroundImageUploadUrlResult result = handler.handle(command(JPEG, FILE_SIZE_BYTES));

        // then
        assertThat(result.backgroundImageKey())
                .isEqualTo("backgrounds/%s/00000000-0000-0000-0000-000000000001.jpg".formatted(userId));
        assertThat(result.uploadUrl()).isEqualTo(uploadUrlGenerator.urlOf(result.backgroundImageKey()));
    }

    @Test
    @DisplayName("key는 요청한 사용자 prefix 아래에 만든다")
    void keyIsUnderRequesterPrefix() {
        // when
        CreateBackgroundImageUploadUrlResult result = handler.handle(command(JPEG, FILE_SIZE_BYTES));

        // then -> 배경 반영 때 이 prefix로 소유자를 검증한다
        assertThat(result.backgroundImageKey()).startsWith("backgrounds/%s/".formatted(userId));
    }

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, .jpg",
            "image/png, .png",
            "image/webp, .webp"
    })
    @DisplayName("확장자는 클라이언트 파일명이 아니라 mimeType으로 정한다")
    void extensionComesFromMimeType(String mimeType, String extension) {
        // when
        CreateBackgroundImageUploadUrlResult result = handler.handle(command(mimeType, FILE_SIZE_BYTES));

        // then
        assertThat(result.backgroundImageKey()).endsWith(extension);
    }

    @Test
    @DisplayName("발급할 때 요청한 형식과 크기를 그대로 서명에 넘긴다")
    void signingValuesArePassedThrough() {
        // when
        handler.handle(command(JPEG, FILE_SIZE_BYTES));

        // then -> 이 두 값이 서명에 들어가야 클라가 다른 타입·크기로 올리지 못한다
        FakeUploadUrlGenerator.IssuedUrl issued = uploadUrlGenerator.last();
        assertThat(issued.contentType()).isEqualTo(JPEG);
        assertThat(issued.sizeBytes()).isEqualTo(FILE_SIZE_BYTES);
    }

    @Test
    @DisplayName("같은 사용자가 다시 발급받아도 key가 겹치지 않는다")
    void keyIsNeverReused() {
        // when
        String first = handler.handle(command(JPEG, FILE_SIZE_BYTES)).backgroundImageKey();
        String second = handler.handle(command(JPEG, FILE_SIZE_BYTES)).backgroundImageKey();

        // then -> key가 같으면 이전 이미지를 덮어쓰고 업로드 검증도 무력해진다
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("배경 key는 프로필 prefix 아래에 만들지 않는다")
    void keyIsNotUnderProfilePrefix() {
        // when
        CreateBackgroundImageUploadUrlResult result = handler.handle(command(JPEG, FILE_SIZE_BYTES));

        // then -> 같은 prefix면 배경 key로 프로필을, 프로필 key로 배경을 반영할 수 있게 된다
        assertThat(result.backgroundImageKey()).doesNotStartWith("profiles/");
    }

    @Test
    @DisplayName("대문자 형식으로 요청해도 서명에는 소문자 정규값을 넘긴다")
    void signingUsesCanonicalMimeType() {
        // when
        handler.handle(command("IMAGE/JPEG", FILE_SIZE_BYTES));

        // then -> 클라 업로드 헤더는 정규값이라 서명값도 같아야 한다
        assertThat(uploadUrlGenerator.last().contentType()).isEqualTo(JPEG);
    }

    @Test
    @DisplayName("허용하지 않는 형식이면 URL을 발급하지 않고 중단한다")
    void unsupportedMimeTypeStopsBeforeIssuing() {
        // when & then
        assertThatThrownBy(() -> handler.handle(command("image/gif", FILE_SIZE_BYTES)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(uploadUrlGenerator.isEmpty()).isTrue();
    }
}
