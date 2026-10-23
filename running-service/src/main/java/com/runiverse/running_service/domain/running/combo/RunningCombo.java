package com.runiverse.running_service.domain.running.combo;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.combo.exception.ComboRouteSizeMismatchException;
import com.runiverse.running_service.domain.running.combo.exception.ComboUsersNotOrderedException;
import com.runiverse.running_service.domain.running.combo.vo.ComboCount;
import com.runiverse.running_service.domain.running.combo.vo.ComboPoint;
import com.runiverse.running_service.domain.running.combo.vo.ComboRoute;
import com.runiverse.running_service.domain.running.combo.vo.ComboSegmentNumber;
import com.runiverse.running_service.domain.running.metric.vo.RunningPeriod;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 두 참가자가 함께 달린 콤보 한 구간 — 방이 닫힐 때 한 번 만들어지고 고치지 않는다.
// 시각·횟수는 쌍의 값이고, 달성 위치만 사람마다 다르다(원격 러닝이라 같은 시각에 각자 다른 길 위에 있다)
@Getter
public class RunningCombo {

    private final RunningRoomId runningRoomId;
    private final UserId firstUserId;
    private final UserId secondUserId;
    private final ComboSegmentNumber segmentNumber;
    private final ComboCount comboCount;
    private final RunningPeriod period;
    // 그 참가자의 기록이 없으면 비어 있다
    private final ComboRoute firstRoute;
    private final ComboRoute secondRoute;

    @Builder
    private RunningCombo(Long runningRoomId, UUID firstUserId, UUID secondUserId,
                         int segmentNumber, int comboCount,
                         LocalDateTime startAt, LocalDateTime endAt,
                         List<ComboPoint> firstRoute, List<ComboPoint> secondRoute) {
        this.runningRoomId = new RunningRoomId(runningRoomId);
        this.firstUserId = new UserId(firstUserId);
        this.secondUserId = new UserId(secondUserId);
        // 같은 사람끼리의 쌍과 뒤집힌 쌍을 함께 막는다 — A-B와 B-A가 다른 행으로 갈리면
        // 같은 콤보가 두 번 남는다. 앱과 DB의 UUID 정렬은 UUIDv7에서만 같다
        if (this.firstUserId.value().compareTo(this.secondUserId.value()) >= 0) {
            throw new ComboUsersNotOrderedException();
        }
        this.segmentNumber = new ComboSegmentNumber(segmentNumber);
        this.comboCount = new ComboCount(comboCount);
        this.period = new RunningPeriod(startAt, endAt);
        this.firstRoute = routeOf(firstRoute, this.comboCount);
        this.secondRoute = routeOf(secondRoute, this.comboCount);
    }

    // 방이 닫힐 때 Redis의 콤보 구간을 옮겨 만든다 — 아직 ID가 없다.
    // 읽는 경로가 생기면 restore를 더한다
    public static RunningComboBuilder archive() {
        return builder();
    }

    public Optional<ComboRoute> getFirstRoute() {
        return Optional.ofNullable(firstRoute);
    }

    public Optional<ComboRoute> getSecondRoute() {
        return Optional.ofNullable(secondRoute);
    }

    // 기록이 없는 참가자는 null로 넘어온다. 있으면 i번째 점이 i콤보라 점 수가 유지 횟수와 같아야 한다 —
    // 어긋나면 클라가 찍은 "n콤보" 표시가 엉뚱한 자리를 가리킨다
    private static ComboRoute routeOf(List<ComboPoint> points, ComboCount comboCount) {
        if (points == null) {
            return null;
        }
        ComboRoute route = new ComboRoute(points);
        if (route.size() != comboCount.value()) {
            throw new ComboRouteSizeMismatchException();
        }
        return route;
    }
}
