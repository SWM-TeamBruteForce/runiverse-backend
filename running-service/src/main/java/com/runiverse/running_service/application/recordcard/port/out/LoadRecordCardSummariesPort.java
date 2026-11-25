package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;

import java.util.Collection;
import java.util.List;

public interface LoadRecordCardSummariesPort {

    // ownerId의 카드 중 공개 범위가 visibilities에 드는 것을 순위 역순으로 — beforeDisplayOrder보다 작은 순위부터 limit개.
    // 숨긴 방의 기록에 붙은 카드는 빠진다(7-1과 같다)
    List<RecordCardSummary> loadSummaries(UserId ownerId, Collection<RecordCardVisibility> visibilities,
                                          Integer beforeDisplayOrder, int limit);
}
