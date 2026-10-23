package com.runiverse.running_service.application.user.query.backgroundimage;

import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.common.UserImageUrlResolver;
import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetBackgroundImageUsecase;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetBackgroundImageUrlHandler implements GetBackgroundImageUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final UserImageUrlResolver userImageUrlResolver;

    @Override
    public GetBackgroundImageUrlResult handle(GetBackgroundImageUrlQuery query) {
        // 1. 대상 사용자 조회
        User user = loadUserByIdPort.loadById(new UserId(query.userId()))
                .orElseThrow(ProfileNotFoundException::new);

        // 2. 배경이 없으면 URL도 없다 - 기본 배경은 클라가 그린다
        String backgroundImageUrl = userImageUrlResolver.resolve(UserImageType.BACKGROUND, user);
        return new GetBackgroundImageUrlResult(query.userId(), backgroundImageUrl);
    }
}
