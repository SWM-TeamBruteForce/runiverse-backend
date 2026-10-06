package com.runiverse.running_service.infrastructure.storage;

import com.runiverse.running_service.application.running.port.out.GpsTrackUpload;
import com.runiverse.running_service.application.running.port.out.SaveGpsTrackPort;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GpsTrackS3Adapter implements SaveGpsTrackPort {

    private static final String KEY_PREFIX = "gps-tracks";
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final S3Client s3Client;
    private final S3Properties properties;

    @Override
    public String save(GpsTrackUpload upload) {
        String key = key(upload);
        s3Client.putObject(
                request -> request
                        .bucket(properties.gpsTrackBucket())
                        .key(key)
                        .contentType("application/json"),
                RequestBody.fromString(envelope(upload), StandardCharsets.UTF_8));
        return key;
    }

    // 날짜는 러닝 시작 시각이다 — 업로드 시각을 쓰면 재시도가 다른 키로 가서
    // 같은 러닝의 객체가 둘이 된다
    private String key(GpsTrackUpload upload) {
        return "%s/%s/%d/%s.json".formatted(
                KEY_PREFIX, upload.userId(), upload.runningRoomId(),
                upload.startAt().format(KEY_DATE));
    }

    private String envelope(GpsTrackUpload upload) {
        // raw는 이미 유효한 JSON 배열이라 파싱했다 다시 만들지 않고 그대로 끼운다.
        // 보간값은 Long·UUID·ISO 시각과 컴파일 상수뿐이라 이스케이프가 필요 없다
        return """
                {"schemaVersion":%d,"fields":[%s],"runningRoomId":%d,"userId":"%s",\
                "startAt":"%s","endAt":"%s","points":%s}"""
                .formatted(
                        TrackPoint.SCHEMA_VERSION,
                        quotedFields(),
                        upload.runningRoomId(),
                        upload.userId(),
                        withOffset(upload.startAt()),
                        withOffset(upload.endAt()),
                        upload.raw());
    }

    private static String quotedFields() {
        return TrackPoint.COMPACT_FIELDS.stream()
                .map("\"%s\""::formatted)
                .collect(Collectors.joining(","));
    }

    // 오프셋을 붙인다 — 이 객체는 DB 밖에서 단독으로 읽히므로 벽시계만 남기면 UTC로 오독된다
    private static String withOffset(LocalDateTime at) {
        return at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
