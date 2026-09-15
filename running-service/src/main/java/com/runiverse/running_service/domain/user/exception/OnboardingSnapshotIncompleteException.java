package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.DeletedUserErrorCode;

public class OnboardingSnapshotIncompleteException extends BusinessException {

    public OnboardingSnapshotIncompleteException() {
        super(DeletedUserErrorCode.ONBOARDING_SNAPSHOT_INCOMPLETE);
    }
}
