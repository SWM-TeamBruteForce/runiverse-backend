package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.RecruitmentClosePolicy;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.LoadActiveApplicationPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchPlayersPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomDetailPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchPlayer;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.InvitationDraft;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭방 정보 조립 단위 테스트")
class RoomInfoAssemblerTest {

    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OTHER = UuidCreator.getTimeOrderedEpoch();
    private static final Long ROOM_ID = 125L;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(15);
    private static final Duration INVITE_CLOSE_OFFSET = Duration.ofMinutes(1);
    private static final Duration READY_OFFSET = Duration.ofSeconds(10);
    private static final Duration FORCE_FINISH_OFFSET = Duration.ofHours(6);
    private static final int PACE_TIE_TOLERANCE = 10;
    // 이 테스트가 다루는 흐름은 아니지만 프로퍼티가 요구한다
    private static final Duration COOLDOWN = Duration.ofMinutes(20);
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 7, 25, 19, 0);
    private static final int TEAM_PACE = 375;
    private static final int TARGET_DISTANCE = 5000;
    private static final String IMAGE_KEY = "profile/me.jpg";
    private static final String IMAGE_URL = "https://cdn.runiverse.test/profile/me.jpg";

    @Mock
    private LoadMatchPlayersPort loadMatchPlayersPort;

    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    // assembleFor(UserId) 경로가 쓰는 셋 — 이 테스트는 assemble(RunningRoom)만 다룬다
    @Mock
    private LoadActiveApplicationPort loadActiveApplicationPort;

    @Mock
    private LoadMatchRoomPort loadMatchRoomPort;

    @Mock
    private LoadMatchRoomDetailPort loadMatchRoomDetailPort;

    private RoomInfoAssembler roomInfoAssembler;

    @BeforeEach
    void setUp() {
        // 페이스 동점 임계는 후보 배정(11번)에서만 쓴다 — 조립에는 마감 오프셋만 걸린다
        MatchProperties matchProperties = new MatchProperties(CLOSE_OFFSET, READY_OFFSET, FORCE_FINISH_OFFSET,
                PACE_TIE_TOLERANCE, COOLDOWN,
                LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
        RecruitmentClosePolicy recruitmentClosePolicy = new RecruitmentClosePolicy(matchProperties,
                new InviteStartPolicy(new InviteProperties(
                        Duration.ofMinutes(10), INVITE_CLOSE_OFFSET, 1_000, 42_195, 5), matchProperties));
        roomInfoAssembler = new RoomInfoAssembler(
                loadMatchPlayersPort, loadPlayerProfilesPort, generateViewUrlPort,
                recruitmentClosePolicy,
                loadActiveApplicationPort, loadMatchRoomPort, loadMatchRoomDetailPort);
    }

    @Test
    @DisplayName("모집 마감은 저장값이 아니라 시작 시각에서 오프셋을 뺀 값이다")
    void calculatesCloseAtFromStartAt() {
        // given -> 방에는 마감 시각 컬럼이 없다. running_rooms.close_at은 방이 닫힌 시각이라 별개다
        givenPlayers(List.of(), Map.of());

        // when
        RoomInfo roomInfo = roomInfoAssembler.assemble(room());

        // then
        assertThat(roomInfo.closeAt()).isEqualTo(LocalDateTime.of(2026, 7, 25, 18, 45));
        assertThat(roomInfo.scheduledStartAt()).isEqualTo(START_AT);
        assertThat(roomInfo.runningRoomId()).isEqualTo(ROOM_ID);
        assertThat(roomInfo.status()).isEqualTo(RunningRoomStatus.MATCHING);
        assertThat(roomInfo.targetDistanceMeters()).isEqualTo(TARGET_DISTANCE);
        assertThat(roomInfo.teamAveragePaceSecondsPerKm()).isEqualTo(TEAM_PACE);
    }

    @Test
    @DisplayName("초대 방의 모집 마감은 초대 오프셋으로 계산한다")
    void calculatesInviteCloseAtWithInviteOffset() {
        // given -> 매칭 오프셋(15분)을 쓰면 시작 15분 전부터 초대 방이 마감으로 보인다
        givenPlayers(List.of(), Map.of());

        // when
        RoomInfo roomInfo = roomInfoAssembler.assemble(room(RunningRoomType.INVITE));

        // then
        assertThat(roomInfo.closeAt()).isEqualTo(START_AT.minus(INVITE_CLOSE_OFFSET));
    }

    @Test
    @DisplayName("참가자에 프로필과 소개글을 붙여 내려준다")
    void joinsProfileIntoPlayers() {
        // given
        givenPlayers(
                List.of(new MatchPlayer(ME, 360)),
                Map.of(ME, new PlayerProfile(ME, "동완러너", IMAGE_KEY, "즐겁게 같이 달려요!")));
        given(generateViewUrlPort.generate(IMAGE_KEY)).willReturn(IMAGE_URL);

        // when
        RoomInfo.RoomPlayer player = roomInfoAssembler.assemble(room()).players().getFirst();

        // then
        assertThat(player.userId()).isEqualTo(ME);
        assertThat(player.nickname()).isEqualTo("동완러너");
        assertThat(player.profileImageUrl()).isEqualTo(IMAGE_URL);
        assertThat(player.introduction()).isEqualTo("즐겁게 같이 달려요!");
        assertThat(player.averagePaceSecondsPerKm()).isEqualTo(360);
        assertThat(player.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("사진이 없으면 URL을 발급하지 않는다")
    void skipsViewUrlWhenNoProfileImage() {
        // given
        givenPlayers(
                List.of(new MatchPlayer(ME, 360)),
                Map.of(ME, new PlayerProfile(ME, "동완러너", null, null)));

        // when
        RoomInfo.RoomPlayer player = roomInfoAssembler.assemble(room()).players().getFirst();

        // then -> 없는 키로 서명을 만들면 열리지 않는 URL이 내려간다
        assertThat(player.profileImageUrl()).isNull();
        assertThat(player.introduction()).isNull();
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("탈퇴한 참가자는 자리를 유지한 채 익명으로 내려간다")
    void marksWithdrawnPlayerAsDeleted() {
        // given -> 탈퇴하면 users 행이 지워져 프로필 조회 결과에서 빠진다
        givenPlayers(
                List.of(new MatchPlayer(ME, 360), new MatchPlayer(OTHER, 390)),
                Map.of(ME, new PlayerProfile(ME, "동완러너", null, null)));

        // when
        List<RoomInfo.RoomPlayer> players = roomInfoAssembler.assemble(room()).players();

        // then -> 목록에서 빼지 않는다. 빼면 인원수가 방과 어긋난다
        assertThat(players).hasSize(2);
        RoomInfo.RoomPlayer withdrawn = players.get(1);
        assertThat(withdrawn.userId()).isEqualTo(OTHER);
        assertThat(withdrawn.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(withdrawn.profileImageUrl()).isNull();
        assertThat(withdrawn.introduction()).isNull();
        assertThat(withdrawn.averagePaceSecondsPerKm()).isEqualTo(390);
        assertThat(withdrawn.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("방 종류를 싣고 매칭 방의 초대 현황은 비어 있다")
    void carriesTypeAndEmptyInvitationsForMatchRoom() {
        // given
        givenPlayers(List.of(), Map.of());

        // when
        RoomInfo roomInfo = roomInfoAssembler.assemble(room());

        // then -> 클라가 방 화면을 가르고, 매칭 방은 invitations가 항상 빈 배열이다
        assertThat(roomInfo.type()).isEqualTo(RunningRoomType.MATCH);
        assertThat(roomInfo.invitations()).isEmpty();
    }

    @Test
    @DisplayName("초대 방은 응답 전인 초대만 받는 사람당 한 건씩 프로필을 붙여 싣는다")
    void showsInviteesOncePerInviteeWithProfile() {
        // given -> 대기 중인 사람은 보낸 사람이 둘이라 행이 둘이다
        UUID pending = UuidCreator.getTimeOrderedEpoch();
        UUID declined = UuidCreator.getTimeOrderedEpoch();
        UUID left = UuidCreator.getTimeOrderedEpoch();
        UUID accepted = UuidCreator.getTimeOrderedEpoch();
        RunningRoom room = inviteRoom(RunningRoomStatus.MATCHING, List.of(
                new InvitationDraft(new UserId(ME), new UserId(pending), RoomInvitationStatus.PENDING, 1),
                new InvitationDraft(new UserId(OTHER), new UserId(pending), RoomInvitationStatus.PENDING, 2),
                new InvitationDraft(new UserId(ME), new UserId(declined), RoomInvitationStatus.DECLINED, 1),
                new InvitationDraft(new UserId(ME), new UserId(left), RoomInvitationStatus.LEFT, 1),
                new InvitationDraft(new UserId(ME), new UserId(accepted), RoomInvitationStatus.ACCEPTED, 1)));
        given(loadMatchPlayersPort.loadPlayers(new RunningRoomId(ROOM_ID)))
                .willReturn(List.of(new MatchPlayer(ME, 360)));
        // 참가자와 초대 대상을 한 번에 읽는다
        given(loadPlayerProfilesPort.loadProfiles(List.of(ME, pending))).willReturn(Map.of(
                ME, new PlayerProfile(ME, "동완러너", null, null),
                pending, new PlayerProfile(pending, "영희", IMAGE_KEY, null)));
        given(generateViewUrlPort.generate(IMAGE_KEY)).willReturn(IMAGE_URL);

        // when
        RoomInfo roomInfo = roomInfoAssembler.assemble(room);

        // then -> 수락은 players로 옮겨 가고 거절·나감은 현황에서 빠진다. 빠진 사람의 프로필은 읽지 않는다
        assertThat(roomInfo.type()).isEqualTo(RunningRoomType.INVITE);
        assertThat(roomInfo.invitations()).containsExactly(
                new RoomInfo.RoomInvitee(pending, "영희", IMAGE_URL, RoomInvitationStatus.PENDING, false));
    }

    @Test
    @DisplayName("만료된 초대는 현황에 싣지 않는다")
    void hidesExpiredInvitations() {
        // given
        UUID expired = UuidCreator.getTimeOrderedEpoch();
        RunningRoom room = inviteRoom(RunningRoomStatus.MATCHING, List.of(
                new InvitationDraft(new UserId(ME), new UserId(expired), RoomInvitationStatus.EXPIRED, 1)));
        givenPlayers(List.of(), Map.of());

        // when & then
        assertThat(roomInfoAssembler.assemble(room).invitations()).isEmpty();
    }

    @Test
    @DisplayName("모집이 끝난 초대 방은 남은 초대가 있어도 현황을 비운다")
    void emptiesInvitationsAfterRecruitment() {
        // given -> 마감 갱신이 빈 현황으로 나가 "더는 초대받지 않는다"를 알린다
        UUID pending = UuidCreator.getTimeOrderedEpoch();
        RunningRoom room = inviteRoom(RunningRoomStatus.MATCHED, List.of(
                new InvitationDraft(new UserId(ME), new UserId(pending), RoomInvitationStatus.PENDING, 1)));
        givenPlayers(List.of(), Map.of());

        // when
        RoomInfo roomInfo = roomInfoAssembler.assemble(room);

        // then -> 초대 대상의 프로필도 읽지 않는다(givenPlayers가 참가자 ID만으로 스텁했다)
        assertThat(roomInfo.status()).isEqualTo(RunningRoomStatus.MATCHED);
        assertThat(roomInfo.invitations()).isEmpty();
    }

    @Test
    @DisplayName("초대 대상의 프로필이 없으면 탈퇴로 표시한다")
    void marksMissingInviteeAsDeleted() {
        // given -> 탈퇴하면 FK CASCADE로 행이 지워지지만, 형식은 참가자와 같게 둔다
        UUID pending = UuidCreator.getTimeOrderedEpoch();
        RunningRoom room = inviteRoom(RunningRoomStatus.MATCHING, List.of(
                new InvitationDraft(new UserId(ME), new UserId(pending), RoomInvitationStatus.PENDING, 1)));
        given(loadMatchPlayersPort.loadPlayers(new RunningRoomId(ROOM_ID))).willReturn(List.of());
        given(loadPlayerProfilesPort.loadProfiles(List.of(pending))).willReturn(Map.of());

        // when
        RoomInfo.RoomInvitee invitee = roomInfoAssembler.assemble(room).invitations().getFirst();

        // then
        assertThat(invitee.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(invitee.profileImageUrl()).isNull();
        assertThat(invitee.isDeleted()).isTrue();
    }

    private static RunningRoom inviteRoom(RunningRoomStatus status, List<InvitationDraft> invitations) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.INVITE)
                .status(status)
                .startAt(START_AT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(TEAM_PACE)
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        new UserId(ME), new RunningPlayerId(1L), 0, true)))
                .invitations(invitations)
                .build();
    }

    private void givenPlayers(List<MatchPlayer> players, Map<UUID, PlayerProfile> profiles) {
        given(loadMatchPlayersPort.loadPlayers(new RunningRoomId(ROOM_ID))).willReturn(players);
        given(loadPlayerProfilesPort.loadProfiles(players.stream().map(MatchPlayer::userId).toList()))
                .willReturn(profiles);
    }

    // 모집 중인 방은 아직 닫히지 않았으므로 closeAt은 비어 있어야 복원된다
    private static RunningRoom room() {
        return room(RunningRoomType.MATCH);
    }

    private static RunningRoom room(RunningRoomType type) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(type)
                .status(RunningRoomStatus.MATCHING)
                .startAt(START_AT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(TEAM_PACE)
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        new UserId(ME), new RunningPlayerId(1L), 0, true)))
                .build();
    }
}
