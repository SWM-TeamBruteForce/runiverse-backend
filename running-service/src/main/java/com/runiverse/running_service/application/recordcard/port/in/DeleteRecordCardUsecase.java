package com.runiverse.running_service.application.recordcard.port.in;

import com.runiverse.running_service.application.recordcard.command.delete.DeleteRecordCardCommand;

public interface DeleteRecordCardUsecase {

    void handle(DeleteRecordCardCommand command);
}
