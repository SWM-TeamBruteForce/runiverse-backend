package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.player.RunningPlayer;

public interface UpdateMatchApplicationPort {

    // 취소·이탈로 바뀐 status와 deleted_at을 반영한다
    void update(RunningPlayer player);
}
