package com.runiverse.running_service.application.running.port.out;

// 폴리라인을 푼 좌표 한 점. BoundaryPoint와 달리 거리·시각·실측 인덱스를 갖지 않는다 —
// 화면에 찍을 값만 남긴다
public record RoutePoint(double latitude, double longitude) {

}
