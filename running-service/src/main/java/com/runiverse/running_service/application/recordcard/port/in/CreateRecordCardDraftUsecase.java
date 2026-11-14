package com.runiverse.running_service.application.recordcard.port.in;

import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;

public interface CreateRecordCardDraftUsecase {

    CreateRecordCardDraftResult handle(CreateRecordCardDraftCommand command);
}
