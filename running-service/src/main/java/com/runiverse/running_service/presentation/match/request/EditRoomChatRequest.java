package com.runiverse.running_service.presentation.match.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 보내기(SendRoomChatRequest)와 같은 검증이다(api-spec 5-E-3) — 도메인 VO(RunningRoomChatContent)도 같은 규칙을 지키지만 그쪽 예외는 500이다.
// 내 메시지인지·지웠는지·방이 열려 있는지는 저장소를 봐야 해 EditRoomChatHandler가 본다
public record EditRoomChatRequest(
        // 누락·빈 문자열·공백만 모두 여기서 걸린다
        @NotBlank(message = "메시지 내용은 필수입니다.")
        @Size(max = 200, message = "메시지는 200자 이하여야 합니다.")
        String content
) {

}
