package com.runiverse.running_service.application.user.query.nickname;

import com.runiverse.running_service.application.user.port.in.CheckNicknameAvailabilityUsecase;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.domain.user.vo.Nickname;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckNicknameAvailabilityHandler implements CheckNicknameAvailabilityUsecase {

    private final CheckNicknameDuplicatePort checkNicknameDuplicatePort;

    @Override
    public CheckNicknameAvailabilityResult handle(CheckNicknameAvailabilityQuery query) {
        // 1. vo에서 정규화 진행
        Nickname nickname = new Nickname(query.nickname());

        // 2. 남이 쓰고 있지 않다면 사용 가능
        boolean available = !checkNicknameDuplicatePort.existsByNickname(nickname);
        return new CheckNicknameAvailabilityResult(nickname.value(), available);
    }
}
