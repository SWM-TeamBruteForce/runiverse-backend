package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.match.common.RoomChatAssembler;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 지운 메시지의 원문은 이 조립을 지나면 사라진다 — 목록·보내기·이후 수정·삭제가 모두 여기를 거친다
@ExtendWith(MockitoExtension.class)
@DisplayName("방 채팅 조립 단위 테스트")
class RoomChatAssemblerTest {

    private static final long ROOM_ID = 125L;
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final UUID YOUNGHEE = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 11, 18, 52, 10);

    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    private RoomChatAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new RoomChatAssembler(loadPlayerProfilesPort, generateViewUrlPort);
    }

    private static RoomChatRow row(long chatId, UUID sender, String content,
                                   LocalDateTime editedAt, LocalDateTime deletedAt) {
        return new RoomChatRow(chatId, ROOM_ID, sender, content, editedAt, deletedAt, SENT_AT);
    }

    @Test
    @DisplayName("살아 있는 메시지는 내용과 보낸 사람 프로필을 그대로 싣는다")
    void assemblesLiveChat() {
        // given
        given(loadPlayerProfilesPort.loadProfiles(List.of(CHULSU)))
                .willReturn(Map.of(CHULSU, new PlayerProfile(CHULSU, "철수", "profiles/c.png", null)));
        given(generateViewUrlPort.generate("profiles/c.png")).willReturn("https://cdn/profiles/c.png");

        // when
        RoomChat chat = assembler.assemble(row(3021L, CHULSU, "곧 출발해요", null, null));

        // then
        assertThat(chat).isEqualTo(new RoomChat(3021L, ROOM_ID,
                new RoomChat.Sender(CHULSU, "철수", "https://cdn/profiles/c.png", false),
                "곧 출발해요", false, null, SENT_AT));
    }

    @Test
    @DisplayName("지운 메시지는 자리만 남기고 내용을 null로 내린다")
    void hidesDeletedContent() {
        // given -> 행에는 원문이 남아 있다(신고 대응)
        given(loadPlayerProfilesPort.loadProfiles(List.of(CHULSU)))
                .willReturn(Map.of(CHULSU, new PlayerProfile(CHULSU, "철수", null, null)));
        LocalDateTime editedAt = SENT_AT.plusMinutes(1);

        // when
        RoomChat chat = assembler.assemble(row(3021L, CHULSU, "지우기 전 내용", editedAt, SENT_AT.plusMinutes(2)));

        // then -> 수정 시각은 지운 뒤에도 그대로다
        assertThat(chat.content()).isNull();
        assertThat(chat.isDeleted()).isTrue();
        assertThat(chat.editedAt()).isEqualTo(editedAt);
        assertThat(chat.chatId()).isEqualTo(3021L);
        assertThat(chat.sender().nickname()).isEqualTo("철수");
    }

    @Test
    @DisplayName("보낸 사람이 탈퇴했으면 탈퇴 유저 형식이고 메시지 내용은 그대로다")
    void deletedSenderKeepsContent() {
        // given -> 탈퇴하면 users 행이 지워져 프로필 조회에서 빠진다
        given(loadPlayerProfilesPort.loadProfiles(List.of(CHULSU))).willReturn(Map.of());

        // when
        RoomChat chat = assembler.assemble(row(3021L, CHULSU, "곧 출발해요", null, null));

        // then -> 보낸 사람 탈퇴와 메시지 삭제는 다른 축이다
        assertThat(chat.sender()).isEqualTo(new RoomChat.Sender(CHULSU, "탈퇴한 사용자", null, true));
        assertThat(chat.content()).isEqualTo("곧 출발해요");
        assertThat(chat.isDeleted()).isFalse();
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("한 페이지의 보낸 사람 프로필은 중복 없이 한 번에 읽고 입력 순서를 지킨다")
    void loadsProfilesOnceAndKeepsOrder() {
        // given -> 철수·영희·철수 순으로 최신순 정렬된 페이지
        given(loadPlayerProfilesPort.loadProfiles(List.of(CHULSU, YOUNGHEE))).willReturn(Map.of(
                CHULSU, new PlayerProfile(CHULSU, "철수", null, null),
                YOUNGHEE, new PlayerProfile(YOUNGHEE, "영희", null, null)));

        // when
        List<RoomChat> chats = assembler.assemble(List.of(
                row(3, CHULSU, "셋", null, null),
                row(2, YOUNGHEE, "둘", null, null),
                row(1, CHULSU, "하나", null, null)));

        // then
        assertThat(chats).extracting(RoomChat::chatId).containsExactly(3L, 2L, 1L);
        assertThat(chats).extracting(chat -> chat.sender().nickname()).containsExactly("철수", "영희", "철수");
        verify(loadPlayerProfilesPort, times(1)).loadProfiles(List.of(CHULSU, YOUNGHEE));
    }

    @Test
    @DisplayName("빈 페이지는 프로필을 읽지 않고 빈 목록이다")
    void emptyPage() {
        // when
        List<RoomChat> chats = assembler.assemble(List.<RoomChatRow>of());

        // then
        assertThat(chats).isEmpty();
        verifyNoInteractions(loadPlayerProfilesPort, generateViewUrlPort);
    }
}
