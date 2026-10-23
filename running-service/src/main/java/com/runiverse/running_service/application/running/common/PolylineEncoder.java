package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.RoutePoint;

import java.util.List;
import java.util.function.ToDoubleFunction;

public final class PolylineEncoder {

    private static final double FACTOR = 1e5;        // 정밀도 5
    private static final int CHUNK_MASK = 0x1f;      // 한 번에 떼는 5비트
    private static final int CONTINUATION = 0x20;    // "뒤에 더 있다" 표시
    private static final int ASCII_OFFSET = 63;

    private PolylineEncoder() {
    }

    public static String encode(List<BoundaryPoint> points) {
        return encode(points, BoundaryPoint::latitude, BoundaryPoint::longitude);
    }

    // 경계점이 아닌 좌표 목록 — 경로에서 골라낸 콤보 달성 위치를 경로와 같은 형식으로 담을 때 쓴다
    public static String encodePoints(List<RoutePoint> points) {
        return encode(points, RoutePoint::latitude, RoutePoint::longitude);
    }

    private static <T> String encode(List<T> points, ToDoubleFunction<T> latitudeOf,
                                     ToDoubleFunction<T> longitudeOf) {
        StringBuilder encoded = new StringBuilder();
        long previousLatitude = 0;
        long previousLongitude = 0;
        for (T point : points) {
            // 먼저 반올림하고 그다음 차분을 낸다 — 순서가 바뀌면 점마다 오차가 쌓인다
            long latitude = Math.round(latitudeOf.applyAsDouble(point) * FACTOR);
            long longitude = Math.round(longitudeOf.applyAsDouble(point) * FACTOR);
            appendSigned(latitude - previousLatitude, encoded);
            appendSigned(longitude - previousLongitude, encoded);
            previousLatitude = latitude;
            previousLongitude = longitude;
        }
        return encoded.toString();
    }

    // 부호를 최하위 비트로 옮기고 5비트씩 끊어 63을 더한다.
    // 마지막 조각에는 continuation 비트를 붙이지 않는다 — 붙이면 디코더가 계속 읽으려 한다
    private static void appendSigned(long value, StringBuilder out) {
        long shifted = value < 0 ? ~(value << 1) : value << 1;
        while (shifted >= CONTINUATION) {
            out.append((char) ((CONTINUATION | (shifted & CHUNK_MASK)) + ASCII_OFFSET));
            shifted >>= 5;
        }
        out.append((char) (shifted + ASCII_OFFSET));
    }
}
