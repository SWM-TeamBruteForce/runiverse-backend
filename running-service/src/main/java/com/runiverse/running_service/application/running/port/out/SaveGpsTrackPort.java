package com.runiverse.running_service.application.running.port.out;

public interface SaveGpsTrackPort {

    // 반환값이 running_records.gps_track_key에 그대로 들어간다
    String save(GpsTrackUpload upload);
}
