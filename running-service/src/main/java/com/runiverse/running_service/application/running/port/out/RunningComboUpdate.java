package com.runiverse.running_service.application.running.port.out;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record RunningComboUpdate(
        // 보낸 참가자와 그와 관계가 얽힌 참가자 — 지금 겹친 사람과 방금 끊긴 사람이다
        Set<UUID> recipients,
        // 콤보가 살아 있는 관계만 담는다. 끊긴 관계가 빠지는 것이 곧 끊김 통지다
        List<RunningComboRelation> relations
) {

}
