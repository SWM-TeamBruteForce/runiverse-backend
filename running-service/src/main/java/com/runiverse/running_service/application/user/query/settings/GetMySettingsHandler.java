package com.runiverse.running_service.application.user.query.settings;

import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetMySettingsUsecase;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.aggregate.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMySettingsHandler implements GetMySettingsUsecase {

    private final LoadUserByIdPort loadUserByIdPort;

    @Override
    public GetMySettingsResult handle(GetMySettingsQuery query) {
        UserId userId = new UserId(query.userId());

        // 두 값 모두 애그리거트가 들고 있어 추가 조회가 필요 없다
        User user = loadUserByIdPort.loadById(userId)
                .orElseThrow(UserNotFoundException::new);

        return new GetMySettingsResult(
                user.isAlertConsent(),
                user.getProfileVisibility().name()
        );
    }
}
