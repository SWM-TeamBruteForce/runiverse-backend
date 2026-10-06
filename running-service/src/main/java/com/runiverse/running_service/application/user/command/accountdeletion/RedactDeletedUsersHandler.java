package com.runiverse.running_service.application.user.command.accountdeletion;

import com.runiverse.running_service.application.user.port.in.RedactDeletedUsersUsecase;
import com.runiverse.running_service.application.user.port.out.LoadDeletedUserIdsPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// 트랜잭션을 걸지 않는다 — 제거는 DeletedUserRedactor가 건마다 자기 트랜잭션에서 한다
@Slf4j
@Service
@RequiredArgsConstructor
public class RedactDeletedUsersHandler implements RedactDeletedUsersUsecase {

    private final LoadDeletedUserIdsPort loadDeletedUserIdsPort;
    private final DeletedUserRedactor deletedUserRedactor;
    private final AccountDeletionProperties properties;

    @Override
    public void redactAfterRetention() {
        LocalDateTime deletedBefore = LocalDateTime.now().minus(properties.retention());
        List<UserId> targets = loadDeletedUserIdsPort.loadDeletedBefore(deletedBefore);
        for (UserId userId : targets) {
            try {
                deletedUserRedactor.redact(userId);
            } catch (RuntimeException e) {
                // 한 건이 터져도 나머지는 지운다. 실패한 행은 신원 정보가 남아 다음 실행에 다시 걸린다
                log.error("[회원] 탈퇴 기록 신원 정보 제거 실패: 처리하지 못한 예외 - userId={}", userId.value(), e);
            }
        }
    }
}
