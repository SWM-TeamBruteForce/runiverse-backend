package com.runiverse.running_service.application.common.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface LockUserPort {

    // 트랜잭션이 끝날 때까지 같은 유저의 요청을 줄 세운다. 다른 Lock*Port와 달리 users 행을 잠그지도 읽지도 않는다 —
    // 러닝 종료가 참가자 행 다음에 users 행을 고쳐서, 탈퇴가 users 행부터 잠그면 순서가 엇갈려 교착에 빠진다
    void lock(UserId userId);
}
