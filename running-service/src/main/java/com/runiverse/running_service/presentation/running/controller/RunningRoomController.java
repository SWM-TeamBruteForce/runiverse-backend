package com.runiverse.running_service.presentation.running.controller;

import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomCommand;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomResult;
import com.runiverse.running_service.application.running.port.in.GetRunningResultsUsecase;
import com.runiverse.running_service.application.running.port.in.GetRunningSplitResultsUsecase;
import com.runiverse.running_service.application.running.port.in.OpenSoloRoomUsecase;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsQuery;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsResult;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsQuery;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsResult;
import com.runiverse.running_service.presentation.running.response.RunningResultsResponse;
import com.runiverse.running_service.presentation.running.response.RunningSplitResultsResponse;
import com.runiverse.running_service.presentation.running.response.SoloRunningStartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("running-rooms")
@RequiredArgsConstructor
public class RunningRoomController {

    private final OpenSoloRoomUsecase startSoloRunningUsecase;
    private final GetRunningResultsUsecase getRunningResultsUsecase;
    private final GetRunningSplitResultsUsecase getRunningSplitResultsUsecase;

    @PostMapping("/solo")
    public ResponseEntity<SoloRunningStartResponse> startSolo(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        OpenSoloRoomResult result = startSoloRunningUsecase.handle(
                new OpenSoloRoomCommand(userId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SoloRunningStartResponse(result.runningRoomId()));
    }

    @GetMapping("/{runningRoomId}/results")
    public ResponseEntity<RunningResultsResponse> getResults(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long runningRoomId
    ) {
        // 조회자는 토큰에서만 온다 — 클라가 보낸 값이면 남의 결과를 볼 수 있다
        UUID userId = UUID.fromString(jwt.getSubject());
        GetRunningResultsResult result = getRunningResultsUsecase.handle(
                new GetRunningResultsQuery(runningRoomId, userId));
        return ResponseEntity.ok(RunningResultsResponse.from(result));
    }

    @GetMapping("/{runningRoomId}/split-results")
    public ResponseEntity<RunningSplitResultsResponse> getSplitResults(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long runningRoomId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        GetRunningSplitResultsResult result = getRunningSplitResultsUsecase.handle(
                new GetRunningSplitResultsQuery(runningRoomId, userId));
        return ResponseEntity.ok(RunningSplitResultsResponse.from(result));
    }
}
