package com.runiverse.running_service.application.running.command.finish;

import com.runiverse.running_service.application.common.port.out.UpdateUserAvgPacePort;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningNotStartableException;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.out.CreateRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.DeleteRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.GpsTrackUpload;
import com.runiverse.running_service.application.running.port.out.LoadRecentRunningPacesPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadUserWeightPort;
import com.runiverse.running_service.application.running.port.out.LoadWeatherPort;
import com.runiverse.running_service.application.running.port.out.RecentRunningPace;
import com.runiverse.running_service.application.running.port.out.RunningTrack;
import com.runiverse.running_service.application.running.port.out.SaveGpsTrackPort;
import com.runiverse.running_service.application.running.port.out.StartMatchCooldownPort;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.Weather;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.record.SplitDraft;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.user.vo.AvgPace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class FinishRunningHandler implements FinishRunningUsecase {

    private final LoadRunningRoomPort loadRunningRoomPort;
    private final LoadRoomPlayerPort loadRoomPlayerPort;
    private final LoadRunningTrackPort loadRunningTrackPort;
    private final LoadUserWeightPort loadUserWeightPort;
    private final LoadWeatherPort loadWeatherPort;
    private final SaveGpsTrackPort saveGpsTrackPort;
    private final CreateRunningRecordPort createRunningRecordPort;
    private final UpdateRunningPlayerPort updateRunningPlayerPort;
    private final DeleteRunningTrackPort deleteRunningTrackPort;
    private final ExistsRunningPlayerPort existsRunningPlayerPort;
    private final UpdateRunningRoomPort updateRunningRoomPort;
    private final StartMatchCooldownPort startMatchCooldownPort;
    private final ExistsRunningRecordPort existsRunningRecordPort;
    private final LoadRecentRunningPacesPort loadRecentRunningPacesPort;
    private final UpdateUserAvgPacePort updateUserAvgPacePort;
    private final RunningFinishProperties properties;
    // 1인 확정 방에서 혼자 뛰다 그만두는 것은 제재하지 않는다 — 곤란해지는 상대가 없다.
    // 시작 전 이탈(CancelMatchHandler)의 면제와 같은 기준이다.
    // 러닝 시작 후에는 인원이 줄지 않으므로(erd) 이 값이 곧 확정 시점 인원이다
    private static final int PENALTY_MIN_PLAYER_COUNT = 2;
    // 이만큼 쌓여야 실측 평균으로 갈아탄다. 그전에는 온보딩 입력값을 쓴다.
    // 늘리면 자기 신고값이 오래 남고, 줄이면 한 번의 회복 러닝에 매칭 페이스가 흔들린다
    private static final int AVG_PACE_SAMPLE_SIZE = 5;

    @Override
    public void handle(FinishRunningCommand command) {
        RunningRoomId roomId = new RunningRoomId(command.runningRoomId());
        UserId userId = new UserId(command.userId());
        // 1. 활성 신청이 아니라 이 방의 참가자를 찾는다 — 이미 끝난 참가자도 찾아야 멱등이 된다
        RunningPlayer player = loadRoomPlayerPort.load(roomId, userId)
                .orElseThrow(NotRoomPlayerException::new);
        // 이미 확정된 참가자 - 기록을 덮어쓰지 않고 트랙만 정리한 뒤 ack를 다시 보낸다
        if (!player.isActive()) {
            deleteTrackAfterCommit(command.runningRoomId(), userId);
            return;
        }
        // RUNNING_START를 거치지 않은 참가자는 확정할 러닝이 없다.
        // 도메인 예외가 아니라 여기서 거른다 — 도메인 예외는 500으로 마스킹된다
        if (player.getStatus() != RunningPlayerStatus.RUNNING) {
            throw new RunningNotStartableException();
        }
        // 2. 목표 거리는 참가자가 아니라 방이 정한다 —
        //    참가자별 목표로 나누면 같은 방에서 splitNumber N이 서로 다른 구간을 가리킨다
        RunningRoom room = loadRunningRoomPort.loadById(roomId)
                .orElseThrow(RunningRoomNotFoundException::new);
        // 온보딩에서 몸무게는 필수다 — 비어 있으면 러닝을 시작할 수 없었어야 할 사용자다
        BigDecimal weightKg = loadUserWeightPort.loadWeightKg(userId)
                .orElseThrow(OnboardingNotCompletedException::new);

        // 3. 마지막 수신 좌표까지로 지표를 낸다.
        //    산출할 수 없는 트랙이면 실제 거리를 0으로 보고 상태만 확정한다
        RunningTrack track = loadRunningTrackPort.load(command.runningRoomId(), userId);
        Optional<TrackAnalysis> analysis = TrackAnalyzer.analyze(
                track.points(), analysisTargetMeters(room), weightKg, properties);
        // 4. 기록은 만들 수 있을 때만 남긴다 — 상태 확정과 기록 생성은 별개다.
        //    기록이 없으면 표본이 그대로라 평균 페이스도 다시 낼 것이 없다
        analysis.ifPresent(result -> {
            createRecord(command, track, result, weightKg);
            updateAvgPace(userId);
        });

        // 5. 상태를 확정한다
        finish(player, room, analysis.map(TrackAnalysis::totalDistanceMeters).orElse(0));
        updateRunningPlayerPort.update(player);
        // 6. 러닝이 끝났으니 자리를 비운다 — 인원과 방 상태는 건드리지 않는다.
        //    러닝 시작 후 current_player_count는 "몇 명으로 확정됐나"로 고정된다(erd)
        room.finishSession(userId);
        // 7. 방은 마지막 한 사람이 끝낼 때 닫힌다.
        //    참가자 갱신을 먼저 반영해야 방금 끝낸 자신이 RUNNING으로 세어지지 않는다
        closeRoomIfLastPlayer(room);
        deleteTrackAfterCommit(command.runningRoomId(), userId);
    }

    // 솔로 방은 목표 거리가 없다 — 상한을 넘겨 실측 트랙을 자르지 않고 그대로 분석한다
    private int analysisTargetMeters(RunningRoom room) {
        return room.getTargetDistance().orElseGet(Distance::unlimited).meters();
    }

    private void createRecord(FinishRunningCommand command, RunningTrack track,
                              TrackAnalysis analysis, BigDecimal weightKg) {
        // 원본 트랙은 목표 이후 좌표까지 그대로 올린다 — 끊는 것은 기록뿐이다.
        // 키가 러닝 시작 시각으로 정해져 재시도해도 같은 객체를 덮어쓴다
        String gpsTrackKey = saveGpsTrackPort.save(new GpsTrackUpload(
                command.runningRoomId(), command.userId(),
                analysis.startAt(), analysis.endAt(), track.raw()));
        // 날씨는 출발 지점·출발 시각 기준이다 — 조회에 실패해도 어댑터가 기본값을 준다
        TrackPoint origin = track.points().get(0);
        Weather weather = loadWeatherPort.load(
                origin.latitude(), origin.longitude(), analysis.startAt());
        createRunningRecordPort.create(RunningRecord.finish()
                .runningRoomId(command.runningRoomId())
                .userId(command.userId())
                .avgPace(analysis.avgPaceSecondsPerKm())
                .totalDistance(analysis.totalDistanceMeters())
                .totalDuration(analysis.totalDurationSeconds())
                // 구간 칼로리의 합이 아니라 확정 거리·시간으로 다시 낸다
                .totalCalories(CalorieCalculator.kcal(
                        analysis.avgPaceSecondsPerKm(), analysis.totalDurationSeconds(), weightKg))
                .gpsTrackKey(gpsTrackKey)
                .routePolyline(analysis.routePolyline())
                .startAt(analysis.startAt())
                .endAt(analysis.endAt())
                .weatherCode(weather.code())
                .temperature(weather.temperature())
                .avgCadence(avgCadence(analysis.splits()))
                .totalElevationGain(analysis.totalElevationGainMeters())
                .splits(analysis.splits())
                .build());
    }

    // 러닝 전체 평균이라 구간 시간으로 가중한다 — 산술 평균은 오래 걸린 구간을 과소평가한다.
    // 케이던스는 선택 항목이라 표본이 하나도 없으면 null로 남긴다
    private Integer avgCadence(List<SplitDraft> splits) {
        long weighted = 0;
        long seconds = 0;
        for (SplitDraft split : splits) {
            if (split.avgCadence() == null) {
                continue;
            }
            weighted += (long) split.avgCadence() * split.duration();
            seconds += split.duration();
        }
        return seconds == 0 ? null : (int) (weighted / seconds);
    }

    // 확정 거리로만 판정한다. command.forced()는 조기 종료 '의사'일 뿐
    // 최종 상태를 정하지 않는다
    private void finish(RunningPlayer player, RunningRoom room, int totalDistanceMeters) {
        LocalDateTime finishedAt = LocalDateTime.now();
        Optional<Distance> target = room.getTargetDistance();
        // 목표가 없는 솔로 러닝은 사용자가 끝낸 것이 곧 완주다 — 비율을 잴 기준이 없다
        if (target.isEmpty() || totalDistanceMeters >= target.get().meters()) {
            player.complete(finishedAt);
            return;
        }
        double ratio = (double) totalDistanceMeters / target.get().meters();
        boolean penalty = ratio < properties.penaltyDistanceRatio()
                && room.getPlayerCount().current() >= PENALTY_MIN_PLAYER_COUNT;
        player.leave(penalty, finishedAt);
        if (penalty) {
            // 근거는 status(RUNNING_LEFT_PENALTY)에 남고, "지금 막혀 있나"는 Redis TTL이 답한다
            startMatchCooldownPort.start(player.getUserId(), properties.cooldown());
        }
    }

    // 시작 때 RUNNING이 된 참가자가 전원 종료되면 방도 끝난다.
    // 1인 방도 같은 규칙이다 — 인원이 0이 됐다고 닫지 않는다(시작 후 인원은 확정 시점 값으로 고정된다)
    private void closeRoomIfLastPlayer(RunningRoom room) {
        RunningRoomId roomId = room.getRunningRoomId().orElseThrow();
        // 강제 종료가 먼저 닫았을 수 있다 — 끝난 방에 다시 부르면 도메인 예외다
        if (room.getStatus() == RunningRoomStatus.STARTED
                && !existsRunningPlayerPort.existsRunning(roomId)) {
            LocalDateTime closedAt = LocalDateTime.now();
            // 행선지는 유효 기록 유무로 갈린다 — 시작만 눌렀거나 몇십 미터 만에 그만둔 방을
            // 완료로 남기지 않는다. 반대로 기록이 있으면 CANCELLED로 닫을 수 없다:
            // terminal이라 FINISHED에 못 가고 결과 조회 경로가 함께 끊긴다
            if (existsRunningRecordPort.existsInRoom(roomId)) {
                room.finish(closedAt);
            } else {
                room.cancel(closedAt);
            }
        }
        // 방을 닫지 않아도 세션 변경(is_connected)은 저장돼야 한다
        updateRunningRoomPort.update(room);
    }

    // 커밋이 실패하면 재시도가 같은 트랙으로 다시 확정해야 한다 — 삭제는 커밋 뒤로 미룬다.
    // 동기화가 없으면(트랜잭션 없이 페이크로 조립하는 테스트) 바로 지운다 —
    // 이 앱에서 트랜잭션이 열려 있는데 동기화가 없는 상태는 없다
    private void deleteTrackAfterCommit(Long runningRoomId, UserId userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteRunningTrackPort.delete(runningRoomId, userId);
                }
            });
            return;
        }
        deleteRunningTrackPort.delete(runningRoomId, userId);
    }

    // 최근 N건의 거리 합·시간 합으로 다시 낸다. 기록별 avg_pace의 산술 평균은
    // 300m 러닝과 10km 러닝을 같은 무게로 세서 짧은 기록이 값을 끌고 간다
    private void updateAvgPace(UserId userId) {
        List<RecentRunningPace> recent =
                loadRecentRunningPacesPort.loadRecent(userId, AVG_PACE_SAMPLE_SIZE);
        // 표본이 덜 찼으면 온보딩 입력값을 그대로 둔다 — 한두 번의 실측으로 갈아치우면
        // 컨디션 나쁜 하루가 그대로 실력이 된다. 자기 신고값이라도 '평소'에는 더 가깝다
        if (recent.size() < AVG_PACE_SAMPLE_SIZE) {
            return;
        }
        long meters = 0;
        long seconds = 0;
        for (RecentRunningPace record : recent) {
            meters += record.totalDistanceMeters();
            seconds += record.totalDurationSeconds();
        }
        // total_distance는 1 이상이 보장돼(ck_running_record_total_distance) 0으로 나눌 일이 없다
        updateUserAvgPacePort.updateAvgPace(userId, AvgPace.clamped((int) (seconds * 1000 / meters)));
    }
}
