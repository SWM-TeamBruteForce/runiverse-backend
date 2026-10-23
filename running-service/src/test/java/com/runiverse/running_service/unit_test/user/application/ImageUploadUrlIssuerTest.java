package com.runiverse.running_service.unit_test.user.application;

import com.runiverse.running_service.application.user.common.ImageContentType;
import com.runiverse.running_service.application.user.common.ImageUploadUrlIssuer;
import com.runiverse.running_service.application.user.common.ImageUploadUrlIssuer.IssuedUpload;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.port.out.GenerateImageIdPort;
import com.runiverse.running_service.application.user.port.out.GenerateUploadUrlPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("이미지 업로드 URL 발급 단위 테스트")
public class ImageUploadUrlIssuerTest {

    private static final UUID USER_ID = UUID.fromString("019ff918-a5ac-75b6-b20b-350454888411");
    private static final UUID IMAGE_ID = UUID.fromString("019ffa54-917f-7477-9482-5792597ef3b0");
    private static final String UPLOAD_URL = "https://bucket.s3.amazonaws.com/signed";

    @Mock
    private GenerateImageIdPort generateImageIdPort;

    @Mock
    private GenerateUploadUrlPort generateUploadUrlPort;

    @InjectMocks
    private ImageUploadUrlIssuer imageUploadUrlIssuer;

    @ParameterizedTest
    @EnumSource(UserImageType.class)
    @DisplayName("종류별 prefix 아래의 key와 그 key에 서명한 URL을 돌려준다")
    void issueReturnsKeyUnderTypePrefix(UserImageType type) {
        // given
        String expectedKey = type.createKey(USER_ID, IMAGE_ID, ImageContentType.JPEG);
        when(generateImageIdPort.generate()).thenReturn(IMAGE_ID);
        when(generateUploadUrlPort.generate(expectedKey, "image/jpeg", 204800L)).thenReturn(UPLOAD_URL);

        // when
        IssuedUpload issued = imageUploadUrlIssuer.issue(type, USER_ID, "image/jpeg", 204800L);

        // then
        assertThat(issued.key()).isEqualTo(expectedKey).startsWith(type.prefixOf(USER_ID));
        assertThat(issued.uploadUrl()).isEqualTo(UPLOAD_URL);
    }

    @Test
    @DisplayName("대문자 mimeType도 서명에는 소문자 정규값으로 넘긴다")
    void issueSignsCanonicalMimeType() {
        // given -> 업로드 헤더의 Content-Type은 서명값과 같아야 하므로 정규값으로 맞춘다
        when(generateImageIdPort.generate()).thenReturn(IMAGE_ID);

        // when
        IssuedUpload issued = imageUploadUrlIssuer.issue(UserImageType.BACKGROUND, USER_ID, "IMAGE/PNG", 1L);

        // then
        assertThat(issued.key()).endsWith(".png");
        verify(generateUploadUrlPort).generate(issued.key(), "image/png", 1L);
    }

    @Test
    @DisplayName("지원하지 않는 형식이면 key를 만들거나 서명하지 않는다")
    void issueRejectsUnsupportedMimeType() {
        // when & then -> 400은 Request DTO가 먼저 막는다. 여기까지 오면 버그라 서명까지 가면 안 된다
        assertThatThrownBy(() -> imageUploadUrlIssuer.issue(UserImageType.PROFILE, USER_ID, "image/gif", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(generateImageIdPort, generateUploadUrlPort);
    }
}
