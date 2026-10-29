package com.runiverse.running_service.application.device.port.out;

import com.runiverse.running_service.domain.device.vo.PushToken;

public interface SendPushPort {

    // 실패해도 던지지 않는다 — 이미 커밋된 뒤라 되돌릴 것이 없다. 토큰을 정리할지, 만료로 보내지 않았는지는 결과로 알린다
    PushSendStatus send(PushToken pushToken, PushMessage message);
}
