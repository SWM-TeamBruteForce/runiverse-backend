package com.runiverse.running_service.application.running.port.out;

import java.time.LocalDateTime;
import java.util.List;

public record TrackPoint(
        // 아래 넷과 pauseSegment는 Location.isValid()가 null을 막아준다
        long sequence,
        double latitude,
        double longitude,
        // 단말이 못 잴 수 있다 — 배치를 통째로 버리지 않으려고 null을 그대로 받는다
        Double altitudeMeters,
        double accuracyMeters,
        Double speedMetersPerSecond,
        Double headingDegrees,
        Integer cadenceSpm,
        Integer currentPaceSecondsPerKm,
        LocalDateTime recordedAt,
        // 일시정지 구간 번호 — 버튼 일시정지마다 1씩 오른다. 이웃한 두 좌표의 값이 다르면 그 사이가 일시정지다
        int pauseSegment
) {

    // compact 배열의 자리 순서 정본 — RunningTrackRedisAdapter.compact()/parse()와
    // S3 봉투가 이 하나를 공유한다. 순서를 바꾸면 SCHEMA_VERSION을 올린다
    public static final int SCHEMA_VERSION = 2;
    // 순번 상한 — 입구 검사와 Redis 비트맵 크기가 이 하나를 공유한다.
    // TTL 6h × 1Hz = 21,600이라 정상 클라는 닿지 않는 sanity bound다
    public static final long MAX_SEQUENCE = 100_000L;
    // 일시정지 구간 번호 상한 — 순번보다 커질 수 없으니 같은 값을 쓴다
    public static final int MAX_PAUSE_SEGMENT = (int) MAX_SEQUENCE;
    public static final List<String> COMPACT_FIELDS = List.of(
            "sequence", "latitude", "longitude", "altitudeMeters", "accuracyMeters",
            "speedMetersPerSecond", "headingDegrees", "cadenceSpm",
            "currentPaceSecondsPerKm", "recordedAtEpochSecond", "pauseSegment");
}
