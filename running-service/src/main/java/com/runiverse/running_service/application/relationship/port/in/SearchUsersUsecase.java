package com.runiverse.running_service.application.relationship.port.in;

import com.runiverse.running_service.application.relationship.query.search.SearchUsersQuery;
import com.runiverse.running_service.application.relationship.query.search.SearchUsersResult;

public interface SearchUsersUsecase {

    SearchUsersResult handle(SearchUsersQuery query);
}
