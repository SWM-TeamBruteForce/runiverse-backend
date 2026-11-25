package com.runiverse.running_service.application.running.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface LoadSegmentPolylinesPort {

    // 기록마다 구간 polyline을 번호순으로 한 번에 읽는다 — 기록마다 부르면 기록 수만큼 쿼리가 나간다.
    // 구간이 없는 기록은 결과에 없다
    Map<Long, List<String>> loadSegmentPolylines(Collection<Long> runningRecordIds);
}
