package com.runiverse.running_service.application.recordcard.port.in;

import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailQuery;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;

public interface GetRecordCardDetailUsecase {

    GetRecordCardDetailResult handle(GetRecordCardDetailQuery query);
}
