package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.Instant;

// 참가자 두 명 사이의 콤보 상태. 콤보는 사람이 아니라 관계에 붙어서
// A-B·A-C·B-C가 각각 독립적으로 쌓인다.
// 현재 콤보 횟수는 담지 않는다 — 시작 시각에서 계산하는 값이라 저장하면 같은 사실이 두 벌이 된다
public record RunningComboPair(
        UserId first,
        UserId second,
        // 지금 이어지고 있는 콤보가 시작된 시각. 끊긴 관계는 null이다
        Instant startedAt,
        int maxComboCount,
        // 남은 봐주기 횟수. 0인 채로 또 빗나가야 콤보가 끊긴다
        int missLeft
) {

    // 두 참가자를 정렬해 담는다 — A-B와 B-A가 서로 다른 관계로 갈라지면
    // 한쪽이 보낸 배치가 쌓아 둔 콤보를 다른 쪽 배치가 못 찾고 1부터 다시 센다
    public RunningComboPair {
        if (first.value().compareTo(second.value()) > 0) {
            UserId swapped = first;
            first = second;
            second = swapped;
        }
    }

    // 아직 한 번도 겹치지 않은 관계 — 저장된 것이 없을 때의 출발점이다
    public static RunningComboPair empty(UserId first, UserId second, int missAllowance) {
        return new RunningComboPair(first, second, null, 0, missAllowance);
    }

    public boolean inCombo() {
        return startedAt != null;
    }

    public boolean contains(UserId userId) {
        return first.equals(userId) || second.equals(userId);
    }

    // 관계에 낀 참가자로만 부른다 — contains()로 거른 뒤에 쓴다
    public UserId partnerOf(UserId userId) {
        return first.equals(userId) ? second : first;
    }
}
