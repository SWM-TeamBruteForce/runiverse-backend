package com.runiverse.running_service.application.user.query.profileimage;

import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.common.UserImageUrlResolver;
import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetProfileImageUsecase;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetProfileImageUrlHandler implements GetProfileImageUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final UserImageUrlResolver userImageUrlResolver;

    @Override
    public GetProfileImageUrlResult handle(GetProfileImageUrlQuery query) {
        // 1. 대상 사용자 조회
        User user = loadUserByIdPort.loadById(new UserId(query.userId()))
                .orElseThrow(ProfileNotFoundException::new);

        // 2. 이미지가 없으면 URL도 없다
        String profileImageUrl = userImageUrlResolver.resolve(UserImageType.PROFILE, user);
        return new GetProfileImageUrlResult(query.userId(), profileImageUrl);
    }
}
