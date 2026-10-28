package com.runiverse.running_service.unit_test.infrastructure.persistence.running;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.InvitationDraft;
import com.runiverse.running_service.domain.running.room.RoomInvitation;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.infrastructure.persistence.running.RunningPersistenceAdapter;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRoomInvitationJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRoomJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRoomSessionJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 초대는 방 애그리거트의 내부 엔티티라 방 어댑터가 복원·저장을 함께 맡는다 — 그 부분만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝방 어댑터 초대 복원·저장 단위 테스트")
class RunningRoomInvitationPersistenceTest {

    private static final long ROOM_ID = 130L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 26, 19, 0);

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<RunningRoomJpaEntity> roomQuery;

    @Mock
    private TypedQuery<RunningRoomSessionJpaEntity> sessionQuery;

    @Mock
    private TypedQuery<RunningRoomInvitationJpaEntity> invitationQuery;

    @InjectMocks
    private RunningPersistenceAdapter adapter;

    private UUID host;
    private UUID otherInviter;
    private UUID invitee;

    @BeforeEach
    void setUp() {
        host = UuidCreator.getTimeOrderedEpoch();
        otherInviter = UuidCreator.getTimeOrderedEpoch();
        invitee = UuidCreator.getTimeOrderedEpoch();
    }

    private static RunningRoomJpaEntity roomEntity(RunningRoomType type) {
        RunningRoomJpaEntity entity = RunningRoomJpaEntity.create(
                type, RunningRoomStatus.MATCHING, START, 7_000, 330, 1, 4);
        ReflectionTestUtils.setField(entity, "runningRoomId", ROOM_ID);   // IDENTITY라 DB가 채우는 값
        return entity;
    }

    private void givenLockedRoom(RunningRoomJpaEntity room) {
        when(entityManager.createQuery(anyString(), eq(RunningRoomJpaEntity.class))).thenReturn(roomQuery);
        when(roomQuery.setParameter(anyString(), any())).thenReturn(roomQuery);
        when(roomQuery.setLockMode(any())).thenReturn(roomQuery);
        when(roomQuery.getResultStream()).thenReturn(Stream.of(room));
    }

    private void givenSessions(RunningRoomJpaEntity room) {
        when(entityManager.createQuery(anyString(), eq(RunningRoomSessionJpaEntity.class))).thenReturn(sessionQuery);
        when(sessionQuery.setParameter(anyString(), any())).thenReturn(sessionQuery);
        when(sessionQuery.getResultList()).thenReturn(List.of(
                RunningRoomSessionJpaEntity.create(room, host, 1L, 0, true)));
    }

    private void givenInvitations(List<RunningRoomInvitationJpaEntity> rows) {
        when(entityManager.createQuery(anyString(), eq(RunningRoomInvitationJpaEntity.class)))
                .thenReturn(invitationQuery);
        when(invitationQuery.setParameter(anyString(), any())).thenReturn(invitationQuery);
        when(invitationQuery.getResultList()).thenReturn(rows);
    }

    // 도메인 방 — update에 넘길 값이다
    private RunningRoom domainRoom(List<InvitationDraft> invitations) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.INVITE)
                .status(RunningRoomStatus.MATCHING)
                .startAt(START)
                .targetDistance(7_000)
                .avgPace(330)
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(new UserId(host), new RunningPlayerId(1L), 0, true)))
                .invitations(invitations)
                .build();
    }

    @Nested
    @DisplayName("복원 테스트")
    class RestoreTest {

        @Test
        @DisplayName("초대 방을 잠가 읽으면 보낸 사람별 초대 행까지 함께 복원한다")
        void restoresInvitationsOfInviteRoom() {
            // given
            RunningRoomJpaEntity room = roomEntity(RunningRoomType.INVITE);
            givenLockedRoom(room);
            givenSessions(room);
            givenInvitations(List.of(
                    RunningRoomInvitationJpaEntity.create(room, host, invitee, RoomInvitationStatus.PENDING, 2),
                    RunningRoomInvitationJpaEntity.create(
                            room, otherInviter, invitee, RoomInvitationStatus.PENDING, 1)));

            // when
            RunningRoom restored = adapter.lockById(new RunningRoomId(ROOM_ID)).orElseThrow();

            // then -> 나가기(LEFT)·수락이 이 값을 보고 판정한다
            assertThat(restored.getInvitations()).hasSize(2);
            assertThat(restored.invitationStatusOf(new UserId(invitee)))
                    .contains(RoomInvitationStatus.PENDING);
            assertThat(restored.getInvitations())
                    .filteredOn(invitation -> invitation.isSentBy(new UserId(host)))
                    .singleElement()
                    .satisfies(invitation -> assertThat(invitation.getInviteCount().value()).isEqualTo(2));
        }

        @Test
        @DisplayName("매칭 방은 초대 조회를 내보내지 않는다")
        void skipsInvitationQueryForMatchRoom() {
            // given
            RunningRoomJpaEntity room = roomEntity(RunningRoomType.MATCH);
            givenLockedRoom(room);
            givenSessions(room);

            // when
            RunningRoom restored = adapter.lockById(new RunningRoomId(ROOM_ID)).orElseThrow();

            // then -> 매칭·솔로 방의 잠금 경로에 쿼리가 하나도 늘지 않는다
            assertThat(restored.getInvitations()).isEmpty();
            verify(entityManager, never()).createQuery(anyString(), eq(RunningRoomInvitationJpaEntity.class));
        }
    }

    @Nested
    @DisplayName("저장 테스트")
    class UpdateTest {

        @Test
        @DisplayName("처음 보낸 초대는 새 행으로 만들고 이미 있는 행은 상태·횟수만 맞춘다")
        void insertsNewAndUpdatesStored() {
            // given -> host의 행은 이미 있고 otherInviter의 행은 이번에 새로 생겼다
            RunningRoomJpaEntity room = roomEntity(RunningRoomType.INVITE);
            RunningRoomInvitationJpaEntity storedRow = RunningRoomInvitationJpaEntity.create(
                    room, host, invitee, RoomInvitationStatus.DECLINED, 2);
            when(entityManager.find(RunningRoomJpaEntity.class, ROOM_ID)).thenReturn(room);
            givenSessions(room);
            givenInvitations(List.of(storedRow));

            RunningRoom domain = domainRoom(List.of(new InvitationDraft(
                    new UserId(host), new UserId(invitee), RoomInvitationStatus.DECLINED, 2)));
            domain.invite(new UserId(host), new UserId(invitee), 5);           // 되살림 + 3회
            domain.invite(new UserId(host), new UserId(otherInviter), 5);      // 다른 받는 사람에게 처음

            // when
            adapter.update(domain);

            // then
            assertThat(storedRow.getStatus()).isEqualTo(RoomInvitationStatus.PENDING);
            assertThat(storedRow.getInviteCount()).isEqualTo(3);
            ArgumentCaptor<RunningRoomInvitationJpaEntity> created =
                    ArgumentCaptor.forClass(RunningRoomInvitationJpaEntity.class);
            verify(entityManager).persist(created.capture());
            assertThat(created.getValue().getInviterId()).isEqualTo(host);
            assertThat(created.getValue().getInviteeId()).isEqualTo(otherInviter);
            assertThat(created.getValue().getStatus()).isEqualTo(RoomInvitationStatus.PENDING);
            assertThat(created.getValue().getInviteCount()).isEqualTo(1);
            assertThat(created.getValue().getRoom()).isSameAs(room);
        }

        @Test
        @DisplayName("초대가 없는 방이면 초대 행을 읽지도 쓰지도 않는다")
        void skipsWhenNoInvitations() {
            // given
            RunningRoomJpaEntity room = roomEntity(RunningRoomType.INVITE);
            when(entityManager.find(RunningRoomJpaEntity.class, ROOM_ID)).thenReturn(room);
            givenSessions(room);

            // when
            adapter.update(domainRoom(List.of()));

            // then -> 행을 지우는 경로가 없어 초대 없이 복원된 방이 저장돼도 기존 행이 안전하다
            verify(entityManager, never()).createQuery(anyString(), eq(RunningRoomInvitationJpaEntity.class));
            verify(entityManager, never()).persist(any(RunningRoomInvitationJpaEntity.class));
        }

        @Test
        @DisplayName("저장된 행이 도메인에 없어도 지우지 않는다")
        void neverDeletesStoredRows() {
            // given
            RunningRoomJpaEntity room = roomEntity(RunningRoomType.INVITE);
            RunningRoomInvitationJpaEntity untouched = RunningRoomInvitationJpaEntity.create(
                    room, otherInviter, invitee, RoomInvitationStatus.PENDING, 1);
            when(entityManager.find(RunningRoomJpaEntity.class, ROOM_ID)).thenReturn(room);
            givenSessions(room);
            givenInvitations(List.of(untouched));
            RunningRoom domain = domainRoom(List.of());
            domain.invite(new UserId(host), new UserId(otherInviter), 5);

            // when
            adapter.update(domain);

            // then
            verify(entityManager, never()).remove(any());
            assertThat(untouched.getStatus()).isEqualTo(RoomInvitationStatus.PENDING);
        }
    }
}
