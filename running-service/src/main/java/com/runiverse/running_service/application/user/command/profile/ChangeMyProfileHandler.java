package com.runiverse.running_service.application.user.command.profile;

import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.ChangeMyProfileUsecase;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardingPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.UpdateIntroductionPort;
import com.runiverse.running_service.application.user.port.out.UpdateOnboardingPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.Birthday;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.Introduction;
import com.runiverse.running_service.domain.user.vo.Weight;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangeMyProfileHandler implements ChangeMyProfileUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final UpdateIntroductionPort updateIntroductionPort;
    private final ExistsOnboardingPort existsOnboardingPort;
    private final UpdateOnboardingPort updateOnboardingPort;

    @Override
    public ChangeMyProfileResult handle(ChangeMyProfileCommand command) {
        UserId userId = new UserId(command.userId());

        // 1. 갱신 대상이 남아 있는지 확인
        loadUserByIdPort.loadById(userId).orElseThrow(UserNotFoundException::new);

        // 2. 값 규칙은 VO가 지킨다 — 저장 전에 전부 만들어 하나라도 어긋나면 아무것도 바꾸지 않는다
        Introduction introduction = command.introduction() == null
                ? null : new Introduction(command.introduction());
        Gender gender = command.gender() == null ? null : Gender.from(command.gender());
        Birthday birthday = command.birthday() == null ? null : new Birthday(command.birthday());
        Weight weight = command.weightKg() == null ? null : new Weight(command.weightKg());
        Height height = command.heightCm() == null ? null : new Height(command.heightCm());

        // 3. 온보딩 값이 담겼으면 저장 전에 완료 여부부터 막는다 — 절반만 반영되지 않도록
        if (command.hasOnboardingField() && !existsOnboardingPort.existsByUserId(userId)) {
            throw new OnboardingNotCompletedException();
        }

        // 4. 소개글은 users에 있어 온보딩 전에도 바꿀 수 있다
        if (introduction != null) {
            updateIntroductionPort.updateIntroduction(userId, introduction);
        }
        if (command.hasOnboardingField()) {
            updateOnboardingPort.updateOnboarding(userId, gender, birthday, weight, height);
        }

        return new ChangeMyProfileResult(
                introduction == null ? null : introduction.value(),
                gender == null ? null : gender.name(),
                birthday == null ? null : birthday.value(),
                weight == null ? null : weight.value(),
                height == null ? null : height.value()
        );
    }
}
