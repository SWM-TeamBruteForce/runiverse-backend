package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.finish.BoundaryPoint;
import com.runiverse.running_service.application.running.command.finish.PolylineEncoder;
import com.runiverse.running_service.application.running.command.finish.RunningFinishProperties;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningResultNotFoundException;
import com.runiverse.running_service.application.running.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultPlayersPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningSplitsPort;
import com.runiverse.running_service.application.running.port.out.PlayerProfile;
import com.runiverse.running_service.application.running.port.out.RunningResultPlayer;
import com.runiverse.running_service.application.running.port.out.RunningResultRecord;
import com.runiverse.running_service.application.running.port.out.RunningSplitRow;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsHandler;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsQuery;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsResult;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 구간별 결과 조회 단위 테스트")
public class GetRunningSplitResultsHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OTHER = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 30, 19, 0, 30);
    private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 8, 30, 19, 30, 30);
    private static final int SPLIT_DISTANCE = 10;
    private static final double PRECISION = 1e-5;

    // 운영 설정과 같은 값 — splitDistanceMeters가 구간 경계 계산의 기준이다
    private static final RunningFinishProperties PROPERTIES =
            new RunningFinishProperties(0.8, SPLIT_DISTANCE, 100, 60, 3.0);

    // 점 다섯 개짜리 경로. 인덱스로 잘린 구간을 눈으로 확인하려고 위도를 1씩 띄운다
    private static final List<BoundaryPoint> ROUTE = List.of(
            boundary(0, 35.0), boundary(10, 36.0), boundary(20, 37.0),
            boundary(30, 38.0), boundary(40, 39.0));

    @Mock
    private LoadRunningRoomPort loadRunningRoomPort;
    @Mock
    private LoadRunningResultPlayersPort loadRunningResultPlayersPort;
    @Mock
    private LoadRunningResultRecordPort loadRunningResultRecordPort;
    @Mock
    private LoadRunningSplitsPort loadRunningSplitsPort;
    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;
    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    private GetRunningSplitResultsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetRunningSplitResultsHandler(
                loadRunningRoomPort,
                loadRunningResultPlayersPort,
                loadRunningResultRecordPort,
                loadRunningSplitsPort,
                loadPlayerProfilesPort,
                generateViewUrlPort,
                PROPERTIES);
    }

    @Test
    @DisplayName("방이 없으면 404로 끊는다 -> 참가자 판정보다 먼저다")
    void 방이_없으면_404다() {
        // given
        when(loadRunningRoomPort.loadById(any())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME)))
                .isInstanceOf(RunningResultNotFoundException.class);
        verify(loadRunningSplitsPort, never()).loadSplits(any());
    }

    @Test
    @DisplayName("방 참가자가 아니면 403이다 -> 구간을 읽기 전에 막는다")
    void 참가자가_아니면_403이다() {
        // given
        givenRoomExists();
        when(loadRunningResultPlayersPort.loadPlayers(any()))
                .thenReturn(List.of(player(OTHER, RunningPlayerStatus.COMPLETED)));

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME)))
                .isInstanceOf(NotRoomPlayerException.class);
        verify(loadRunningSplitsPort, never()).loadSplits(any());
    }

    @Test
    @DisplayName("같은 splitNumber에 참가자가 함께 묶인다 -> 같은 거리 구간의 비교표가 된다")
    void 같은_구간에_참가자가_묶인다() {
        // given -> 둘 다 1·2번 구간을 뛰었다
        givenRoomAndPlayers();
        when(loadRunningSplitsPort.loadSplits(any())).thenReturn(List.of(
                split(ME, 1, 0, 2), split(OTHER, 1, 0, 1),
                split(ME, 2, 2, 4), split(OTHER, 2, 1, 3)));
        givenRecordAndProfiles();

        // when
        GetRunningSplitResultsResult result =
                handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME));

        // then -> 구간은 번호 오름차순, 각 구간에 두 사람이 들어간다
        assertThat(result.splits()).hasSize(2);
        assertThat(result.splits().get(0).splitNumber()).isEqualTo(1);
        assertThat(result.splits().get(1).splitNumber()).isEqualTo(2);
        assertThat(result.splits().get(0).players())
                .extracting(GetRunningSplitResultsResult.SplitPlayer::userId)
                .containsExactlyInAnyOrder(ME, OTHER);
    }

    @Test
    @DisplayName("구간 경계는 번호로 계산한다 -> DB에 없는 값이다")
    void 구간_경계를_번호로_계산한다() {
        // given
        givenRoomAndPlayers();
        when(loadRunningSplitsPort.loadSplits(any())).thenReturn(List.of(
                split(ME, 1, 0, 2), split(ME, 2, 2, 4)));
        givenRecordAndProfiles();

        // when
        List<GetRunningSplitResultsResult.Split> splits =
                handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME)).splits();

        // then -> 1번은 0~10m, 2번은 10~20m
        assertThat(splits.get(0).startDistanceMeters()).isZero();
        assertThat(splits.get(0).endDistanceMeters()).isEqualTo(SPLIT_DISTANCE);
        assertThat(splits.get(1).startDistanceMeters()).isEqualTo(SPLIT_DISTANCE);
        assertThat(splits.get(1).endDistanceMeters()).isEqualTo(SPLIT_DISTANCE * 2);
        assertThat(splits.get(0).distanceMeters()).isEqualTo(SPLIT_DISTANCE);
    }

    @Test
    @DisplayName("경로는 끝 인덱스를 포함해 자르고, 남의 인덱스로 자르지 않는다")
    void 본인_인덱스로_끝점까지_자른다() {
        // given -> 같은 구간인데 본인은 0~2, 상대는 0~1로 인덱스가 다르다
        givenRoomAndPlayers();
        when(loadRunningSplitsPort.loadSplits(any())).thenReturn(List.of(
                split(ME, 1, 0, 2), split(OTHER, 1, 0, 1)));
        givenRecordAndProfiles();

        // when
        GetRunningSplitResultsResult.Split split =
                handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME)).splits().get(0);

        // then -> route_end_index는 포함이라 점 셋이다. 상대 인덱스를 썼다면 둘이 된다
        assertThat(split.routes()).hasSize(3);
        assertThat(split.routes().get(0).latitude()).isCloseTo(35.0, within(PRECISION));
        assertThat(split.routes().get(2).latitude()).isCloseTo(37.0, within(PRECISION));
    }

    @Test
    @DisplayName("이어붙일 때 경계점이 겹친다 -> 앞 구간의 끝과 뒤 구간의 시작이 같은 점이다")
    void 경계점이_겹친다() {
        // given
        givenRoomAndPlayers();
        when(loadRunningSplitsPort.loadSplits(any())).thenReturn(List.of(
                split(ME, 1, 0, 2), split(ME, 2, 2, 4)));
        givenRecordAndProfiles();

        // when
        List<GetRunningSplitResultsResult.Split> splits =
                handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME)).splits();

        // then
        List<com.runiverse.running_service.application.running.port.out.RoutePoint> first =
                splits.get(0).routes();
        assertThat(first.get(first.size() - 1).latitude())
                .isCloseTo(splits.get(1).routes().get(0).latitude(), within(PRECISION));
    }

    @Test
    @DisplayName("본인이 도달하지 못한 구간은 경로가 없다 -> 남은 남대로 지표만 실린다")
    void 도달하지_못한_구간은_경로가_없다() {
        // given -> 상대만 3번 구간까지 갔다
        givenRoomAndPlayers();
        when(loadRunningSplitsPort.loadSplits(any())).thenReturn(List.of(
                split(ME, 1, 0, 2), split(OTHER, 1, 0, 2), split(OTHER, 2, 2, 4)));
        givenRecordAndProfiles();

        // when
        List<GetRunningSplitResultsResult.Split> splits =
                handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME)).splits();

        // then
        assertThat(splits.get(1).routes()).isNull();
        assertThat(splits.get(1).players())
                .extracting(GetRunningSplitResultsResult.SplitPlayer::userId)
                .containsExactly(OTHER);
    }

    @Test
    @DisplayName("기록이 없는 참가자는 최상위 players에서도 빠진다 -> 6-1과 다른 규칙이다")
    void 기록이_없으면_양쪽에서_뺀다() {
        // given -> 상대는 아직 뛰는 중이라 구간이 하나도 없다
        givenRoomAndPlayers();
        when(loadRunningSplitsPort.loadSplits(any())).thenReturn(List.of(split(ME, 1, 0, 2)));
        givenRecordAndProfiles();

        // when
        GetRunningSplitResultsResult result =
                handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME));

        // then
        assertThat(result.players())
                .extracting(GetRunningSplitResultsResult.Player::userId)
                .containsExactly(ME);
    }

    @Test
    @DisplayName("본인 기록이 없으면 최상위 값이 null이고 구간이 비어 있다")
    void 본인_기록이_없으면_비어_있다() {
        // given -> 본인은 아직 뛰는 중이고 상대만 끝냈다
        givenRoomAndPlayers();
        when(loadRunningSplitsPort.loadSplits(any())).thenReturn(List.of(split(OTHER, 1, 0, 2)));
        when(loadRunningResultRecordPort.loadRecord(any(), any())).thenReturn(Optional.empty());
        when(loadPlayerProfilesPort.loadProfiles(any()))
                .thenReturn(Map.of(OTHER, new PlayerProfile(OTHER, "러닝초보", null)));

        // when
        GetRunningSplitResultsResult result =
                handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME));

        // then -> 구간 행은 있지만 자를 폴리라인이 없다
        assertThat(result.totalDistanceMeters()).isNull();
        assertThat(result.totalElevationGainMeters()).isNull();
        assertThat(result.startedAt()).isNull();
        assertThat(result.finishedAt()).isNull();
        assertThat(result.splits().get(0).routes()).isNull();
    }

    @Test
    @DisplayName("구간 지표가 자리 그대로 옮겨진다 -> 값이 전부 int라 어긋나도 컴파일된다")
    void 구간_지표가_자리_그대로_옮겨진다() {
        // given
        givenRoomAndPlayers();
        when(loadRunningSplitsPort.loadSplits(any())).thenReturn(List.of(split(ME, 1, 0, 2)));
        givenRecordAndProfiles();

        // when
        GetRunningSplitResultsResult.SplitPlayer player =
                handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME))
                        .splits().get(0).players().get(0);

        // then -> split()이 넣은 값과 하나씩 대응한다
        assertThat(player.userId()).isEqualTo(ME);
        assertThat(player.durationSeconds()).isEqualTo(3);
        assertThat(player.averagePaceSecondsPerKm()).isEqualTo(345);
        assertThat(player.averageCadenceSpm()).isEqualTo(162);
        assertThat(player.caloriesKcal()).isEqualTo(1);
        assertThat(player.elevationChangeMeters()).isNull();
    }

    @Test
    @DisplayName("구간 거리 설정이 그대로 응답에 실린다")
    void 구간_거리_설정이_실린다() {
        // given
        givenRoomAndPlayers();
        when(loadRunningSplitsPort.loadSplits(any())).thenReturn(List.of(split(ME, 1, 0, 2)));
        givenRecordAndProfiles();

        // when & then
        assertThat(handler.handle(new GetRunningSplitResultsQuery(ROOM_ID, ME))
                .splitDistanceMeters()).isEqualTo(SPLIT_DISTANCE);
    }

    private void givenRoomExists() {
        when(loadRunningRoomPort.loadById(any())).thenReturn(Optional.of(mock(RunningRoom.class)));
    }

    private void givenRoomAndPlayers() {
        givenRoomExists();
        when(loadRunningResultPlayersPort.loadPlayers(any())).thenReturn(List.of(
                player(ME, RunningPlayerStatus.COMPLETED),
                player(OTHER, RunningPlayerStatus.COMPLETED)));
    }

    private void givenRecordAndProfiles() {
        when(loadRunningResultRecordPort.loadRecord(any(), any())).thenReturn(Optional.of(
                new RunningResultRecord(
                        PolylineEncoder.encode(ROUTE), STARTED_AT, FINISHED_AT, 5020, 42)));
        Map<UUID, PlayerProfile> profiles = new HashMap<>();
        profiles.put(ME, new PlayerProfile(ME, "동완러너", null));
        profiles.put(OTHER, new PlayerProfile(OTHER, "러닝초보", null));
        when(loadPlayerProfilesPort.loadProfiles(any())).thenReturn(profiles);
    }

    private RunningResultPlayer player(UUID userId, RunningPlayerStatus status) {
        return new RunningResultPlayer(userId, status, 5020, 1800, 352, 359, 165, 42);
    }

    // 지표를 전부 다른 값으로 둔다 — 같은 값이면 자리가 바뀌어도 통과한다
    private RunningSplitRow split(UUID userId, int splitNumber, int startIndex, int endIndex) {
        return new RunningSplitRow(userId, splitNumber, SPLIT_DISTANCE, 3, 345, 162, null, 1,
                startIndex, endIndex);
    }

    private static BoundaryPoint boundary(int distanceMeters, double latitude) {
        return new BoundaryPoint(distanceMeters, latitude, 127.0, STARTED_AT, 0);
    }
}
