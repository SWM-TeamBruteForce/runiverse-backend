package com.runiverse.running_service.domain.running.record;

import com.runiverse.running_service.domain.running.record.vo.RoutePolyline;
import com.runiverse.running_service.domain.running.record.vo.SegmentNumber;
import lombok.Getter;

// 끊긴 곳에서 나뉜 경로 조각. 구간은 끝 경계점이 있는 조각에 속하고, 경로 범위는 이 조각 안의 점 번호다
@Getter
public class RunningSegment {

    private final SegmentNumber segmentNumber;
    private final RoutePolyline routePolyline;

    private RunningSegment(int segmentNumber, String routePolyline) {
        this.segmentNumber = new SegmentNumber(segmentNumber);
        this.routePolyline = new RoutePolyline(routePolyline);
    }

    // 기록이 조립할 때만 쓰는 통로 — 밖에서는 SegmentDraft까지만 만들 수 있다
    static RunningSegment from(SegmentDraft draft) {
        return new RunningSegment(draft.segmentNumber(), draft.routePolyline());
    }
}
