package com.runiverse.running_service.application.recordcard.port.in;

import com.runiverse.running_service.application.recordcard.query.list.GetRecordCardsQuery;
import com.runiverse.running_service.application.recordcard.query.list.GetRecordCardsResult;

public interface GetRecordCardsUsecase {

    GetRecordCardsResult handle(GetRecordCardsQuery query);
}
