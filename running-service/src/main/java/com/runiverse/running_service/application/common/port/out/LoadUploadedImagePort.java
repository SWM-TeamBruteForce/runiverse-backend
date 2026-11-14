package com.runiverse.running_service.application.common.port.out;

import java.util.Optional;

public interface LoadUploadedImagePort {

    Optional<UploadedImage> load(String key);
}
