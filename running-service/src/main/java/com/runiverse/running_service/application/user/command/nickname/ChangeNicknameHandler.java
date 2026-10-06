package com.runiverse.running_service.application.user.command.nickname;

import com.runiverse.running_service.application.user.exception.NicknameAlreadyExistsException;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.port.in.ChangeNicknameUsecase;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.UpdateNicknamePort;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangeNicknameHandler implements ChangeNicknameUsecase {

    private final LoadNicknamePort loadNicknamePort;
    private final CheckNicknameDuplicatePort checkNicknameDuplicatePort;
    private final UpdateNicknamePort updateNicknamePort;

    @Override
    public ChangeNicknameResult handle(ChangeNicknameCommand command) {
        UserId userId = new UserId(command.userId());
        // 1. 닉네임은 VO가 정규화한다
        Nickname nickname = new Nickname(command.nickname());

        // 2. 온보딩에 존재하는지 우선적으로 확인
        Nickname current = loadNicknamePort.loadNickname(userId)
                .orElseThrow(OnboardingNotCompletedException::new);

        // 3. 자기 자신의 닉네임과 같으면 그대로 반환 -> 변하는 거 없음
        if (current.equals(nickname)) {
            return new ChangeNicknameResult(current.value());
        }

        // 4. 남이 닉네임을 쓰고 있으면 막는다
        if (checkNicknameDuplicatePort.existsByNickname(nickname)) {
            throw new NicknameAlreadyExistsException();
        }

        // 5. 갱신
        updateNicknamePort.updateNickname(userId, nickname);
        return new ChangeNicknameResult(nickname.value());
    }
}
