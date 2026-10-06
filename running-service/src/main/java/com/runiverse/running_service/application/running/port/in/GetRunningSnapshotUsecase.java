package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.query.snapshot.GetRunningSnapshotQuery;
import com.runiverse.running_service.application.running.query.snapshot.GetRunningSnapshotResult;

public interface GetRunningSnapshotUsecase {

    GetRunningSnapshotResult handle(GetRunningSnapshotQuery query);
}
