package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.ChangeLiveRunningStatusPort;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatusChange;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.PublishRunningProgressPort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.RunningProgress;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 참가자 상태를 바꾸고, 바뀌었으면 방에 알린다.
// 좌표 배치와 무관하게 일어나는 변화라 진행 통지를 직접 낸다 — 기다리면 멈춘 동안에는 영영 안 나간다
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveRunningStatusChanger {

    private final ChangeLiveRunningStatusPort changeLiveRunningStatusPort;
    private final LoadRunningDistancePort loadRunningDistancePort;
    private final PublishRunningProgressPort publishRunningProgressPort;

    // 실패해도 던지지 않는다 — 표시용 값이라 시작·종료·연결 정리를 막을 이유가 없다
    public void change(Long runningRoomId, UserId userId, Integer targetDistanceMeters,
                       LiveRunningStatus status) {
        try {
            LiveRunningStatusChange change =
                    changeLiveRunningStatusPort.change(runningRoomId, userId, status);
            // 그대로면 알리지 않는다 — 같은 PAUSE가 여러 번 와도 상대 화면은 한 번만 바뀐다
            if (!change.changed()) {
                return;
            }
            // 진행 정보 모양 그대로 보낸다 — 클라는 참가자별 최신값을 이 메시지 하나로 덮는다
            RunningDistance distance = loadRunningDistancePort.loadDistance(runningRoomId, userId);
            publishRunningProgressPort.publish(runningRoomId, new RunningProgress(
                    userId.value(),
                    distance.metersRounded(),
                    targetDistanceMeters,
                    distance.lastPaceSecondsPerKm(),
                    change.current()));
        } catch (RuntimeException e) {
            log.error("[러닝] 참가자 상태 변경 실패: 처리하지 못한 예외 - roomId={}, userId={}, status={}",
                    runningRoomId, userId.value(), status, e);
        }
    }
}
