package com.runiverse.running_service.presentation.user.controller;

import com.runiverse.running_service.application.running.port.in.GetMyRunningRecordsUsecase;
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsQuery;
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsResult;
import com.runiverse.running_service.presentation.user.request.RunningRecordsRequest;
import com.runiverse.running_service.presentation.user.response.RunningRecordsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("users/me/running-records")
@RequiredArgsConstructor
public class RunningRecordController {

    private final GetMyRunningRecordsUsecase getMyRunningRecordsUsecase;

    @GetMapping
    public ResponseEntity<RunningRecordsResponse> getMyRecords(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute RunningRecordsRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        GetMyRunningRecordsResult result = getMyRunningRecordsUsecase.handle(
                new GetMyRunningRecordsQuery(
                        userId, LocalDate.parse(request.from()), LocalDate.parse(request.to())));
        return ResponseEntity.ok(RunningRecordsResponse.from(result));
    }
}
