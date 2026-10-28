package com.runiverse.running_service.application.user.query.profile;

import com.runiverse.running_service.application.common.port.out.CountFriendsPort;
import com.runiverse.running_service.application.common.port.out.LoadRelationshipTypesPort;
import com.runiverse.running_service.application.common.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.common.UserImageType;
import com.runiverse.running_service.application.user.common.UserImageUrlResolver;
import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.port.in.GetUserProfileUsecase;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.Nickname;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserProfileHandler implements GetUserProfileUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final LoadNicknamePort loadNicknamePort;
    private final UserImageUrlResolver userImageUrlResolver;
    private final LoadRelationshipTypesPort loadRelationshipTypesPort;
    private final CountFriendsPort countFriendsPort;

    @Override
    public GetUserProfileResult handle(GetUserProfileQuery query) {
        UserId targetUserId = new UserId(query.targetUserId());

        // 1. 없는 사용자와 탈퇴한 사용자를 구분하지 않는다
        User user = loadUserByIdPort.loadById(targetUserId).orElseThrow(ProfileNotFoundException::new);

        boolean isMe = query.viewerId().equals(query.targetUserId());

        // 2. 사진이 없으면 URL도 없다
        String profileImageUrl = userImageUrlResolver.resolve(UserImageType.PROFILE, user);

        // 3. 도메인의 빈 문자열을 조회 응답 규칙인 null로 되돌린다
        String introduction = user.getIntroduction().value();

        // 4. 본인에게는 관계가 없다 — NONE이 아니라 null이다
        RelationshipType relationshipType = isMe ? null : relationshipWith(new UserId(query.viewerId()), targetUserId);

        return new GetUserProfileResult(
                user.getUserId().value(),
                isMe,
                loadNicknamePort.loadNickname(targetUserId).map(Nickname::value).orElse(null),
                profileImageUrl,
                introduction.isEmpty() ? null : introduction,
                countFriendsPort.countFriends(targetUserId),
                relationshipType
        );
    }

    // 조회자 행의 지금 관계 — 행은 처음 요청할 때 생겨 없으면 아무 관계도 아니다
    private RelationshipType relationshipWith(UserId viewerId, UserId targetUserId) {
        return loadRelationshipTypesPort.loadTypes(viewerId, List.of(targetUserId.value()))
                .getOrDefault(targetUserId.value(), RelationshipType.NONE);
    }
}
