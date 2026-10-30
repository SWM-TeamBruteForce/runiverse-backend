package com.runiverse.running_service.application.device.command.push;

import com.runiverse.running_service.application.device.port.out.PushType;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@ConfigurationProperties(prefix = "push")
@Validated
public record PushProperties(
        // 보내지 않는 종류 — 알림함·앱 스트림은 그대로 가고 폰 알림만 빠진다
        @NotNull Set<PushType> disabledTypes
) {

}
