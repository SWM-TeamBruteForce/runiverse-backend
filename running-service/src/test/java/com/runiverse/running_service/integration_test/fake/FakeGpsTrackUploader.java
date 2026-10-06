package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.GpsTrackUpload;
import com.runiverse.running_service.application.running.port.out.SaveGpsTrackPort;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// GpsTrackS3Adapter를 대신한다 — 키 규칙까지 같게 둬서
// 재시도가 같은 객체를 덮어쓴다는 성질이 테스트에서도 성립한다
public class FakeGpsTrackUploader implements SaveGpsTrackPort {

    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final List<GpsTrackUpload> uploads = new ArrayList<>();

    @Override
    public String save(GpsTrackUpload upload) {
        uploads.add(upload);
        return "gps-tracks/%s/%d/%s.json".formatted(
                upload.userId(), upload.runningRoomId(), upload.startAt().format(KEY_DATE));
    }

    // 검증 전용
    public List<GpsTrackUpload> uploads() {
        return List.copyOf(uploads);
    }

    public boolean isEmpty() {
        return uploads.isEmpty();
    }
}
