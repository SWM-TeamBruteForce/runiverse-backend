package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;

import java.util.Optional;

public interface LoadMaxDisplayOrderPort {

    // 이 사람 카드 중 가장 큰 순위 — 새 카드는 이 값 + 1(없으면 1)을 받는다.
    // 기록 행 잠금은 같은 기록끼리만 줄을 세워, 다른 기록으로 동시에 만들면 같은 값을 읽을 수 있다 —
    // 그때는 uk_record_card_user_display_order가 하나를 막고 막힌 쪽은 409다(재시도하지 않는다)
    Optional<DisplayOrder> loadMax(UserId userId);
}
