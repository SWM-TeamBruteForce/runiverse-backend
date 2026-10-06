package com.runiverse.running_service.unit_test.user.application;

import com.runiverse.running_service.application.user.command.profileimage.ProfileImageContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("프로필 이미지 형식 단위 테스트")
public class ProfileImageContentTypeTest {

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, jpg",
            "image/png, png",
            "image/webp, webp"
    })
    @DisplayName("허용된 mimeType은 정해진 확장자로 바뀐다")
    void mimeTypeToExtension(String mimeType, String extension) {
        // when
        ProfileImageContentType contentType = ProfileImageContentType.from(mimeType);

        // then -> 확장자는 클라이언트 파일명이 아니라 이 표에서만 나온다
        assertThat(contentType.getExtension()).isEqualTo(extension);
        assertThat(contentType.getMimeType()).isEqualTo(mimeType);
    }

    @Test
    @DisplayName("mimeType의 대소문자는 구분하지 않는다")
    void mimeTypeIsCaseInsensitive() {
        // when & then -> Request DTO의 @Pattern도 (?i)라 둘의 허용 범위를 맞춘다
        assertThat(ProfileImageContentType.from("IMAGE/JPEG"))
                .isEqualTo(ProfileImageContentType.JPEG);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "image/gif",
            "image/heic",
            "application/pdf",
            "''",
            "null"
    }, nullValues = "null")
    @DisplayName("허용 목록에 없는 mimeType은 예외를 던진다")
    void unsupportedMimeTypeThrows(String mimeType) {
        // 400은 Request DTO가 막고, 여기까지 왔다면 서버 쪽 실수라 예외로 끊는다
        assertThatThrownBy(() -> ProfileImageContentType.from(mimeType))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
