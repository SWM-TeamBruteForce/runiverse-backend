package com.runiverse.running_service.application.user.command.profileimage;

import com.runiverse.running_service.application.user.port.in.CreateProfileImageUploadUrlUsecase;
import com.runiverse.running_service.application.user.port.out.GenerateProfileImageIdPort;
import com.runiverse.running_service.application.user.port.out.GenerateUploadUrlPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateProfileImageUploadUrlHandler implements CreateProfileImageUploadUrlUsecase {

    private final GenerateProfileImageIdPort generateProfileImageIdPort;
    private final GenerateUploadUrlPort generateUploadUrlPort;

    @Override
    public CreateProfileImageUploadUrlResult handle(CreateProfileImageUploadUrlCommand command) {
        // 0. 허용된 형식인지 확인하고 확장자를 얻음
        ProfileImageContentType contentType = ProfileImageContentType.from(command.mimeType());

        // 1. 이미지 저장을 위한 키네임 생성
        // prefix에 userId를 넣어 프로필 소유자 검증
        String profileImageKey = ProfileImageKeyPolicy.create(
                command.userId(),
                generateProfileImageIdPort.generate(),
                contentType
        ).value();

        // 2. 키 위치에 맞는 upload_url 생성
        // contentType이 서명에 포함되므로 업로드 헤더와 일치 해야함
        String uploadUrl = generateUploadUrlPort.generate(profileImageKey, contentType.getMimeType(),
                command.fileSizeBytes());

        return new CreateProfileImageUploadUrlResult(profileImageKey, uploadUrl);
    }
}
