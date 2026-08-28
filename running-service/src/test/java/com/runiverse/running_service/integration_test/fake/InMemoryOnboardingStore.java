package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.running.port.out.LoadUserWeightPort;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardingPort;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.LoadOnboardingProfilePort;
import com.runiverse.running_service.application.user.port.out.OnboardingProfile;
import com.runiverse.running_service.application.user.port.out.SaveOnboardingPort;
import com.runiverse.running_service.application.user.port.out.UpdateNicknamePort;
import com.runiverse.running_service.application.user.port.out.UpdateOnboardingPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.user.aggregate.UserOnboarding;
import com.runiverse.running_service.domain.user.vo.AvgPace;
import com.runiverse.running_service.domain.user.vo.Birthday;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.Weight;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class InMemoryOnboardingStore implements ExistsOnboardingPort,
        CheckNicknameDuplicatePort, SaveOnboardingPort, LoadNicknamePort, UpdateNicknamePort,
        UpdateOnboardingPort, LoadUserAvgPacePort, LoadUserWeightPort, LoadOnboardingProfilePort {

    // 실제 어댑터가 컬럼 단위로 갱신하므로 도메인 객체가 아니라 user_onboardings의 한 행을 들고 있는다
    @Getter
    public static class OnboardingRow {

        private final UserId userId;
        private final AvgPace avgPace;
        private Nickname nickname;
        private Gender gender;
        private Birthday birthday;
        private Weight weight;
        private Height height;

        private OnboardingRow(UserOnboarding onboarding) {
            this.userId = onboarding.getUserId();
            this.avgPace = onboarding.getAvgPace();
            this.nickname = onboarding.getNickname();
            this.gender = onboarding.getGender();
            this.birthday = onboarding.getBirthday();
            this.weight = onboarding.getWeight();
            this.height = onboarding.getHeight();
        }
    }

    private final Map<UUID, OnboardingRow> rows = new LinkedHashMap<>();
    // 온보딩 유즈케이스를 거치지 않고 상태만 심은 유저까지 포함한다
    private final Set<UUID> onboardedUserIds = new HashSet<>();

    @Override
    public void saveOnboarding(UserOnboarding onboarding) {
        UUID userId = onboarding.getUserId().value();
        rows.put(userId, new OnboardingRow(onboarding));
        onboardedUserIds.add(userId);
    }

    @Override
    public boolean existsByUserId(UserId userId) {
        return onboardedUserIds.contains(userId.value());
    }

    @Override
    public boolean existsByNickname(Nickname nickname) {
        return rows.values().stream().anyMatch(row -> row.nickname.equals(nickname));
    }

    @Override
    public Optional<Nickname> loadNickname(UserId userId) {
        return row(userId).map(OnboardingRow::getNickname);
    }

    // 실제 어댑터와 같이 행이 없으면 빈 Optional이고, 그게 곧 "온보딩 전" 판정이 된다
    @Override
    public Optional<OnboardingProfile> loadOnboardingProfile(UserId userId) {
        return row(userId).map(row -> new OnboardingProfile(
                row.gender, row.birthday, row.weight, row.height));
    }

    // 실제 어댑터와 같이 온보딩 행이 없으면 막고, 유니크 위반은 DB가 아니라 여기서 흉내 낸다
    @Override
    public void updateNickname(UserId userId, Nickname nickname) {
        row(userId).orElseThrow(OnboardingNotCompletedException::new).nickname = nickname;
    }

    // 실제 어댑터와 같이 담겨 온 컬럼만 바꾼다
    @Override
    public void updateOnboarding(UserId userId, Gender gender, Birthday birthday, Weight weight, Height height) {
        OnboardingRow row = row(userId).orElseThrow(OnboardingNotCompletedException::new);
        if (gender != null) {
            row.gender = gender;
        }
        if (birthday != null) {
            row.birthday = birthday;
        }
        if (weight != null) {
            row.weight = weight;
        }
        if (height != null) {
            row.height = height;
        }
    }

    // 실제 UserPersistenceAdapter와 같이 러닝의 평균 페이스 조회도 이 저장소가 겸한다
    // — 페이스 출처가 user_onboardings.avg_pace이기 때문이다.
    // 행이 없으면 빈 Optional이고, 그게 곧 "온보딩 미완료" 판정이 된다
    @Override
    public Optional<Pace> loadAvgPace(UserId userId) {
        return row(userId)
                .map(OnboardingRow::getAvgPace)
                .map(AvgPace::secondPerKm)
                .map(Pace::new);
    }

    // 체중도 같은 행에서 나온다 — 종료 시 칼로리 계산이 이 값을 쓴다.
    // 비어 있으면 온보딩을 마치지 않은 사용자다
    @Override
    public Optional<BigDecimal> loadWeightKg(UserId userId) {
        return row(userId)
                .map(OnboardingRow::getWeight)
                .map(Weight::value);
    }

    // 테스트 준비
    public void markOnboarded(UUID userId) {
        onboardedUserIds.add(userId);
    }

    // 검증 전용 — 갱신까지 반영된 현재 행
    public Optional<OnboardingRow> findByUserId(UUID userId) {
        return Optional.ofNullable(rows.get(userId));
    }

    // 검증 전용 — 갱신까지 반영된 현재 닉네임
    public Optional<String> nicknameOf(UUID userId) {
        return findByUserId(userId).map(row -> row.nickname.value());
    }

    public int size() {
        return rows.size();
    }

    private Optional<OnboardingRow> row(UserId userId) {
        return Optional.ofNullable(rows.get(userId.value()));
    }
}
