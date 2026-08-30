package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.finish.BoundaryPoint;
import com.runiverse.running_service.application.running.command.finish.PolylineEncoder;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningResultNotFoundException;
import com.runiverse.running_service.application.running.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultPlayersPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.PlayerProfile;
import com.runiverse.running_service.application.running.port.out.RunningResultPlayer;
import com.runiverse.running_service.application.running.port.out.RunningResultRecord;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsHandler;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsQuery;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsResult;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 결과 조회 단위 테스트")
public class GetRunningResultHandlerTest {

    private static final long ROOM_ID = 125L;
    // UserId VO가 UUIDv7만 받는다 — 명세 예시의 v4를 그대로 쓰면 생성자에서 막힌다
    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OTHER = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 30, 19, 0, 30);
    private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 8, 30, 19, 30, 30);
    private static final String IMAGE_KEY = "profile/me.jpg";
    private static final String IMAGE_URL = "https://cdn.example.com/profile/me.jpg";
    private static final double PRECISION = 1e-5;

    @Mock
    private LoadRunningRoomPort loadRunningRoomPort;
    @Mock
    private LoadRunningResultPlayersPort loadRunningResultPlayersPort;
    @Mock
    private LoadRunningResultRecordPort loadRunningResultRecordPort;
    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;
    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    private GetRunningResultsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetRunningResultsHandler(
                loadRunningRoomPort,
                loadRunningResultPlayersPort,
                loadRunningResultRecordPort,
                loadPlayerProfilesPort,
                generateViewUrlPort);
    }

    @Test
    @DisplayName("방이 없으면 404로 끊는다 -> 참가자 판정보다 먼저다")
    void 방이_없으면_404다() {
        // given
        when(loadRunningRoomPort.loadById(any())).thenReturn(Optional.empty());

        // when & then -> 없는 방에 403이 나가면 방의 존재 여부가 새어 나간다
        assertThatThrownBy(() -> handler.handle(new GetRunningResultsQuery(ROOM_ID, ME)))
                .isInstanceOf(RunningResultNotFoundException.class);
        verify(loadRunningResultPlayersPort, never()).loadPlayers(any());
    }

    @Test
    @DisplayName("방 참가자가 아니면 403이다")
    void 참가자가_아니면_403이다() {
        // given -> 남의 방을 조회한다
        givenRoomExists();
        when(loadRunningResultPlayersPort.loadPlayers(any()))
                .thenReturn(List.of(finished(OTHER, RunningPlayerStatus.COMPLETED)));

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRunningResultsQuery(ROOM_ID, ME)))
                .isInstanceOf(NotRoomPlayerException.class);
        // 권한이 없으면 본인 기록을 읽지 않는다
        verify(loadRunningResultRecordPort, never()).loadRecord(any(), any());
    }

    @Test
    @DisplayName("시작 전 이탈자는 참가자로 세지 않는다 -> 러닝을 뛴 적이 없다")
    void 시작_전_이탈자는_403이다() {
        // given
        givenRoomExists();
        when(loadRunningResultPlayersPort.loadPlayers(any())).thenReturn(List.of(
                withoutRecord(ME, RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY),
                finished(OTHER, RunningPlayerStatus.COMPLETED)));

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRunningResultsQuery(ROOM_ID, ME)))
                .isInstanceOf(NotRoomPlayerException.class);
    }

    @Test
    @DisplayName("최상위 시각과 경로는 본인 기록에서만 나온다")
    void 최상위_값은_본인_기록_기준이다() {
        // given -> 상대도 기록이 있지만 최상위 값은 본인 것이어야 한다
        givenRoomExists();
        when(loadRunningResultPlayersPort.loadPlayers(any())).thenReturn(List.of(
                finished(ME, RunningPlayerStatus.COMPLETED),
                finished(OTHER, RunningPlayerStatus.COMPLETED)));
        when(loadRunningResultRecordPort.loadRecord(any(), any()))
                .thenReturn(Optional.of(record()));
        givenProfiles(profile(ME, "동완러너", IMAGE_KEY), profile(OTHER, "러닝초보", null));
        when(generateViewUrlPort.generate(anyString())).thenReturn(IMAGE_URL);

        // when
        GetRunningResultsResult result = handler.handle(new GetRunningResultsQuery(ROOM_ID, ME));

        // then
        assertThat(result.runningRoomId()).isEqualTo(ROOM_ID);
        assertThat(result.startedAt()).isEqualTo(STARTED_AT);
        assertThat(result.finishedAt()).isEqualTo(FINISHED_AT);
        assertThat(result.routes()).hasSize(2);
        assertThat(result.routes().get(0).latitude()).isCloseTo(35.1795543, within(PRECISION));
        assertThat(result.routes().get(1).longitude()).isCloseTo(129.0757104, within(PRECISION));
    }

    @Test
    @DisplayName("본인 기록이 없으면 최상위 세 값이 전부 null이다 -> 아직 뛰는 중이다")
    void 본인_기록이_없으면_최상위가_null이다() {
        // given
        givenRoomExists();
        when(loadRunningResultPlayersPort.loadPlayers(any()))
                .thenReturn(List.of(withoutRecord(ME, RunningPlayerStatus.RUNNING)));
        when(loadRunningResultRecordPort.loadRecord(any(), any())).thenReturn(Optional.empty());
        givenProfiles(profile(ME, "동완러너", null));

        // when
        GetRunningResultsResult result = handler.handle(new GetRunningResultsQuery(ROOM_ID, ME));

        // then
        assertThat(result.startedAt()).isNull();
        assertThat(result.finishedAt()).isNull();
        assertThat(result.routes()).isNull();
    }

    @Test
    @DisplayName("기록이 없는 참가자는 지표가 전부 null이고 status는 RUNNING이다")
    void 기록이_없으면_지표가_null이다() {
        // given
        givenRoomExists();
        when(loadRunningResultPlayersPort.loadPlayers(any())).thenReturn(List.of(
                finished(ME, RunningPlayerStatus.COMPLETED),
                withoutRecord(OTHER, RunningPlayerStatus.RUNNING)));
        when(loadRunningResultRecordPort.loadRecord(any(), any()))
                .thenReturn(Optional.of(record()));
        givenProfiles(profile(ME, "동완러너", null), profile(OTHER, "러닝초보", null));

        // when
        GetRunningResultsResult.Player other = playerOf(
                handler.handle(new GetRunningResultsQuery(ROOM_ID, ME)), OTHER);

        // then
        assertThat(other.status()).isEqualTo("RUNNING");
        assertThat(other.isMe()).isFalse();
        assertThat(other.totalDistanceMeters()).isNull();
        assertThat(other.totalDurationSeconds()).isNull();
        assertThat(other.totalCaloriesKcal()).isNull();
        assertThat(other.averagePaceSecondsPerKm()).isNull();
        assertThat(other.averageCadenceSpm()).isNull();
        assertThat(other.totalElevationGainMeters()).isNull();
    }

    @Test
    @DisplayName("중도 이탈도 COMPLETED로 나간다 -> 페널티 여부를 남의 화면에 뿌리지 않는다")
    void 이탈자도_COMPLETED다() {
        // given
        givenRoomExists();
        when(loadRunningResultPlayersPort.loadPlayers(any())).thenReturn(List.of(
                finished(ME, RunningPlayerStatus.COMPLETED),
                finished(OTHER, RunningPlayerStatus.RUNNING_LEFT_PENALTY)));
        when(loadRunningResultRecordPort.loadRecord(any(), any()))
                .thenReturn(Optional.of(record()));
        givenProfiles(profile(ME, "동완러너", null), profile(OTHER, "러닝초보", null));

        // when
        GetRunningResultsResult.Player other = playerOf(
                handler.handle(new GetRunningResultsQuery(ROOM_ID, ME)), OTHER);

        // then
        assertThat(other.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("탈퇴한 참가자는 공통 탈퇴 유저 형식으로 나간다")
    void 탈퇴자는_공통_형식이다() {
        // given -> users 행이 지워져 프로필 조회에서 빠진다
        givenRoomExists();
        when(loadRunningResultPlayersPort.loadPlayers(any())).thenReturn(List.of(
                finished(ME, RunningPlayerStatus.COMPLETED),
                finished(OTHER, RunningPlayerStatus.COMPLETED)));
        when(loadRunningResultRecordPort.loadRecord(any(), any()))
                .thenReturn(Optional.of(record()));
        givenProfiles(profile(ME, "동완러너", null));

        // when
        GetRunningResultsResult.Player other = playerOf(
                handler.handle(new GetRunningResultsQuery(ROOM_ID, ME)), OTHER);

        // then -> userId는 유지하고 기록 지표도 그대로 남는다(api-spec §0)
        assertThat(other.userId()).isEqualTo(OTHER);
        assertThat(other.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(other.profileImageUrl()).isNull();
        assertThat(other.isDeleted()).isTrue();
        assertThat(other.totalDistanceMeters()).isEqualTo(5020);
    }

    @Test
    @DisplayName("프로필 사진이 없으면 URL도 만들지 않는다")
    void 사진이_없으면_URL도_없다() {
        // given
        givenRoomExists();
        when(loadRunningResultPlayersPort.loadPlayers(any()))
                .thenReturn(List.of(finished(ME, RunningPlayerStatus.COMPLETED)));
        when(loadRunningResultRecordPort.loadRecord(any(), any()))
                .thenReturn(Optional.of(record()));
        givenProfiles(profile(ME, "동완러너", null));

        // when
        GetRunningResultsResult.Player me = playerOf(
                handler.handle(new GetRunningResultsQuery(ROOM_ID, ME)), ME);

        // then
        assertThat(me.profileImageUrl()).isNull();
        assertThat(me.isMe()).isTrue();
        assertThat(me.isDeleted()).isFalse();
        verify(generateViewUrlPort, never()).generate(anyString());
    }

    private void givenRoomExists() {
        when(loadRunningRoomPort.loadById(any())).thenReturn(Optional.of(mock(RunningRoom.class)));
    }

    private void givenProfiles(PlayerProfile... profiles) {
        Map<UUID, PlayerProfile> byUserId = new java.util.HashMap<>();
        for (PlayerProfile profile : profiles) {
            byUserId.put(profile.userId(), profile);
        }
        when(loadPlayerProfilesPort.loadProfiles(any())).thenReturn(byUserId);
    }

    private PlayerProfile profile(UUID userId, String nickname, String imageKey) {
        return new PlayerProfile(userId, nickname, imageKey);
    }

    private RunningResultPlayer finished(UUID userId, RunningPlayerStatus status) {
        return new RunningResultPlayer(userId, status, 5020, 1800, 352, 359, 165, 42);
    }

    private RunningResultPlayer withoutRecord(UUID userId, RunningPlayerStatus status) {
        return new RunningResultPlayer(userId, status, null, null, null, null, null, null);
    }

    // 6-2가 쓰는 두 값까지 채운다 — 6-1은 앞의 셋만 읽는다
    private RunningResultRecord record() {
        return new RunningResultRecord(polyline(), STARTED_AT, FINISHED_AT, 5020, 42);
    }

    private String polyline() {
        return PolylineEncoder.encode(List.of(
                new BoundaryPoint(0, 35.1795543, 129.0756416, STARTED_AT, 0),
                new BoundaryPoint(10, 35.1796012, 129.0757104, STARTED_AT, 1)));
    }

    private GetRunningResultsResult.Player playerOf(GetRunningResultsResult result, UUID userId) {
        return result.players().stream()
                .filter(player -> player.userId().equals(userId))
                .findFirst()
                .orElseThrow();
    }
}
