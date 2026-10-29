package com.runiverse.running_service.application.device.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface LoadPushTargetPort {

    // 그 유저의 켜진 기기 — 로그아웃했거나 등록한 적이 없으면 없다
    Optional<PushTarget> loadPushTarget(UserId userId);
}
