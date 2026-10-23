package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsQuery;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;

public interface GetMatchSlotsUsecase {

    GetMatchSlotsResult handle(GetMatchSlotsQuery query);
}
