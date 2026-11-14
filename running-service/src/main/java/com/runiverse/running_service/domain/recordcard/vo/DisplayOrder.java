package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.InvalidDisplayOrderException;

// 작성자별 카드 순위 — 1이 가장 오래된 카드다. 카드를 지우면 행이 사라지고 남은 카드의 순위는 당기지 않는다
public record DisplayOrder(int value) {

    private static final int FIRST_VALUE = 1;

    public static final DisplayOrder FIRST = new DisplayOrder(FIRST_VALUE);

    public DisplayOrder {
        if (value < FIRST_VALUE) {
            throw new InvalidDisplayOrderException();
        }
    }

    // 지금 가장 큰 순위 다음 자리 — 새 카드가 받는다
    public DisplayOrder next() {
        return new DisplayOrder(value + 1);
    }
}
