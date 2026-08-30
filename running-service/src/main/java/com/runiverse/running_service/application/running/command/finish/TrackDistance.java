package com.runiverse.running_service.application.running.command.finish;

import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.util.List;

public final class TrackDistance {

    private static final double EARTH_RADIUS_METERS = 6371008.8;   // IUGG 평균 반경

    private TrackDistance() {
    }

    // 길이는 점 개수와 같고 첫 원소는 0이다.
    // 10m 경계 탐색·목표 지점 절단·구간 거리 계산이 전부 이 배열 위에서 끝난다
    public static double[] cumulativeMeters(List<TrackPoint> points) {
        double[] cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + between(points.get(i - 1), points.get(i));
        }
        return cumulative;
    }

    public static double between(TrackPoint from, TrackPoint to) {
        return between(from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    // 러닝 중 누적은 직전 좌표를 위경도만 들고 있다 — 계산식은 위와 하나를 공유한다
    public static double between(
            double fromLatitudeDegrees, double fromLongitudeDegrees,
            double toLatitudeDegrees, double toLongitudeDegrees) {
        double fromLatitude = Math.toRadians(fromLatitudeDegrees);
        double toLatitude = Math.toRadians(toLatitudeDegrees);
        double latitudeDelta = toLatitude - fromLatitude;
        double longitudeDelta = Math.toRadians(toLongitudeDegrees - fromLongitudeDegrees);
        double a = square(Math.sin(latitudeDelta / 2))
                + Math.cos(fromLatitude) * Math.cos(toLatitude)
                * square(Math.sin(longitudeDelta / 2));
        // asin(√a)로 바꾸지 않는다 — 수학적으로는 같지만 두 점이 멀어질수록 수치가 불안정하다
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double square(double value) {
        return value * value;
    }
}
