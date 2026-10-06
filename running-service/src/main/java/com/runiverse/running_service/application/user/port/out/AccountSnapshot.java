package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// 탈퇴가 지우기 전에 한 번에 떠 두는 값 — 계정·소셜 연동·온보딩이 섞여 있어
// 어느 애그리거트도 그대로 담지 못한다
public record AccountSnapshot(
        UUID userId,
        String email,
        LocalDateTime joinedAt,
        // 소셜 연동이 없으면 null
        Provider provider,
        String providerId,
        // 온보딩 전에 탈퇴하면 아래 여섯이 전부 null이다
        String nickname,
        Gender gender,
        LocalDate birthday,
        Integer avgPace,
        BigDecimal weight,
        BigDecimal height
) {

    public boolean hasOnboarded() {
        return nickname != null;
    }
}
