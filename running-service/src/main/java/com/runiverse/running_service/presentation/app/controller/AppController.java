package com.runiverse.running_service.presentation.app.controller;

import com.runiverse.running_service.application.app.port.in.GetAppVersionUsecase;
import com.runiverse.running_service.application.app.query.version.GetAppVersionQuery;
import com.runiverse.running_service.application.app.query.version.GetAppVersionResult;
import com.runiverse.running_service.presentation.app.request.AppVersionRequest;
import com.runiverse.running_service.presentation.app.response.AppVersionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("app")
@RequiredArgsConstructor
public class AppController {

    private final GetAppVersionUsecase getAppVersionUsecase;

    @GetMapping("/version")
    public ResponseEntity<AppVersionResponse> getAppVersion(@Valid @ModelAttribute AppVersionRequest request) {
        GetAppVersionResult result = getAppVersionUsecase.handle(new GetAppVersionQuery(request.platform()));
        return ResponseEntity.ok(new AppVersionResponse(result.minimumVersion(), result.latestVersion()));
    }
}
