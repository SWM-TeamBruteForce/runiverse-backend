package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.DeleteRunningComboStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 방이 닫힐 때 콤보를 정리한다 — 방 닫힘 트랜잭션 안에서 부르고, 실제 일은 커밋 뒤에 한다.
// 같은 트랜잭션에서 하면 콤보 읽기·저장이 실패할 때 마지막 참가자의 종료까지 롤백되고,
// 그동안 방·참가자 잠금을 쥔다. 대가로 실패하면 그 방의 콤보는 남지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class RunningComboArchiver {

    private final RunningComboWriter runningComboWriter;
    private final DeleteRunningComboStatePort deleteRunningComboStatePort;

    // FINISHED — 콤보를 남기고 상태를 지운다
    public void archiveAfterCommit(Long runningRoomId) {
        afterCommit(() -> archive(runningRoomId));
    }

    // CANCELLED — 남길 기록이 없어 저장하지 않고 상태만 지운다
    public void discardAfterCommit(Long runningRoomId) {
        afterCommit(() -> deleteRunningComboStatePort.deleteComboState(runningRoomId));
    }

    private void archive(Long runningRoomId) {
        RunningComboArchive archive;
        try {
            archive = runningComboWriter.write(runningRoomId);
        } catch (RuntimeException e) {
            // 방 닫힘은 이미 커밋됐다 — 여기서 던지면 커밋된 종료가 클라에는 실패로 보인다.
            // 상태는 지우지 않는다: TTL까지 남아 원인을 들여다볼 수 있다
            log.error("[러닝] 콤보 저장 실패: 처리하지 못한 예외 - roomId={}", runningRoomId, e);
            return;
        }
        log.info("[러닝] 콤보 저장 성공 - roomId={}, closedCount={}, openCount={}, rowCount={}",
                runningRoomId, archive.closedCount(), archive.openCount(), archive.rowCount());
        // 저장이 커밋된 뒤에만 지운다 — 먼저 지우면 실패한 방의 콤보가 어디에도 남지 않는다
        deleteRunningComboStatePort.deleteComboState(runningRoomId);
    }

    // 동기화가 없으면(트랜잭션 없이 페이크로 조립하는 테스트) 바로 한다 —
    // 이 앱에서 트랜잭션이 열려 있는데 동기화가 없는 상태는 없다
    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }
}
