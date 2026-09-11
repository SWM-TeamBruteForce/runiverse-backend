package com.runiverse.running_service.application.running.command.combo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "running-combo")
@Validated
public record RunningComboProperties(
        // 콤보가 없을 때 이 거리 안에 들어오면 붙는다
        @NotNull @Positive Integer enterMeters,
        // 붙어 있는 동안은 이만큼 벌어져도 유지한다 —
        // 창이 하나면 경계에 걸친 두 사람의 콤보가 판정마다 켜졌다 꺼진다
        @NotNull @Positive Integer exitMeters,
        // 이보다 오래된 누적 거리는 비교에서 뺀다 — 연결이 끊긴 사람은 마지막 거리에
        // 멈춰 서 있어, 남겨두면 그 지점을 지나는 사람에게 잠깐 콤보가 붙는다
        @NotNull Duration freshness,
        // 유지 횟수 1회에 해당하는 시간
        @NotNull Duration tick,
        // 연속 이만큼 빗나가야 콤보가 깨진다. 상대 데이터가 낡았거나 GPS가 튄
        // 한 번의 판정으로 오래 쌓인 콤보가 사라지면 안 된다.
        // 0이면 봐주지 않고 첫 판정에서 바로 끊는다
        @NotNull @PositiveOrZero Integer missAllowance
) {

}
