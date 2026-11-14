package com.runiverse.running_service.application.common.port.out;

public interface GenerateUploadUrlPort {

    String generate(String key, String contentType, long sizeBytes);
}
