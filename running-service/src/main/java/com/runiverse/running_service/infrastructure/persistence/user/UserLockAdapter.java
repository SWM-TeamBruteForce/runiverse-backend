package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.application.common.port.out.LockUserPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserLockAdapter implements LockUserPort {

    private final EntityManager entityManager;

    // 트랜잭션 단위 advisory lock이라 커밋·롤백 때 저절로 풀린다.
    // 키가 숫자 하나라 UUID를 접는다 — 다른 유저와 키가 겹치면 잠깐 서로 기다릴 뿐 결과는 틀리지 않는다.
    // 반환 타입이 void인 함수라 FROM 절에 두어 결과 행을 하나 만든다
    @Override
    public void lock(UserId userId) {
        UUID value = userId.value();
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(:key)")
                .setParameter("key", value.getMostSignificantBits() ^ value.getLeastSignificantBits())
                .getSingleResult();
    }
}
