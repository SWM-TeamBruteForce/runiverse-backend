package com.runiverse.running_service.application.running.command.finish;

import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningNotStartableException;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.out.CreateRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.DeleteRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.GpsTrackUpload;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadUserWeightPort;
import com.runiverse.running_service.application.running.port.out.LoadWeatherPort;
import com.runiverse.running_service.application.running.port.out.RunningTrack;
import com.runiverse.running_service.application.running.port.out.SaveGpsTrackPort;
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
    private final RunningFinishProperties properties;

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
        //    산출할 수 없는 트랙이면 실제 거리를 0으로 보고 상태만 확정한다(feature-spec §2)
        RunningTrack track = loadRunningTrackPort.load(command.runningRoomId(), userId);
        Optional<TrackAnalysis> analysis = TrackAnalyzer.analyze(
                track.points(), analysisTargetMeters(room), weightKg, properties);
        // 4. 기록은 만들 수 있을 때만 남긴다 — 상태 확정과 기록 생성은 별개다(erd.md running_records)
        analysis.ifPresent(result -> createRecord(command, track, result, weightKg));

        // 5. 상태를 확정한다
        finish(player, room, analysis.map(TrackAnalysis::totalDistanceMeters).orElse(0));
        updateRunningPlayerPort.update(player);

        // 6. 방은 마지막 한 사람이 끝낼 때 닫힌다.
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
        // 원본 트랙은 목표 이후 좌표까지 그대로 올린다 — 끊는 것은 기록뿐이다(erd.md).
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
                // 구간 칼로리의 합이 아니라 확정 거리·시간으로 다시 낸다(erd.md running_records)
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
    // 최종 상태를 정하지 않는다(api-spec 5-D)
    private void finish(RunningPlayer player, RunningRoom room, int totalDistanceMeters) {
        LocalDateTime finishedAt = LocalDateTime.now();
        Optional<Distance> target = room.getTargetDistance();
        // 목표가 없는 솔로 러닝은 사용자가 끝낸 것이 곧 완주다 — 비율을 잴 기준이 없다
        if (target.isEmpty() || totalDistanceMeters >= target.get().meters()) {
            player.complete(finishedAt);
            return;
        }
        double ratio = (double) totalDistanceMeters / target.get().meters();
        player.leave(ratio < properties.penaltyDistanceRatio(), finishedAt);
    }

    // 시작 때 RUNNING이 된 참가자가 전원 종료되면 방도 끝난다(api-spec 5-D).
    // 1인 방도 같은 규칙이다 — 인원이 0이 됐다고 CANCELLED로 닫지 않는다.
    // 닫으면 CANCELLED가 terminal이라 FINISHED에 못 가고 결과 조회 경로가 무너진다(feature-spec §2)
    private void closeRoomIfLastPlayer(RunningRoom room) {
        // 타임아웃이 먼저 닫았을 수 있다 — 끝난 방에 finish()를 다시 부르면 도메인 예외다
        if (room.getStatus() != RunningRoomStatus.STARTED
                || existsRunningPlayerPort.existsRunning(room.getRunningRoomId().orElseThrow())) {
            return;
        }
        room.finish(LocalDateTime.now());
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
}
