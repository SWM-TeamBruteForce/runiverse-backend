package com.runiverse.running_service.domain.relationship.vo;

import com.runiverse.running_service.domain.relationship.exception.InvalidUserRelationshipIdException;

public record UserRelationshipId(Long value) {

    public UserRelationshipId {
        if (value == null || value < 1) {
            throw new InvalidUserRelationshipIdException();
        }
    }
}
