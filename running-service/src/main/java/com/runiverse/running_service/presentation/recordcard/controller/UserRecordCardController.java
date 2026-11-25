package com.runiverse.running_service.presentation.recordcard.controller;

import com.runiverse.running_service.application.recordcard.port.in.GetRecordCardsUsecase;
import com.runiverse.running_service.application.recordcard.query.list.GetRecordCardsQuery;
import com.runiverse.running_service.presentation.recordcard.response.RecordCardsResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 10-3 사용자의 기록 카드 목록 — 본인과 타인이 보는 범위만 다른 리소스라 {userId} 경로 하나에 둔다(api-convention).
// 프로필 그리드·전체 보기가 쓴다. 공개 범위는 유스케이스가 관계 등급으로 거른다
@RestController
@RequestMapping("users")
@RequiredArgsConstructor
public class UserRecordCardController {

    // 사용자 ID는 서버가 만든 UUIDv7이다 — 관계 API(RelationshipController)와 같은 형식 검사
    private static final String USER_ID_FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    // 공통 커서 규칙 — 넘으면 400이 아니라 50으로 줄인다
    private static final int MAX_LIMIT = 50;

    private final GetRecordCardsUsecase getRecordCardsUsecase;

    @GetMapping("/{userId}/record-cards")
    public ResponseEntity<RecordCardsResponse> getRecordCards(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = USER_ID_FORMAT, message = "입력값이 올바르지 않습니다.") String userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "입력값이 올바르지 않습니다.") int limit
    ) {
        return ResponseEntity.ok(RecordCardsResponse.from(getRecordCardsUsecase.handle(new GetRecordCardsQuery(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(userId),
                // 첫 페이지에 cursor=를 빈 값으로 실어 보내도 처음부터 읽는다
                cursor == null || cursor.isBlank() ? null : cursor,
                Math.min(limit, MAX_LIMIT)))));
    }
}
