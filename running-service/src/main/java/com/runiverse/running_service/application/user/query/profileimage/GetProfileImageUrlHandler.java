package com.runiverse.running_service.application.user.query.profileimage;

import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetProfileImageUsecase;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetProfileImageUrlHandler implements GetProfileImageUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final GenerateViewUrlPort generateViewUrlPort;

    @Override
    public GetProfileImageUrlResult handle(GetProfileImageUrlQuery query) {
        // 1. 대상 사용자 조회
        User user = loadUserByIdPort.loadById(new UserId(query.userId()))
                .orElseThrow(ProfileNotFoundException::new);

        // 2. 이미지가 없으면 URL도 없다
        String profileImageUrl = user.getProfileImageKey()
                .map(ProfileImageKey::value)
                .map(generateViewUrlPort::generate)
                .orElse(null);
        return new GetProfileImageUrlResult(query.userId(), profileImageUrl);
    }
}
