package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.running.port.out.PlayerProfile;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// UserPersistenceAdapter의 프로필 일괄 조회를 대신한다.
// 실제 어댑터는 users와 user_onboarding을 inner join하므로 둘 중 하나만 없어도 결과에서 빠진다
public class InMemoryPlayerProfileStore implements LoadPlayerProfilesPort {

    private final InMemoryUserStore userStore;
    private final InMemoryOnboardingStore onboardingStore;
    private final Set<UUID> withdrawn = new HashSet<>();

    public InMemoryPlayerProfileStore(InMemoryUserStore userStore,
                                      InMemoryOnboardingStore onboardingStore) {
        this.userStore = userStore;
        this.onboardingStore = onboardingStore;
    }

    @Override
    public Map<UUID, PlayerProfile> loadProfiles(Collection<UUID> userIds) {
        Map<UUID, PlayerProfile> profiles = new LinkedHashMap<>();
        for (UUID userId : userIds) {
            if (withdrawn.contains(userId)) {
                continue;
            }
            Optional<User> user = userStore.findById(userId);
            Optional<String> nickname = onboardingStore.nicknameOf(userId);
            if (user.isEmpty() || nickname.isEmpty()) {
                continue;
            }
            profiles.put(userId, new PlayerProfile(userId, nickname.get(),
                    user.get().getProfileImageKey().map(ProfileImageKey::value).orElse(null)));
        }
        return profiles;
    }

    // 테스트 준비 — 탈퇴하면 users 행이 지워져 조회에서 빠진다(erd.md §0)
    public void withdraw(UUID userId) {
        withdrawn.add(userId);
    }
}
