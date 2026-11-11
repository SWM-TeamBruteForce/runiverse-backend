package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.match.command.roomchatsend.SendRoomChatCommand;
import com.runiverse.running_service.application.match.command.roomchatsend.SendRoomChatHandler;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.exception.RoomChatClosedException;
import com.runiverse.running_service.application.match.exception.RoomChatRoomNotFoundException;
import com.runiverse.running_service.application.match.port.out.CreateRoomChatPort;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.SavedRoomChat;
import com.runiverse.running_service.application.running.port.out.LoadUserStatusPort;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 판정 순서는 방 안인가(404) → 채팅이 열려 있나(409)다. 걸리면 저장도 발행도 하지 않는다
@ExtendWith(MockitoExtension.class)
@DisplayName("방 채팅 보내기 단위 테스트")
class SendRoomChatHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final long CHAT_ID = 3021L;
    private static final UUID SENDER = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 19, 0);
    private static final LocalDateTime SENT_AT = START.minusMinutes(8);

    @Mock
    private LoadUserStatusPort loadUserStatusPort;

    @Mock
    private CreateRoomChatPort createRoomChatPort;

    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SendRoomChatHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SendRoomChatHandler(loadUserStatusPort, createRoomChatPort,
                loadPlayerProfilesPort, generateViewUrlPort, eventPublisher);
    }

    private static UserStatusRow statusOf(long roomId, RunningRoomType type, RunningRoomStatus roomStatus) {
        return new UserStatusRow(roomId, type, roomStatus, START, 5_000);
    }

    private static SendRoomChatCommand command(String content) {
        return new SendRoomChatCommand(SENDER, ROOM_ID, content);
    }

    private void givenStatus(UserStatusRow status) {
        given(loadUserStatusPort.loadStatus(new UserId(SENDER))).willReturn(Optional.of(status));
    }

    private void givenSaved() {
        given(createRoomChatPort.create(any())).willReturn(new SavedRoomChat(CHAT_ID, SENT_AT));
    }

    private void givenProfile(String imageKey) {
        given(loadPlayerProfilesPort.loadProfiles(List.of(SENDER)))
                .willReturn(Map.of(SENDER, new PlayerProfile(SENDER, "철수", imageKey, null)));
    }

    @Nested
    @DisplayName("보내기 테스트")
    class SendTest {

        @ParameterizedTest
        @EnumSource(value = RunningRoomType.class, names = {"MATCH", "INVITE"})
        @DisplayName("모집 중인 매칭·초대 방이면 저장하고 저장 결과로 채운 채팅을 돌려준다")
        void savesAndReturnsChat(RunningRoomType type) {
            // given
            givenStatus(statusOf(ROOM_ID, type, RunningRoomStatus.MATCHING));
            givenSaved();
            givenProfile("profiles/a.png");
            given(generateViewUrlPort.generate("profiles/a.png")).willReturn("https://cdn/profiles/a.png");

            // when
            RoomChat chat = handler.handle(command("곧 출발해요"));

            // then -> ID와 보낸 시각은 저장이 만든다. 새 메시지라 고친 적도 지운 적도 없다
            assertThat(chat).isEqualTo(new RoomChat(CHAT_ID, ROOM_ID,
                    new RoomChat.Sender(SENDER, "철수", "https://cdn/profiles/a.png", false),
                    "곧 출발해요", false, null, SENT_AT));
        }

        @Test
        @DisplayName("확정 후 대기 중인 방에서도 보낸다")
        void sendsWhileMatched() {
            // given
            givenStatus(statusOf(ROOM_ID, RunningRoomType.MATCH, RunningRoomStatus.MATCHED));
            givenSaved();
            givenProfile(null);

            // when
            RoomChat chat = handler.handle(command("5분 남았어요"));

            // then -> 사진이 없으면 URL도 없다
            assertThat(chat.chatId()).isEqualTo(CHAT_ID);
            assertThat(chat.sender().profileImageUrl()).isNull();
            verifyNoInteractions(generateViewUrlPort);
        }

        @Test
        @DisplayName("요청한 방에 보낸 사람이 쓴 그대로 도메인 채팅을 만들어 저장한다")
        void savesDomainChatForRequestedRoom() {
            // given
            givenStatus(statusOf(ROOM_ID, RunningRoomType.MATCH, RunningRoomStatus.MATCHING));
            givenSaved();
            givenProfile(null);

            // when
            handler.handle(command("곧 출발해요"));

            // then -> 저장 전이라 ID가 없다
            ArgumentCaptor<RunningRoomChat> saved = ArgumentCaptor.forClass(RunningRoomChat.class);
            verify(createRoomChatPort).create(saved.capture());
            assertThat(saved.getValue().getRunningRoomId()).isEqualTo(new RunningRoomId(ROOM_ID));
            assertThat(saved.getValue().getUserId()).isEqualTo(new UserId(SENDER));
            assertThat(saved.getValue().getContent().value()).isEqualTo("곧 출발해요");
            assertThat(saved.getValue().getRunningRoomChatId()).isEmpty();
        }

        @Test
        @DisplayName("보낸 채팅을 ROOM_CHAT_SENT로 발행하고 보낸 사람은 받는 사람에서 뺀다")
        void publishesRoomChatSentExcludingSender() {
            // given
            givenStatus(statusOf(ROOM_ID, RunningRoomType.INVITE, RunningRoomStatus.MATCHING));
            givenSaved();
            givenProfile(null);

            // when
            RoomChat chat = handler.handle(command("곧 출발해요"));

            // then -> 이벤트 data는 응답과 같은 값이다. 커밋 후 발행은 디스패처가 맡는다
            ArgumentCaptor<MatchRoomChangedEvent> published = ArgumentCaptor.forClass(MatchRoomChangedEvent.class);
            verify(eventPublisher).publishEvent(published.capture());
            MatchStreamEvent event = published.getValue().event();
            assertThat(event.type()).isEqualTo(MatchEventType.ROOM_CHAT_SENT);
            assertThat(event.runningRoomId()).isEqualTo(ROOM_ID);
            assertThat(event.chat()).isEqualTo(chat);
            assertThat(event.excludedUserId()).isEqualTo(new UserId(SENDER));
        }

        @Test
        @DisplayName("보낸 사람이 탈퇴 처리와 겹쳐 프로필이 없으면 탈퇴 유저 형식으로 채운다")
        void deletedSenderUsesDeletedFormat() {
            // given -> 탈퇴하면 users 행이 지워져 프로필 조회에서 빠진다
            givenStatus(statusOf(ROOM_ID, RunningRoomType.MATCH, RunningRoomStatus.MATCHING));
            givenSaved();
            given(loadPlayerProfilesPort.loadProfiles(List.of(SENDER))).willReturn(Map.of());

            // when
            RoomChat chat = handler.handle(command("곧 출발해요"));

            // then
            assertThat(chat.sender()).isEqualTo(new RoomChat.Sender(SENDER, "탈퇴한 사용자", null, true));
        }
    }

    @Nested
    @DisplayName("방 밖 테스트")
    class NotInRoomTest {

        @Test
        @DisplayName("배정된 방이 없으면 404다")
        void noAssignedRoom() {
            // given -> 나갔거나 처음부터 신청하지 않았다
            given(loadUserStatusPort.loadStatus(new UserId(SENDER))).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> handler.handle(command("안녕하세요")))
                    .isInstanceOf(RoomChatRoomNotFoundException.class);
            verifyNoInteractions(createRoomChatPort, eventPublisher);
        }

        @Test
        @DisplayName("다른 방에 배정돼 있으면 404다")
        void assignedToAnotherRoom() {
            // given -> 남의 방이 있는지 없는지를 알려 주지 않는다
            givenStatus(statusOf(ROOM_ID + 1, RunningRoomType.MATCH, RunningRoomStatus.MATCHING));

            // when & then
            assertThatThrownBy(() -> handler.handle(command("안녕하세요")))
                    .isInstanceOf(RoomChatRoomNotFoundException.class);
            verifyNoInteractions(createRoomChatPort, eventPublisher);
        }

        @Test
        @DisplayName("솔로 방이면 404다")
        void soloRoom() {
            // given -> 솔로 방에는 채팅이 없다. 솔로는 시작 전이어도 상태와 무관하게 막힌다
            givenStatus(statusOf(ROOM_ID, RunningRoomType.SOLO, RunningRoomStatus.MATCHING));

            // when & then
            assertThatThrownBy(() -> handler.handle(command("안녕하세요")))
                    .isInstanceOf(RoomChatRoomNotFoundException.class);
            verifyNoInteractions(createRoomChatPort, eventPublisher);
        }

        @Test
        @DisplayName("다른 방에 배정된 사람은 그 방이 시작됐어도 409가 아니라 404다")
        void notInRoomComesBeforeClosed() {
            // given -> 방 밖의 사람에게는 방 상태를 알려 주지 않는다
            givenStatus(statusOf(ROOM_ID + 1, RunningRoomType.MATCH, RunningRoomStatus.STARTED));

            // when & then
            assertThatThrownBy(() -> handler.handle(command("안녕하세요")))
                    .isInstanceOf(RoomChatRoomNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("채팅 닫힘 테스트")
    class ClosedTest {

        @ParameterizedTest
        @EnumSource(value = RunningRoomStatus.class, names = {"STARTED", "FINISHED", "CANCELLED"})
        @DisplayName("러닝이 시작됐거나 방이 닫혔으면 배정이 남아 있어도 409다")
        void closedRoomIsConflict(RunningRoomStatus roomStatus) {
            // given -> 시작 시각이 지나 아직 RUNNING_START 전인 JOINED도 여기서 막힌다
            givenStatus(statusOf(ROOM_ID, RunningRoomType.MATCH, roomStatus));

            // when & then
            assertThatThrownBy(() -> handler.handle(command("안녕하세요")))
                    .isInstanceOf(RoomChatClosedException.class);
            verifyNoInteractions(createRoomChatPort, loadPlayerProfilesPort, eventPublisher);
        }
    }
}
