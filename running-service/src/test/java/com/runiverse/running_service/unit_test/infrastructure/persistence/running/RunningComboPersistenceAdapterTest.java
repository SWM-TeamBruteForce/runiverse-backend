package com.runiverse.running_service.unit_test.infrastructure.persistence.running;

import com.runiverse.running_service.application.running.common.PolylineDecoder;
import com.runiverse.running_service.domain.running.combo.RunningCombo;
import com.runiverse.running_service.domain.running.combo.vo.ComboPoint;
import com.runiverse.running_service.infrastructure.persistence.running.RunningComboJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.running.RunningComboPersistenceAdapter;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRoomJpaEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 어댑터의 변환·배치만 검증한다 — 실제 INSERT와 제약은 실행되지 않는다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 콤보 저장 어댑터 단위 테스트")
class RunningComboPersistenceAdapterTest {

    private static final long ROOM_ID = 12L;
    private static final UUID FIRST = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000a1");
    private static final UUID SECOND = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000b2");
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 13, 7, 0, 20);

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private RunningComboPersistenceAdapter adapter;

    private static RunningCombo combo(int segmentNumber, List<ComboPoint> secondRoute) {
        return RunningCombo.archive()
                .runningRoomId(ROOM_ID)
                .firstUserId(FIRST)
                .secondUserId(SECOND)
                .segmentNumber(segmentNumber)
                .comboCount(2)
                .startAt(START)
                .endAt(START.plusSeconds(20))
                .firstRoute(List.of(new ComboPoint(37.56650, 126.97800), new ComboPoint(37.56700, 126.97850)))
                .secondRoute(secondRoute)
                .build();
    }

    @Test
    @DisplayName("달성 위치를 기록 경로와 같은 polyline 형식으로 담아 넣는다")
    void persistsEncodedRoutes() {
        // when
        adapter.createAll(List.of(combo(1, null)));

        // then
        ArgumentCaptor<RunningComboJpaEntity> captor = ArgumentCaptor.forClass(RunningComboJpaEntity.class);
        verify(entityManager).persist(captor.capture());
        RunningComboJpaEntity saved = captor.getValue();
        assertThat(saved.getFirstUserId()).isEqualTo(FIRST);
        assertThat(saved.getSecondUserId()).isEqualTo(SECOND);
        assertThat(saved.getSegmentNumber()).isEqualTo(1);
        assertThat(saved.getComboCount()).isEqualTo(2);
        assertThat(saved.getStartAt()).isEqualTo(START);
        assertThat(PolylineDecoder.decode(saved.getFirstRoutePolyline()))
                .hasSize(2)
                .first().satisfies(point -> assertThat(point.latitude()).isCloseTo(37.56650, within(1e-5)));
        // 기록이 없는 참가자 쪽은 비운다
        assertThat(saved.getSecondRoutePolyline()).isNull();
        verify(entityManager).getReference(RunningRoomJpaEntity.class, ROOM_ID);
    }

    @Test
    @DisplayName("100건마다 flush·clear해 묶어 보낸다")
    void flushesEveryBatch() {
        // given
        List<RunningCombo> combos = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            combos.add(combo(i, null));
        }

        // when
        adapter.createAll(combos);

        // then
        verify(entityManager, times(100)).persist(any(RunningComboJpaEntity.class));
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    @DisplayName("100건보다 적으면 중간에 비우지 않는다 — 커밋이 한 번에 보낸다")
    void doesNotFlushSmallBatch() {
        // when
        adapter.createAll(List.of(combo(1, null), combo(2, null)));

        // then
        verify(entityManager, never()).flush();
        verify(entityManager, times(2)).getReference(eq(RunningRoomJpaEntity.class), eq(ROOM_ID));
    }
}
