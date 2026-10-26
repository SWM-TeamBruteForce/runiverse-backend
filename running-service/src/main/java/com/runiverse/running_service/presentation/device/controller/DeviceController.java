package com.runiverse.running_service.presentation.device.controller;

import com.runiverse.running_service.application.device.command.register.RegisterDeviceCommand;
import com.runiverse.running_service.application.device.port.in.RegisterDeviceUsecase;
import com.runiverse.running_service.presentation.common.filter.AppVersionFilter;
import com.runiverse.running_service.presentation.device.request.RegisterDeviceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("users/me/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final RegisterDeviceUsecase registerDeviceUsecase;

    // 플랫폼·앱 버전은 모든 요청에 싣는 헤더에서 읽는다. 버전 검사 필터가 먼저 거르지만, 필터 밖에서 불려도
    // 도메인 예외(500)까지 가지 않게 여기서도 본다. required = false인 것은 헤더가 없을 때 스프링이 던지는 예외가
    // 전역 처리기에 없어 500이 되기 때문이다 — 빠진 헤더는 @NotBlank가 400으로 막는다
    @PostMapping
    public ResponseEntity<Void> registerDevice(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = AppVersionFilter.PLATFORM_HEADER, required = false)
            @NotBlank(message = "입력값이 올바르지 않습니다.")
            @Pattern(regexp = "^(?i)(IOS|ANDROID)$", message = "입력값이 올바르지 않습니다.")
            String platform,
            @RequestHeader(name = AppVersionFilter.APP_VERSION_HEADER, required = false)
            @NotBlank(message = "입력값이 올바르지 않습니다.")
            @Pattern(regexp = "^[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}$", message = "입력값이 올바르지 않습니다.")
            String appVersion,
            @Valid @RequestBody RegisterDeviceRequest request
    ) {
        registerDeviceUsecase.handle(new RegisterDeviceCommand(
                UUID.fromString(jwt.getSubject()),
                request.deviceId(),
                request.pushToken(),
                platform,
                appVersion
        ));
        return ResponseEntity.noContent().build();
    }
}
