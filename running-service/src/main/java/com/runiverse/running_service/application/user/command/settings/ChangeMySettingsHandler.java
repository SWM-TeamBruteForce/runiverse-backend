package com.runiverse.running_service.application.user.command.settings;

import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.ChangeMySettingsUsecase;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.UpdateSettingsPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangeMySettingsHandler implements ChangeMySettingsUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final UpdateSettingsPort updateSettingsPort;

    @Override
    public ChangeMySettingsResult handle(ChangeMySettingsCommand command) {
        UserId userId = new UserId(command.userId());

        // 1. 갱신 전 값을 확보한다 — 보내지 않은 필드를 응답에 채우려면 필요하다
        User user = loadUserByIdPort.loadById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 2. 값 규칙은 VO가 지킨다 — 저장 전에 만들어 어긋나면 아무것도 바꾸지 않는다
        ProfileVisibility profileVisibility = command.profileVisibility() == null
                ? null : ProfileVisibility.from(command.profileVisibility());

        // 3. 담겨 온 값만 갱신한다 — 둘 다 없으면 갱신할 것이 없어 저장으로 내려가지 않는다
        if (command.alertConsent() != null || profileVisibility != null) {
            updateSettingsPort.updateSettings(userId, command.alertConsent(), profileVisibility);
        }

        // 4. 갱신 전 값에 담겨 온 값을 덮은 것이 곧 저장된 설정이다
        return new ChangeMySettingsResult(
                command.alertConsent() == null ? user.isAlertConsent() : command.alertConsent(),
                (profileVisibility == null ? user.getProfileVisibility() : profileVisibility).name()
        );
    }
}
