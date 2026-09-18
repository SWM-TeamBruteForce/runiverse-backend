package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.running.command.combo.RunningComboReader;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.RunningComboPeer;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.query.snapshot.GetRunningSnapshotHandler;
import com.runiverse.running_service.application.running.query.snapshot.GetRunningSnapshotQuery;
import com.runiverse.running_service.application.running.query.snapshot.GetRunningSnapshotResult;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

// RUNNING_STARTED가 나르는 진입·재연결 화면 복구용 스냅샷을 조립한다(api-spec 5-C).
// 이게 비면 재연결한 클라가 남의 진행·콤보를 복구할 경로가 없다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 스냅샷 조회 단위 테스트")
class GetRunningSnapshotHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final int TARGET_DISTANCE_METERS = 5_000;
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 7, 25, 19, 0);

    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID PEER = UuidCreator.getTimeOrderedEpoch();
    // 방을 이미 떠난 사람 — 세션이 끊겨 있어 스냅샷에서 빠져야 한다
    private static final UUID LEFT = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private LoadRunningRoomPort loadRunningRoomPort;

    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    @Mock
    private LoadRunningDistancePort loadRunningDistancePort;

    // 콤보 조립 규칙은 RunningComboEvaluatorTest가 본다 — 여기서는 실려 나가는지만 본다
    @Mock
    private RunningComboReader runningComboReader;

    @InjectMocks
    private GetRunningSnapshotHandler handler;

    @Test
    @DisplayName("방 정보와 참가자 전원의 진행을 담는다")
    void assemblesRoomAndPlayers() {
        // given
        givenRoom(connected(ME), connected(PEER));
        givenProfiles(profile(ME, "완두콩", "p/me.png"), profile(PEER, "강낭콩", null));
        givenDistance(ME, 1_520, 345);
        givenDistance(PEER, 1_480, 352);
        given(generateViewUrlPort.generate("p/me.png")).willReturn("https://cdn.test/me.png");
        given(runningComboReader.read(anyLong(), any())).willReturn(List.of());

        // when
        GetRunningSnapshotResult result = handler.handle(new GetRunningSnapshotQuery(ME, ROOM_ID));

        // then
        assertThat(result.runningRoomId()).isEqualTo(ROOM_ID);
        // 참가자가 RUNNING이 된 시각을 쓰면 같은 방에서 사람마다 경과 시간이 달라진다
        assertThat(result.startedAt()).isEqualTo(START_AT);
        assertThat(result.targetDistanceMeters()).isEqualTo(TARGET_DISTANCE_METERS);
        assertThat(result.players())
                .extracting(GetRunningSnapshotResult.Player::userId)
                .containsExactlyInAnyOrder(ME, PEER);

        GetRunningSnapshotResult.Player me = playerOf(result, ME);
        assertThat(me.nickname()).isEqualTo("완두콩");
        assertThat(me.profileImageUrl()).isEqualTo("https://cdn.test/me.png");
        assertThat(me.distanceMeters()).isEqualTo(1_520);
        assertThat(me.currentPaceSecondsPerKm()).isEqualTo(345);
        assertThat(me.paused()).isFalse();
    }

    @Test
    @DisplayName("본인도 참가자 목록에 담는다")
    void includesRequesterInPlayers() {
        // given -> 진행 통지는 본인을 빼지만 스냅샷은 다르다.
        // 앱 재설치로 로컬 트랙이 사라지면 본인 누적 거리를 복구할 경로가 이것뿐이다
        givenRoom(connected(ME), connected(PEER));
        givenProfiles(profile(ME, "완두콩", null), profile(PEER, "강낭콩", null));
        givenDistance(ME, 1_520, 345);
        givenDistance(PEER, 1_480, 352);
        given(runningComboReader.read(anyLong(), any())).willReturn(List.of());

        // when
        GetRunningSnapshotResult result = handler.handle(new GetRunningSnapshotQuery(ME, ROOM_ID));

        // then
        assertThat(playerOf(result, ME).distanceMeters()).isEqualTo(1_520);
    }

    @Test
    @DisplayName("이탈·완주로 세션이 끊긴 참가자는 담지 않는다")
    void excludesDisconnectedSessions() {
        // given -> 나가기와 완주는 is_connected를 내린다. 러닝 화면에 그들을 그릴 자리가 없다
        givenRoom(connected(ME), disconnected(LEFT));
        givenProfiles(profile(ME, "완두콩", null));
        givenDistance(ME, 1_520, 345);
        given(runningComboReader.read(anyLong(), any())).willReturn(List.of());

        // when
        GetRunningSnapshotResult result = handler.handle(new GetRunningSnapshotQuery(ME, ROOM_ID));

        // then -> 떠난 사람의 누적 거리는 읽지도 않는다
        assertThat(result.players())
                .extracting(GetRunningSnapshotResult.Player::userId)
                .containsExactly(ME);
    }

    @Test
    @DisplayName("탈퇴한 참가자는 닉네임을 대체하고 사진을 비운다")
    void masksDeletedUser() {
        // given -> 신청은 남고 사용자만 사라진다. users 행이 없으면 탈퇴다(api-spec §0)
        givenRoom(connected(ME), connected(PEER));
        givenProfiles(profile(ME, "완두콩", null));   // PEER의 프로필이 없다
        givenDistance(ME, 1_520, 345);
        givenDistance(PEER, 1_480, 352);
        given(runningComboReader.read(anyLong(), any())).willReturn(List.of());

        // when
        GetRunningSnapshotResult result = handler.handle(new GetRunningSnapshotQuery(ME, ROOM_ID));

        // then -> 진행은 그대로 남는다. 자리를 비우면 남은 사람 화면에서 순위가 어긋난다
        GetRunningSnapshotResult.Player peer = playerOf(result, PEER);
        assertThat(peer.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(peer.profileImageUrl()).isNull();
        assertThat(peer.distanceMeters()).isEqualTo(1_480);
    }

    @Test
    @DisplayName("사진이 없으면 URL도 만들지 않는다")
    void skipsViewUrlWithoutImageKey() {
        // given
        givenRoom(connected(ME));
        givenProfiles(profile(ME, "완두콩", null));
        givenDistance(ME, 1_520, 345);
        given(runningComboReader.read(anyLong(), any())).willReturn(List.of());

        // when
        GetRunningSnapshotResult result = handler.handle(new GetRunningSnapshotQuery(ME, ROOM_ID));

        // then -> 없는 키로 presigned URL을 만들면 열리지 않는 링크가 나간다
        assertThat(playerOf(result, ME).profileImageUrl()).isNull();
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("단말이 페이스를 못 잰 참가자는 null로 담는다")
    void keepsNullPace() {
        // given -> 좌표의 페이스는 nullable이고 그대로 저장된다
        givenRoom(connected(ME));
        givenProfiles(profile(ME, "완두콩", null));
        givenDistance(ME, 1_520, null);
        given(runningComboReader.read(anyLong(), any())).willReturn(List.of());

        // when
        GetRunningSnapshotResult result = handler.handle(new GetRunningSnapshotQuery(ME, ROOM_ID));

        // then -> 0으로 바꾸면 "못 쟀다"가 "멈춰 있다"로 둔갑한다
        assertThat(playerOf(result, ME).currentPaceSecondsPerKm()).isNull();
    }

    @Test
    @DisplayName("콤보는 요청한 사람 기준으로 읽어 담는다")
    void readsComboForRequester() {
        // given -> 콤보는 관계에 붙어 있어 누가 보느냐에 따라 목록도 부호도 달라진다
        givenRoom(connected(ME), connected(PEER));
        givenProfiles(profile(ME, "완두콩", null), profile(PEER, "강낭콩", null));
        givenDistance(ME, 1_520, 345);
        givenDistance(PEER, 1_480, 352);
        given(runningComboReader.read(ROOM_ID, new UserId(ME)))
                .willReturn(List.of(new RunningComboPeer(PEER, -8, 12, 30)));

        // when
        GetRunningSnapshotResult result = handler.handle(new GetRunningSnapshotQuery(ME, ROOM_ID));

        // then
        assertThat(result.comboPeers()).hasSize(1);
        assertThat(result.comboPeers().get(0).userId()).isEqualTo(PEER);
        assertThat(result.comboPeers().get(0).comboCount()).isEqualTo(12);
        assertThat(result.comboPeers().get(0).gapMeters()).isEqualTo(-8);
    }

    @Test
    @DisplayName("목표 거리가 없는 솔로 방은 null로 담는다")
    void keepsNullTargetDistanceForSoloRoom() {
        // given
        given(loadRunningRoomPort.loadById(any()))
                .willReturn(Optional.of(room(null, connected(ME))));
        givenProfiles(profile(ME, "완두콩", null));
        givenDistance(ME, 1_520, 345);
        given(runningComboReader.read(anyLong(), any())).willReturn(List.of());

        // when
        GetRunningSnapshotResult result = handler.handle(new GetRunningSnapshotQuery(ME, ROOM_ID));

        // then
        assertThat(result.targetDistanceMeters()).isNull();
    }

    @Test
    @DisplayName("방이 없으면 ROOM_NOT_FOUND로 튕겨낸다")
    void throwsWhenRoomMissing() {
        // given
        given(loadRunningRoomPort.loadById(any())).willReturn(Optional.empty());

        // when & then -> WS 핸들러가 이 코드를 그대로 ERROR로 돌려준다
        assertThatThrownBy(() -> handler.handle(new GetRunningSnapshotQuery(ME, ROOM_ID)))
                .isInstanceOf(RunningRoomNotFoundException.class);
    }

    @Test
    @DisplayName("누적 거리를 못 읽으면 0으로 위장하지 않고 그대로 던진다")
    void throwsWhenDistanceLoadFails() {
        // given -> 0을 실어 보내면 클라가 "거리 0"을 그린다.
        // RUNNING_START는 멱등이라 재시도시키는 편이 낫다
        givenRoom(connected(ME));
        givenProfiles(profile(ME, "완두콩", null));
        given(loadRunningDistancePort.loadDistance(anyLong(), any()))
                .willThrow(new RuntimeException("redis down"));

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRunningSnapshotQuery(ME, ROOM_ID)))
                .isInstanceOf(RuntimeException.class);
    }

    // ── 픽스처 ──

    private void givenRoom(SessionDraft... sessions) {
        given(loadRunningRoomPort.loadById(any()))
                .willReturn(Optional.of(room(TARGET_DISTANCE_METERS, sessions)));
    }

    private static RunningRoom room(Integer targetDistance, SessionDraft... sessions) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.STARTED)
                .startAt(START_AT)
                .avgPace(330)
                .targetDistance(targetDistance)
                .currentPlayerCount(sessions.length)
                .maxPlayerCount(4)
                .sessions(List.of(sessions))
                .build();
    }

    private static SessionDraft connected(UUID userId) {
        return session(userId, true);
    }

    private static SessionDraft disconnected(UUID userId) {
        return session(userId, false);
    }

    private static SessionDraft session(UUID userId, boolean connected) {
        // 신청 ID는 스냅샷이 쓰지 않는다 — 세션을 복원하는 데만 필요하다
        return new SessionDraft(
                new UserId(userId), new RunningPlayerId(userId.getMostSignificantBits() & 0xFFFF),
                0, connected);
    }

    // 탈퇴자는 users 행이 지워져 결과에서 빠진다 — 없는 프로필을 넣지 않는 것이 곧 탈퇴다
    private void givenProfiles(PlayerProfile... profiles) {
        given(loadPlayerProfilesPort.loadProfiles(any())).willReturn(
                Arrays.stream(profiles)
                        .collect(Collectors.toMap(PlayerProfile::userId, profile -> profile)));
    }

    private static PlayerProfile profile(UUID userId, String nickname, String imageKey) {
        return new PlayerProfile(userId, nickname, imageKey, null);
    }

    private void givenDistance(UUID userId, int meters, Integer pace) {
        given(loadRunningDistancePort.loadDistance(eq(ROOM_ID), eq(new UserId(userId))))
                .willReturn(new RunningDistance(meters, 180L, 35.17955, 129.07564, pace));
    }

    private static GetRunningSnapshotResult.Player playerOf(
            GetRunningSnapshotResult result, UUID userId) {
        return result.players().stream()
                .filter(player -> player.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("스냅샷에 없는 참가자: " + userId));
    }
}
