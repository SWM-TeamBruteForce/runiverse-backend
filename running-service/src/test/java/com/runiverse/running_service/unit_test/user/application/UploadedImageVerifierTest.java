package com.runiverse.running_service.unit_test.user.application;

import com.runiverse.running_service.application.user.common.ImageContentType;
import com.runiverse.running_service.application.user.common.UploadedImageVerifier;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.exception.BackgroundImageNotUploadedException;
import com.runiverse.running_service.application.user.exception.InvalidBackgroundImageException;
import com.runiverse.running_service.application.user.exception.InvalidProfileImageException;
import com.runiverse.running_service.application.user.exception.ProfileImageNotUploadedException;
import com.runiverse.running_service.application.user.port.out.LoadUploadedImagePort;
import com.runiverse.running_service.application.user.port.out.UploadedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("업로드 이미지 검증 단위 테스트")
public class UploadedImageVerifierTest {

    private static final UUID OWNER = UUID.fromString("019ff918-a5ac-75b6-b20b-350454888411");
    private static final UUID OTHER = UUID.fromString("019ff918-a5ac-75b6-b20b-350454888499");
    private static final UUID IMAGE_ID = UUID.fromString("019ffa54-917f-7477-9482-5792597ef3b0");
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    @Mock
    private LoadUploadedImagePort loadUploadedImagePort;

    @InjectMocks
    private UploadedImageVerifier uploadedImageVerifier;

    private static String keyOf(UserImageType type, UUID userId) {
        return type.createKey(userId, IMAGE_ID, ImageContentType.JPEG);
    }

    @ParameterizedTest
    @EnumSource(UserImageType.class)
    @DisplayName("본인 key이고 10MB 이하의 허용 형식으로 올라와 있으면 통과한다")
    void verifyPassesValidUpload(UserImageType type) {
        // given -> 경계값: 정확히 10MB는 허용
        String key = keyOf(type, OWNER);
        when(loadUploadedImagePort.load(key)).thenReturn(Optional.of(new UploadedImage(MAX_SIZE_BYTES, "image/jpeg")));

        // when & then
        assertThatCode(() -> uploadedImageVerifier.verify(type, OWNER, key)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("남의 key면 S3를 조회하지 않고 종류별 invalid 예외를 던진다")
    void verifyRejectsOtherUsersKeyBeforeLookup() {
        // given -> S3에 묻기 전에 막아야 남의 key 존재 여부가 응답으로 새지 않는다
        String profileKey = keyOf(UserImageType.PROFILE, OTHER);
        String backgroundKey = keyOf(UserImageType.BACKGROUND, OTHER);

        // when & then
        assertThatThrownBy(() -> uploadedImageVerifier.verify(UserImageType.PROFILE, OWNER, profileKey))
                .isInstanceOf(InvalidProfileImageException.class);
        assertThatThrownBy(() -> uploadedImageVerifier.verify(UserImageType.BACKGROUND, OWNER, backgroundKey))
                .isInstanceOf(InvalidBackgroundImageException.class);
        verifyNoInteractions(loadUploadedImagePort);
    }

    @Test
    @DisplayName("자기 프로필 key를 배경으로 반영하면 거부한다")
    void verifyRejectsOtherTypesKey() {
        // given
        String profileKey = keyOf(UserImageType.PROFILE, OWNER);

        // when & then
        assertThatThrownBy(() -> uploadedImageVerifier.verify(UserImageType.BACKGROUND, OWNER, profileKey))
                .isInstanceOf(InvalidBackgroundImageException.class);
        verifyNoInteractions(loadUploadedImagePort);
    }

    @Test
    @DisplayName("S3에 객체가 없으면 종류별 notUploaded 예외를 던진다")
    void verifyRejectsMissingObject() {
        // given
        String profileKey = keyOf(UserImageType.PROFILE, OWNER);
        String backgroundKey = keyOf(UserImageType.BACKGROUND, OWNER);
        when(loadUploadedImagePort.load(profileKey)).thenReturn(Optional.empty());
        when(loadUploadedImagePort.load(backgroundKey)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> uploadedImageVerifier.verify(UserImageType.PROFILE, OWNER, profileKey))
                .isInstanceOf(ProfileImageNotUploadedException.class);
        assertThatThrownBy(() -> uploadedImageVerifier.verify(UserImageType.BACKGROUND, OWNER, backgroundKey))
                .isInstanceOf(BackgroundImageNotUploadedException.class);
    }

    @Test
    @DisplayName("올라간 파일이 10MB를 넘으면 종류별 invalid 예외를 던진다")
    void verifyRejectsOversizedObject() {
        // given -> 경계값: 10MB + 1바이트
        String key = keyOf(UserImageType.BACKGROUND, OWNER);
        when(loadUploadedImagePort.load(key))
                .thenReturn(Optional.of(new UploadedImage(MAX_SIZE_BYTES + 1, "image/jpeg")));

        // when & then
        assertThatThrownBy(() -> uploadedImageVerifier.verify(UserImageType.BACKGROUND, OWNER, key))
                .isInstanceOf(InvalidBackgroundImageException.class);
    }

    @Test
    @DisplayName("올라간 파일의 Content-Type이 허용값이 아니면 종류별 invalid 예외를 던진다")
    void verifyRejectsUnsupportedContentType() {
        // given
        String key = keyOf(UserImageType.PROFILE, OWNER);
        when(loadUploadedImagePort.load(key)).thenReturn(Optional.of(new UploadedImage(1L, "image/gif")));

        // when & then
        assertThatThrownBy(() -> uploadedImageVerifier.verify(UserImageType.PROFILE, OWNER, key))
                .isInstanceOf(InvalidProfileImageException.class);
    }
}
