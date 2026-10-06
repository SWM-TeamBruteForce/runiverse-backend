package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.RunningComboRelation;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

// 방 채널을 건너는 콤보 통. 받는 인스턴스가 수신자별로 깎아야 해서
// 방 전체의 관계와 수신자 목록을 그대로 나른다
public record ComboMessage(
        Long runningRoomId,
        Set<UUID> recipients,
        List<RunningComboRelation> relations
) {

    public static ComboMessage of(Long runningRoomId, RunningComboUpdate update) {
        return new ComboMessage(runningRoomId, update.recipients(), update.relations());
    }

    public RunningComboUpdate toUpdate() {
        return new RunningComboUpdate(recipients, relations);
    }
}
