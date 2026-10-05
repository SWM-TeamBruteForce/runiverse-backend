package com.runiverse.running_service.application.user.query.profile;

import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetUserProfileUsecase;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserProfileHandler implements GetUserProfileUsecase {

    // TODO: 친구 목데이터 — 친구 요청·수락·삭제 API를 만들 때 실제 조회로 교체한다
    private static final long MOCK_FRIEND_COUNT = 42L;
    private static final String MOCK_FRIEND_STATUS = "ACCEPTED";

    private final LoadUserByIdPort loadUserByIdPort;
    private final LoadNicknamePort loadNicknamePort;
    private final GenerateViewUrlPort generateViewUrlPort;

    @Override
    public GetUserProfileResult handle(GetUserProfileQuery query) {
        UserId targetUserId = new UserId(query.targetUserId());

        // 1. 없는 사용자와 탈퇴한 사용자를 구분하지 않는다
        User user = loadUserByIdPort.loadById(targetUserId).orElseThrow(ProfileNotFoundException::new);

        boolean isMe = query.viewerId().equals(query.targetUserId());

        // 2. 사진이 없으면 URL도 없다
        String profileImageUrl = user.getProfileImageKey()
                .map(ProfileImageKey::value)
                .map(generateViewUrlPort::generate)
                .orElse(null);

        // 3. 도메인의 빈 문자열을 조회 응답 규칙인 null로 되돌린다
        String introduction = user.getIntroduction().value();

        return new GetUserProfileResult(
                user.getUserId().value(),
                isMe,
                loadNicknamePort.loadNickname(targetUserId).map(Nickname::value).orElse(null),
                profileImageUrl,
                introduction.isEmpty() ? null : introduction,
                MOCK_FRIEND_COUNT,
                isMe ? null : MOCK_FRIEND_STATUS   // 본인에게는 친구 관계가 없다
        );
    }
}
