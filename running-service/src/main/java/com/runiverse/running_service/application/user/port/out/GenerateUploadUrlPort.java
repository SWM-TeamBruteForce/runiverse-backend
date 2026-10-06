package com.runiverse.running_service.application.user.port.out;

public interface GenerateUploadUrlPort {

    String generate(String key, String contentType, long sizeBytes);
}
