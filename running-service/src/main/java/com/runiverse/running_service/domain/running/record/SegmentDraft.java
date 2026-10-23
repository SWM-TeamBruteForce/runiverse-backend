package com.runiverse.running_service.domain.running.record;

// 기록이 조립할 경로 조각 하나 — 끊긴 곳에서 나뉜 10m 경계점(encoded polyline)이다
public record SegmentDraft(int segmentNumber, String routePolyline) {

}
