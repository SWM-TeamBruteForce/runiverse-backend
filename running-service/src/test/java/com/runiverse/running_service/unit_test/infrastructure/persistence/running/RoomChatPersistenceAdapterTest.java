package com.runiverse.running_service.unit_test.infrastructure.persistence.running;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.port.out.LockedRoomChat;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.RunningRoomChatHistory;
import com.runiverse.running_service.infrastructure.persistence.running.RoomChatPersistenceAdapter;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRoomChatHistoryJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRoomChatJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRoomJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 수정·삭제 경로의 어댑터 동작만 본다 — JPQL 자체(조건·잠금 SQL)는 실제 DB가 있는 E2E가 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("방 채팅 영속성 어댑터 수정·삭제 단위 테스트")
class RoomChatPersistenceAdapterTest {

    private static final long ROOM_ID = 125L;
    private static final long CHAT_ID = 3021L;
    private static final UUID SENDER = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 25, 18, 52, 10);

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<RunningRoomChatJpaEntity> chatQuery;

    @InjectMocks
    private RoomChatPersistenceAdapter adapter;

    // 식별자·보낸 시각은 DB가 채우므로 저장된 행을 흉내 내려면 직접 넣는다
    private static RunningRoomChatJpaEntity savedEntity(String content) {
        RunningRoomChatJpaEntity entity = RunningRoomChatJpaEntity.create(mock(RunningRoomJpaEntity.class), SENDER, content);
        ReflectionTestUtils.setField(entity, "runningRoomChatId", CHAT_ID);
        ReflectionTestUtils.setField(entity, "createdAt", CREATED_AT);
        return entity;
    }

    private void givenLockedRows(List<RunningRoomChatJpaEntity> rows) {
        when(entityManager.createQuery(anyString(), eq(RunningRoomChatJpaEntity.class))).thenReturn(chatQuery);
        when(chatQuery.setParameter("chatId", CHAT_ID)).thenReturn(chatQuery);
        when(chatQuery.setParameter("runningRoomId", ROOM_ID)).thenReturn(chatQuery);
        when(chatQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(chatQuery);
        when(chatQuery.getResultList()).thenReturn(rows);
    }

    @Nested
    @DisplayName("잠가 읽기 테스트")
    class LoadForUpdateTest {

        @Test
        @DisplayName("행을 쓰기 잠금으로 읽어 도메인 채팅과 보낸 시각으로 돌려준다")
        void locksAndRestores() {
            // given
            givenLockedRows(List.of(savedEntity("곧 출발해요")));

            // when
            Optional<LockedRoomChat> locked = adapter.loadForUpdate(ROOM_ID, CHAT_ID);

            // then -> 잠금이 빠지면 연달아 온 두 수정이 같은 직전 내용을 읽어 이력 하나가 빠진다
            verify(chatQuery).setLockMode(LockModeType.PESSIMISTIC_WRITE);
            assertThat(locked).isPresent();
            RunningRoomChat chat = locked.get().chat();
            assertThat(chat.getRunningRoomChatId()).get().extracting(id -> id.value()).isEqualTo(CHAT_ID);
            assertThat(chat.getRunningRoomId().value()).isEqualTo(ROOM_ID);
            assertThat(chat.isSentBy(new UserId(SENDER))).isTrue();
            assertThat(chat.getContent().value()).isEqualTo("곧 출발해요");
            assertThat(chat.isEdited()).isFalse();
            assertThat(chat.isDeleted()).isFalse();
            assertThat(locked.get().createdAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("지운 메시지도 지운 상태 그대로 돌려준다 — 거르는 건 유스케이스다")
        void returnsDeletedChat() {
            // given
            RunningRoomChatJpaEntity entity = savedEntity("곧 출발해요");
            entity.apply("곧 출발해요", null, CREATED_AT.plusMinutes(1));
            givenLockedRows(List.of(entity));

            // when
            Optional<LockedRoomChat> locked = adapter.loadForUpdate(ROOM_ID, CHAT_ID);

            // then
            assertThat(locked).get().extracting(row -> row.chat().isDeleted()).isEqualTo(true);
        }

        @Test
        @DisplayName("이 방에 그 메시지가 없으면 비어 있다")
        void emptyWhenAbsent() {
            // given -> 다른 방의 메시지도 방 조건에 걸려 여기로 온다
            givenLockedRows(List.of());

            // when & then
            assertThat(adapter.loadForUpdate(ROOM_ID, CHAT_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("변경 쓰기 테스트")
    class UpdateTest {

        @Test
        @DisplayName("고친 내용과 수정 시각을 잠가 읽은 엔티티에 옮긴다")
        void appliesEdit() {
            // given
            RunningRoomChatJpaEntity entity = savedEntity("곧 출발해요");
            when(entityManager.find(RunningRoomChatJpaEntity.class, CHAT_ID)).thenReturn(entity);
            RunningRoomChat chat = RunningRoomChat.restore(CHAT_ID, ROOM_ID, SENDER, "곧 출발해요", null, null);
            LocalDateTime editedAt = CREATED_AT.plusMinutes(2);
            chat.edit(new UserId(SENDER), "조금 늦어요", editedAt);

            // when
            adapter.update(chat);

            // then -> 영속 상태라 커밋 때 UPDATE가 나간다
            assertThat(entity.getContent()).isEqualTo("조금 늦어요");
            assertThat(entity.getEditedAt()).isEqualTo(editedAt);
            assertThat(entity.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("지운 시각을 옮기고 내용은 그대로 둔다 — 신고 대응 근거다")
        void appliesDelete() {
            // given
            RunningRoomChatJpaEntity entity = savedEntity("곧 출발해요");
            when(entityManager.find(RunningRoomChatJpaEntity.class, CHAT_ID)).thenReturn(entity);
            RunningRoomChat chat = RunningRoomChat.restore(CHAT_ID, ROOM_ID, SENDER, "곧 출발해요", null, null);
            LocalDateTime deletedAt = CREATED_AT.plusMinutes(3);
            chat.delete(new UserId(SENDER), deletedAt);

            // when
            adapter.update(chat);

            // then
            assertThat(entity.getDeletedAt()).isEqualTo(deletedAt);
            assertThat(entity.getContent()).isEqualTo("곧 출발해요");
            assertThat(entity.getEditedAt()).isNull();
        }
    }

    @Test
    @DisplayName("수정 이력은 메시지를 참조로 걸고 고치기 직전 내용을 저장한다")
    void persistsHistory() {
        // given
        RunningRoomChatJpaEntity reference = savedEntity("곧 출발해요");
        when(entityManager.getReference(RunningRoomChatJpaEntity.class, CHAT_ID)).thenReturn(reference);
        RunningRoomChat chat = RunningRoomChat.restore(CHAT_ID, ROOM_ID, SENDER, "곧 출발해요", null, null);
        RunningRoomChatHistory history = chat.edit(new UserId(SENDER), "조금 늦어요", CREATED_AT.plusMinutes(2));

        // when
        adapter.create(history);

        // then
        ArgumentCaptor<RunningRoomChatHistoryJpaEntity> saved = ArgumentCaptor.forClass(RunningRoomChatHistoryJpaEntity.class);
        verify(entityManager).persist(saved.capture());
        assertThat(saved.getValue().getChat()).isSameAs(reference);
        assertThat(saved.getValue().getContent()).isEqualTo("곧 출발해요");
    }
}
