package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.common.port.out.UploadedImage;
import com.runiverse.running_service.application.recordcard.port.out.CompleteMultipartUploadPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardImagePort;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// 기록 카드 버킷 — 다 올라간 객체와 나눠 올리는 중인 조각만 흉내 낸다. 합치면 조각이 객체가 되고 UploadId는 사라진다
public class FakeRecordCardObjectStore implements LoadRecordCardImagePort, CompleteMultipartUploadPort {

    private record PendingUpload(String key, String contentType, long sizeBytes, Set<Integer> parts) {

    }

    private final Map<String, UploadedImage> objects = new HashMap<>();
    private final Map<String, PendingUpload> uploads = new HashMap<>();

    @Override
    public java.util.Optional<UploadedImage> load(String imageKey) {
        return java.util.Optional.ofNullable(objects.get(imageKey));
    }

    @Override
    public MultipartCompletion complete(String imageKey, String uploadId, int expectedPartCount) {
        PendingUpload upload = uploads.get(uploadId);
        if (upload == null) {
            return MultipartCompletion.UPLOAD_NOT_FOUND;
        }
        if (upload.parts().size() < expectedPartCount) {
            return MultipartCompletion.PARTS_MISSING;
        }
        objects.put(imageKey, new UploadedImage(upload.sizeBytes(), upload.contentType()));
        uploads.remove(uploadId);
        return MultipartCompletion.COMPLETED;
    }

    // 아래는 앱의 업로드를 흉내 내는 준비 전용
    public void putObject(String key, long sizeBytes, String contentType) {
        objects.put(key, new UploadedImage(sizeBytes, contentType));
    }

    public void uploadPart(String uploadId, String key, String contentType, long sizeBytes, int partNumber) {
        uploads.computeIfAbsent(uploadId, id -> new PendingUpload(key, contentType, sizeBytes, new HashSet<>()))
                .parts().add(partNumber);
    }

    public boolean hasObject(String key) {
        return objects.containsKey(key);
    }
}
