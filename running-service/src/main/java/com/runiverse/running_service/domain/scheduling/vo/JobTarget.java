package com.runiverse.running_service.domain.scheduling.vo;

import com.runiverse.running_service.domain.scheduling.exception.InvalidJobTargetException;

// 타입마다 가리키는 테이블이 달라 식별자를 문자열로 담는다 — MATCH_CLOSE면 running_room_id다.
// 둘이 함께 예약의 대상을 이룬다(scheduled_jobs의 UNIQUE도 이 짝이다)
public record JobTarget(ScheduledJobType type, String id) {

    public JobTarget {
        if (type == null || id == null || id.isBlank()) {
            throw new InvalidJobTargetException();
        }
    }

    public static JobTarget of(ScheduledJobType type, Long id) {
        if (id == null) {
            throw new InvalidJobTargetException();
        }
        return new JobTarget(type, String.valueOf(id));
    }

    // 대상 애그리거트를 찾을 때 되돌린다 — 숫자가 아닌 식별자를 쓰는 타입이 생기면 그때 갈린다
    public Long idAsLong() {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new InvalidJobTargetException();
        }
    }
}
