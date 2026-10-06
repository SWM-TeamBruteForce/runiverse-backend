package com.runiverse.running_service.application.user.command.onboarding;

import com.runiverse.running_service.application.user.exception.AlreadyOnboardedException;
import com.runiverse.running_service.application.user.exception.NicknameAlreadyExistsException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.CompleteOnboardingUsecase;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardingPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.SaveOnboardingPort;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.UserOnboarding;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CompleteOnboardingHandler implements CompleteOnboardingUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final ExistsOnboardingPort existsOnboardingPort;
    private final CheckNicknameDuplicatePort checkNicknameDuplicatePort;
    private final SaveOnboardingPort saveOnboardingPort;

    @Override
    public CompleteOnboardingResult handle(CompleteOnboardingCommand command) {
        // 1. 유저 조회
        UserId userId = new UserId(command.userId());
        User user = loadUserByIdPort.loadById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 2. 온보딩 되어 있으면 막는다
        if (existsOnboardingPort.existsByUserId(userId)) {
            throw new AlreadyOnboardedException();
        }

        // 3. 닉네임 정규화
        Nickname nickname = new Nickname(command.nickname());

        // 4. 닉네임 중복 확인
        if (checkNicknameDuplicatePort.existsByNickname(nickname)) {
            throw new NicknameAlreadyExistsException();
        }

        // 5. 도메인에 위임
        user.completeOnboarding(
                nickname.value(),
                command.gender(),
                command.birthday(),
                command.avgPace(),
                command.weight(),
                command.height()
        );

        // 6. 저장
        UserOnboarding onboarding = user.getOnboarding().orElseThrow();
        saveOnboardingPort.saveOnboarding(onboarding);

        // 7. 반환
        return new CompleteOnboardingResult(user.getUserId().value(), onboarding.getNickname().value());
    }

}
