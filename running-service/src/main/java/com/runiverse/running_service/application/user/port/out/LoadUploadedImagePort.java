package com.runiverse.running_service.application.user.port.out;

import java.util.Optional;

public interface LoadUploadedImagePort {

    Optional<UploadedImage> load(String key);
}
