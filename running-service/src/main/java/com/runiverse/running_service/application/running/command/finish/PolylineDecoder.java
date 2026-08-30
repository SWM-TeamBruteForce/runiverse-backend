package com.runiverse.running_service.application.running.command.finish;

import com.runiverse.running_service.application.running.port.out.RoutePoint;

import java.util.ArrayList;
import java.util.List;

public final class PolylineDecoder {

    private static final double FACTOR = 1e5;        // 정밀도 5
    private static final int CHUNK_MASK = 0x1f;      // 한 번에 떼는 5비트
    private static final int CONTINUATION = 0x20;    // "뒤에 더 있다" 표시
    private static final int ASCII_OFFSET = 63;

    private PolylineDecoder() {

    }

    public static List<RoutePoint> decode(String encoded) {
        List<RoutePoint> points = new ArrayList<>();
        Cursor cursor = new Cursor(encoded);
        long latitude = 0;
        long longitude = 0;
        while (cursor.hasNext()) {
            // 값은 직전 점과의 차분이다 — 누적해야 절대 좌표가 된다. 위도·경도가 번갈아 나온다
            latitude += cursor.nextSigned();
            longitude += cursor.nextSigned();
            points.add(new RoutePoint(latitude / FACTOR, longitude / FACTOR));
        }
        return points;
    }

    // 읽은 위치를 값과 함께 돌려줄 방법이 마땅치 않아 커서를 따로 둔다
    private static final class Cursor {

        private final String encoded;
        private int index;

        private Cursor(String encoded) {
            this.encoded = encoded;
        }

        private boolean hasNext() {
            return index < encoded.length();
        }

        // 5비트씩 모아 값 하나를 만든다. continuation 비트가 꺼진 조각이 마지막이다
        private long nextSigned() {
            long shifted = 0;
            int shift = 0;
            int chunk;
            do {
                chunk = encoded.charAt(index++) - ASCII_OFFSET;
                shifted |= (long) (chunk & CHUNK_MASK) << shift;
                shift += 5;
            } while (chunk >= CONTINUATION);
            // 인코더가 부호를 최하위 비트로 옮겨놨다 — 되돌린다
            return (shifted & 1) != 0 ? ~(shifted >> 1) : shifted >> 1;
        }
    }
}
