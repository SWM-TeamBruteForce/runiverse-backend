package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.running.common.PolylineEncoder;
import com.runiverse.running_service.application.running.port.out.CreateRunningCombosPort;
import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.domain.running.combo.RunningCombo;
import com.runiverse.running_service.domain.running.combo.vo.ComboRoute;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RunningComboPersistenceAdapter implements CreateRunningCombosPort {

    // hibernate.jdbc.batch_size와 맞춰야 실제로 묶여 나간다
    private static final int BATCH_SIZE = 100;
    private final EntityManager entityManager;

    @Override
    public void createAll(List<RunningCombo> combos) {
        int persisted = 0;
        for (RunningCombo combo : combos) {
            // 방 프록시 — 실제 SELECT 없이 FK 값만 쓴다. clear 뒤에도 다시 얻도록 매번 받는다
            RunningRoomJpaEntity room = entityManager.getReference(
                    RunningRoomJpaEntity.class, combo.getRunningRoomId().value());
            entityManager.persist(RunningComboJpaEntity.create(
                    room,
                    combo.getFirstUserId().value(),
                    combo.getSecondUserId().value(),
                    combo.getSegmentNumber().value(),
                    combo.getComboCount().value(),
                    encode(combo.getFirstRoute()),
                    encode(combo.getSecondRoute()),
                    combo.getPeriod().startAt(),
                    combo.getPeriod().endAt()));
            if (++persisted % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }

    // 기록 경로와 같은 형식(encoded polyline, precision 5)으로 담는다 — 클라가 같은 디코더로 읽는다
    private static String encode(Optional<ComboRoute> route) {
        return route
                .map(points -> PolylineEncoder.encodePoints(points.points().stream()
                        .map(point -> new RoutePoint(point.latitude(), point.longitude()))
                        .toList()))
                .orElse(null);
    }
}
