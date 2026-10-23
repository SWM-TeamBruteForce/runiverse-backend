package com.runiverse.running_service.unit_test.infrastructure.persistence.running;

import com.runiverse.running_service.application.running.port.out.RecordedRoute;
import com.runiverse.running_service.application.running.port.out.RecordedSplitTime;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 어댑터의 묶기·변환만 검증한다 — JPQL 자체는 실행되지 않는다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 기록 경로 조회 어댑터 단위 테스트")
class RunningRecordRoutesLoadTest {

    private static final long ROOM_ID = 12L;
    private static final UUID A = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000a1");
    private static final UUID B = UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000b2");
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 13, 7, 0);

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Tuple> recordQuery;

    @Mock
    private TypedQuery<Tuple> segmentQuery;

    @Mock
    private TypedQuery<Tuple> splitQuery;

    @InjectMocks
    private RunningRecordPersistenceAdapter adapter;

    private static Tuple record(long recordId, UUID userId) {
        Tuple tuple = mock(Tuple.class);
        given(tuple.get("recordId", Long.class)).willReturn(recordId);
        given(tuple.get("userId", UUID.class)).willReturn(userId);
        return tuple;
    }

    // 조각은 기록 번호·조각 번호 순으로 온다
    private static Tuple segment(long recordId, String polyline) {
        Tuple tuple = mock(Tuple.class);
        given(tuple.get("recordId", Long.class)).willReturn(recordId);
        given(tuple.get("routePolyline", String.class)).willReturn(polyline);
        return tuple;
    }

    private static Tuple split(long recordId, int segmentNumber, int startIndex, int seconds) {
        Tuple tuple = mock(Tuple.class);
        given(tuple.get("recordId", Long.class)).willReturn(recordId);
        given(tuple.get("segmentNumber", Integer.class)).willReturn(segmentNumber);
        given(tuple.get("routeStartIndex", Integer.class)).willReturn(startIndex);
        given(tuple.get("routeEndIndex", Integer.class)).willReturn(startIndex + 1);
        given(tuple.get("startAt", LocalDateTime.class)).willReturn(START.plusSeconds(seconds));
        given(tuple.get("endAt", LocalDateTime.class)).willReturn(START.plusSeconds(seconds + 3));
        return tuple;
    }

    // 기록 → 조각 → 구간 순으로 세 번 읽는다
    private void givenQueries(List<Tuple> records, List<Tuple> segments, List<Tuple> splits) {
        given(entityManager.createQuery(anyString(), eq(Tuple.class)))
                .willReturn(recordQuery, segmentQuery, splitQuery);
        given(recordQuery.setParameter("roomId", ROOM_ID)).willReturn(recordQuery);
        given(recordQuery.getResultList()).willReturn(records);
        if (!records.isEmpty()) {
            given(segmentQuery.setParameter(eq("recordIds"), any())).willReturn(segmentQuery);
            given(segmentQuery.getResultList()).willReturn(segments);
            given(splitQuery.setParameter("roomId", ROOM_ID)).willReturn(splitQuery);
            given(splitQuery.getResultList()).willReturn(splits);
        }
    }

    @Test
    @DisplayName("기록마다 조각과 구간 시각을 묶는다 — 조각·구간 순서는 쿼리 순서 그대로다")
    void groupsSegmentsAndSplitsByRecord() {
        // given -> 기록 둘. A는 일시정지로 조각이 둘이고, 구간 2는 2번 조각에 걸친다
        givenQueries(
                List.of(record(1L, A), record(2L, B)),
                List.of(segment(1L, "polyline-a1"), segment(1L, "polyline-a2"), segment(2L, "polyline-b")),
                List.of(split(1L, 1, 0, 0), split(1L, 2, 0, 3), split(2L, 1, 0, 0)));

        // when
        List<RecordedRoute> routes = adapter.loadRoutes(ROOM_ID);

        // then
        assertThat(routes).hasSize(2);
        RecordedRoute routeOfA = routes.stream().filter(route -> route.userId().equals(A)).findFirst().orElseThrow();
        assertThat(routeOfA.segmentPolylines()).containsExactly("polyline-a1", "polyline-a2");
        assertThat(routeOfA.splits()).containsExactly(
                new RecordedSplitTime(1, 0, 1, START, START.plusSeconds(3)),
                new RecordedSplitTime(2, 0, 1, START.plusSeconds(3), START.plusSeconds(6)));
        RecordedRoute routeOfB = routes.stream().filter(route -> route.userId().equals(B)).findFirst().orElseThrow();
        assertThat(routeOfB.segmentPolylines()).containsExactly("polyline-b");
        assertThat(routeOfB.splits()).hasSize(1);
    }

    @Test
    @DisplayName("기록이 하나도 없으면 조각·구간을 읽지 않는다")
    void skipsSplitsWithoutRecords() {
        // given
        givenQueries(List.of(), List.of(), List.of());

        // when & then
        assertThat(adapter.loadRoutes(ROOM_ID)).isEmpty();
        verify(entityManager, times(1)).createQuery(anyString(), eq(Tuple.class));
    }
}
