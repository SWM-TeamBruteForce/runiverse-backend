package com.runiverse.running_service.unit_test.infrastructure.persistence.notification;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.notification.port.out.InvitationSnapshot;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.infrastructure.persistence.notification.InvitationSnapshotPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// JPQL 문법·조인은 실제 DB에서만 확인된다 — 여기서는 조건과 결과를 묶는 규칙만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("초대 상태 조회 어댑터 단위 테스트")
class InvitationSnapshotPersistenceAdapterTest {

    private static final UUID INVITEE_ID = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 28, 19, 30);

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<InvitationSnapshot> query;

    @InjectMocks
    private InvitationSnapshotPersistenceAdapter adapter;

    private void givenRows(Stream<InvitationSnapshot> rows) {
        when(entityManager.createQuery(anyString(), eq(InvitationSnapshot.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(rows);
    }

    @Test
    @DisplayName("받은 사람의 초대를 방 목록으로 한 번에 읽어 방별로 돌려준다")
    void loadsSnapshotsByRoom() {
        // given
        InvitationSnapshot first = new InvitationSnapshot(130L, RoomInvitationStatus.PENDING, START, 7_000);
        InvitationSnapshot second = new InvitationSnapshot(131L, RoomInvitationStatus.EXPIRED, START, null);
        givenRows(Stream.of(first, second));

        // when
        Map<Long, InvitationSnapshot> snapshots = adapter.loadSnapshots(new UserId(INVITEE_ID), List.of(130L, 131L));

        // then
        assertThat(snapshots).containsOnly(Map.entry(130L, first), Map.entry(131L, second));
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(InvitationSnapshot.class));
        assertThat(jpql.getValue())
                .contains("FROM RunningRoomInvitationJpaEntity i")
                .contains("JOIN i.room r")
                .contains("i.inviteeId = :inviteeId")
                .contains("r.runningRoomId IN :runningRoomIds");
        verify(query).setParameter("inviteeId", INVITEE_ID);
        verify(query).setParameter("runningRoomIds", List.of(130L, 131L));
    }

    @Test
    @DisplayName("한 방에서 여러 사람에게 받은 초대는 하나로 합친다")
    void mergesRowsOfSameRoom() {
        // given -> 철수와 영희가 같은 방에 초대해 행이 둘이다. 상태는 함께 바뀐다
        InvitationSnapshot fromChulsu = new InvitationSnapshot(130L, RoomInvitationStatus.ACCEPTED, START, 7_000);
        InvitationSnapshot fromYounghee = new InvitationSnapshot(130L, RoomInvitationStatus.ACCEPTED, START, 7_000);
        givenRows(Stream.of(fromChulsu, fromYounghee));

        // when
        Map<Long, InvitationSnapshot> snapshots = adapter.loadSnapshots(new UserId(INVITEE_ID), List.of(130L));

        // then -> 키가 겹쳐도 예외 없이 하나로 남는다
        assertThat(snapshots).containsOnly(Map.entry(130L, fromChulsu));
    }
}
