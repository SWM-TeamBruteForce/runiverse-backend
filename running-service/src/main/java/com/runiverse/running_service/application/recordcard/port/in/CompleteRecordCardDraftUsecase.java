package com.runiverse.running_service.application.recordcard.port.in;

import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftResult;

public interface CompleteRecordCardDraftUsecase {

    CompleteRecordCardDraftResult handle(CompleteRecordCardDraftCommand command);
}
