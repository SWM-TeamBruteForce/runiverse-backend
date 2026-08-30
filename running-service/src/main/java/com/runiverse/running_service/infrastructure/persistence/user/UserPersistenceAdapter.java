package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.application.auth.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByEmailPort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByProviderPort;
import com.runiverse.running_service.application.auth.port.out.SaveUserPort;
import com.runiverse.running_service.application.running.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.running.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.running.port.out.LoadUserWeightPort;
import com.runiverse.running_service.application.running.port.out.PlayerProfile;
import com.runiverse.running_service.application.user.exception.NicknameAlreadyExistsException;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ClearProfileImagePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardingPort;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.LoadOnboardingProfilePort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.OnboardingProfile;
import com.runiverse.running_service.application.user.port.out.SaveOnboardingPort;
import com.runiverse.running_service.application.user.port.out.UpdateIntroductionPort;
import com.runiverse.running_service.application.user.port.out.UpdateNicknamePort;
import com.runiverse.running_service.application.user.port.out.UpdateOnboardingPort;
import com.runiverse.running_service.application.user.port.out.UpdatePasswordPort;
import com.runiverse.running_service.application.user.port.out.UpdateProfileImagePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.aggregate.UserOnboarding;
import com.runiverse.running_service.domain.user.vo.Birthday;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.Introduction;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.PasswordHash;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.user.vo.Weight;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements CheckEmailDuplicatePort, SaveUserPort, LoadUserByEmailPort,
        LoadUserByProviderPort, LoadUserByIdPort, ExistsOnboardingPort, CheckNicknameDuplicatePort, SaveOnboardingPort,
        UpdateProfileImagePort, ClearProfileImagePort, LoadNicknamePort, UpdateNicknamePort,
        UpdatePasswordPort, LoadUserAvgPacePort, UpdateIntroductionPort, UpdateOnboardingPort, LoadUserWeightPort,
        LoadOnboardingProfilePort, LoadPlayerProfilesPort {

    private final EntityManager entityManager;

    @Override
    public boolean existsByEmail(String email) {
        Long count = entityManager.createQuery(
                        """
                                SELECT COUNT(u)
                                FROM UserJpaEntity u
                                WHERE u.email = :email
                                """, Long.class
                )
                .setParameter("email", email)
                .getSingleResult();

        return count > 0;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = UserJpaEntity.create(
                user.getUserId().value(),
                user.getEmail().value(),
                emptyToNull(user.getPasswordHash().value()),
                user.isAlertConsent(),
                user.getProfileImageKey().map(ProfileImageKey::value).orElse(null),
                user.getProfileVisibility(),
                emptyToNull(user.getIntroduction().value())
        );

        entityManager.persist(entity);
        user.getOauthUser().ifPresent(oauth -> entityManager.persist((
                OauthUserJpaEntity.create(
                        oauth.getUserId().value(),
                        oauth.getProvider(),
                        oauth.getProviderId().value()
                )
        )));
        return user;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    @Override
    public Optional<User> loadById(UserId userId) {
        return Optional.ofNullable(entityManager.find(UserJpaEntity.class, userId.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<User> loadByEmail(String email) {
        return entityManager.createQuery(
                        """
                                SELECT u
                                FROM UserJpaEntity u
                                WHERE u.email = :email
                                """, UserJpaEntity.class
                )
                .setParameter("email", email)
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public void updateProfileImage(UserId userId, ProfileImageKey profileImageKey) {
        UserJpaEntity entity = entityManager.find(UserJpaEntity.class, userId.value());
        if (entity == null) {
            throw new UserNotFoundException();
        }
        entity.changeProfileImageKey(profileImageKey.value());

    }

    @Override
    public void clearProfileImage(UserId userId) {
        UserJpaEntity entity = entityManager.find(UserJpaEntity.class, userId.value());
        if (entity == null) {
            throw new UserNotFoundException();
        }
        entity.changeProfileImageKey(null); // 변경 감지로 user.profile_image_key = null이 된다.
    }

    @Override
    public void updatePassword(UserId userId, PasswordHash passwordHash) {
        UserJpaEntity entity = entityManager.find(UserJpaEntity.class, userId.value());
        if (entity == null) {
            throw new UserNotFoundException();
        }
        entity.changePasswordHash(passwordHash.value());
    }

    @Override
    public void updateIntroduction(UserId userId, Introduction introduction) {
        UserJpaEntity entity = entityManager.find(UserJpaEntity.class, userId.value());
        if (entity == null) {
            throw new UserNotFoundException();
        }
        // 빈 소개글은 컬럼을 비운다 — 가입 시 save()도 같은 방식이라 "없음"의 표현을 하나로 둔다
        entity.changeIntroduction(emptyToNull(introduction.value()));
    }

    @Override
    public void updateOnboarding(UserId userId, Gender gender, Birthday birthday,
                                 Weight weight, Height height) {
        UserOnboardingJpaEntity entity =
                entityManager.find(UserOnboardingJpaEntity.class, userId.value());
        if (entity == null) {
            throw new OnboardingNotCompletedException();
        }
        // 담겨 온 값만 바꾼다 — 나머지 컬럼은 건드리지 않는다
        if (gender != null) {
            entity.changeGender(gender);
        }
        if (birthday != null) {
            entity.changeBirthday(birthday.value());
        }
        if (weight != null) {
            entity.changeWeight(weight.value());
        }
        if (height != null) {
            entity.changeHeight(height.value());
        }
    }

    // 칼로리 계산에만 쓴다. 온보딩을 안 끝낸 유저는 행이 없어 Optional이다 —
    // 그 경우 기록 없이 상태만 확정하는 경로로 흘러간다
    @Override
    public Optional<BigDecimal> loadWeightKg(UserId userId) {
        return entityManager.createQuery("""
                        select onboarding.weight
                        from UserOnboardingJpaEntity onboarding
                        where onboarding.userId = :userId
                        """, BigDecimal.class)
                .setParameter("userId", userId.value())
                .getResultStream()
                .findFirst();
    }

    @Override
    public Map<UUID, PlayerProfile> loadProfiles(Collection<UUID> userIds) {
        // in ()는 문법 오류다 — 빈 목록은 쿼리 없이 끝낸다
        if (userIds.isEmpty()) {
            return Map.of();
        }
        // 닉네임은 users가 아니라 user_onboarding에 있다.
        // 탈퇴자는 users 행이 지워져 결과에서 빠지고, 호출자가 그것으로 탈퇴를 판정한다
        return entityManager.createQuery("""
                        select new com.runiverse.running_service.application.running.port.out.PlayerProfile(
                            userEntity.userId, onboarding.nickname, userEntity.profileImageKey)
                        from UserJpaEntity userEntity
                        join UserOnboardingJpaEntity onboarding
                            on onboarding.userId = userEntity.userId
                        where userEntity.userId in :userIds
                        """, PlayerProfile.class)
                .setParameter("userIds", userIds)
                .getResultStream()
                .collect(Collectors.toMap(PlayerProfile::userId, profile -> profile));
    }

    private User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getUserId(),
                entity.getEmail(),
                Objects.requireNonNullElse(entity.getPasswordHash(), ""),
                entity.isAlertConsent(),
                entity.getProfileImageKey(),
                entity.getProfileVisibility(),
                Objects.requireNonNullElse(entity.getIntroduction(), "")
        );
    }

    @Override
    public Optional<User> loadByProvider(Provider provider, String providerId) {
        return entityManager.createQuery(
                        """
                                SELECT u
                                FROM UserJpaEntity u, OauthUserJpaEntity o
                                WHERE o.userId = u.userId
                                    AND o.provider = :provider
                                    AND o.providerId = :providerId
                                """, UserJpaEntity.class
                )
                .setParameter("provider", provider)
                .setParameter("providerId", providerId)
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public boolean existsByUserId(UserId userId) {
        Long count = entityManager.createQuery(
                        """
                                SELECT COUNT(o)
                                FROM UserOnboardingJpaEntity o
                                WHERE o.userId = :userId
                                """, Long.class
                )
                .setParameter("userId", userId.value())
                .getSingleResult();
        return count > 0;
    }

    @Override
    public boolean existsByNickname(Nickname nickname) {
        Long count = entityManager.createQuery(
                        """
                                SELECT COUNT(o)
                                FROM UserOnboardingJpaEntity o
                                WHERE o.nickname = :nickname
                                """, Long.class
                )
                .setParameter("nickname", nickname.value())
                .getSingleResult();
        return count > 0;
    }

    @Override
    public void saveOnboarding(UserOnboarding onboarding) {
        entityManager.persist(UserOnboardingJpaEntity.create(
                onboarding.getUserId().value(),
                onboarding.getNickname().value(),
                onboarding.getGender(),
                onboarding.getBirthday().value(),
                onboarding.getAvgPace().secondPerKm(),
                onboarding.getWeight().value(),
                onboarding.getHeight().value()
        ));
    }

    @Override
    public Optional<Nickname> loadNickname(UserId userId) {
        return Optional.ofNullable(entityManager.find(UserOnboardingJpaEntity.class, userId.value()))
                .map(UserOnboardingJpaEntity::getNickname)
                .map(Nickname::new);
    }

    @Override
    public Optional<OnboardingProfile> loadOnboardingProfile(UserId userId) {
        // 온보딩 행이 없으면 빈 Optional — 온보딩 전에도 프로필 편집 화면은 열린다
        return Optional.ofNullable(entityManager.find(UserOnboardingJpaEntity.class, userId.value()))
                .map(entity -> new OnboardingProfile(
                        entity.getGender(),
                        new Birthday(entity.getBirthday()),
                        new Weight(entity.getWeight()),
                        new Height(entity.getHeight())
                ));
    }

    @Override
    public void updateNickname(UserId userId, Nickname nickname) {
        UserOnboardingJpaEntity entity = entityManager.find(UserOnboardingJpaEntity.class, userId.value());
        if (entity == null) {
            throw new OnboardingNotCompletedException();
        }
        entity.changeNickname(nickname.value());
        try {
            // 커밋을 미루면 유니크 위반이 밖에 터진다. -> 잡지 못한 것이기 떄문에 오류로 처리
            entityManager.flush();
        } catch (PersistenceException e) {
            throw new NicknameAlreadyExistsException();
        }
    }

    @Override
    public Optional<Pace> loadAvgPace(UserId userId) {
        // 온보딩 완료 = user_onboardings row 존재 (erd.md §user_onboardings).
        // row가 없으면 빈 Optional — 핸들러가 ONBOARDING_NOT_COMPLETED로 바꾼다
        return entityManager.createQuery(
                        """
                                SELECT o.avgPace
                                FROM UserOnboardingJpaEntity o
                                WHERE o.userId = :userId
                                """, Integer.class
                )
                .setParameter("userId", userId.value())
                .getResultStream()
                .findFirst()
                .map(Pace::new);
    }
}
