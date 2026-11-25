package com.runiverse.running_service.application.recordcard.port.in;

import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardCommand;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardResult;

public interface UpdateRecordCardUsecase {

    UpdateRecordCardResult handle(UpdateRecordCardCommand command);
}
