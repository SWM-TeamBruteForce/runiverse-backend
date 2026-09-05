package com.runiverse.running_service.unit_test.infrastructure.persistence.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.aggregate.UserOnboarding;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Introduction;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.PasswordHash;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.persistence.user.OauthUserJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.user.UserJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.user.UserOnboardingJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.user.UserPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserPersistenceAdapterTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";

    private static String profileImageKeyOf(UUID userId) {
        return "profiles/" + userId + "/photo.jpg";
    }

    // 카카오 회원번호
    private static final String PROVIDER_ID = "1234567890";

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Long> countQuery;

    @Mock
    private TypedQuery<UserJpaEntity> userQuery;

    @InjectMocks
    private UserPersistenceAdapter userPersistenceAdapter;

    @Test
    @DisplayName("동일한 이메일이 존재하면 true를 반환한다")
    void existsByEmailReturnsTrue() {
        // given
        String email = "test@example.com";

        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenReturn(countQuery);

        when(countQuery.setParameter("email", email))
                .thenReturn(countQuery);

        when(countQuery.getSingleResult())
                .thenReturn(1L);

        // when
        boolean result = userPersistenceAdapter.existsByEmail(email);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("동일한 이메일이 존재하지 않으면 false를 반환한다")
    void existsByEmailReturnsFalse() {
        // given
        String email = "test@example.com";

        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenReturn(countQuery);

        when(countQuery.setParameter("email", email))
                .thenReturn(countQuery);

        when(countQuery.getSingleResult())
                .thenReturn(0L);

        // when
        boolean result = userPersistenceAdapter.existsByEmail(email);

        // then -> 이메일이 실제로 쿼리에 바인딩됐는지는 반환값으로 증명되지 않는다
        assertThat(result).isFalse();
        verify(countQuery).setParameter("email", email);
        verify(countQuery).getSingleResult();
    }

    @Test
    @DisplayName("이메일에 해당하는 유저를 도메인 User로 변환해 반환한다")
    void loadByEmailReturnsUser() {
        // given
        String email = "test@example.com";
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        UserJpaEntity entity = UserJpaEntity.create(
                userId, email, PASSWORD_HASH, true, profileImageKeyOf(userId), ProfileVisibility.FRIENDS, "러닝을 좋아합니다"
        );

        givenUserQueryReturns(email, Stream.of(entity));

        // when
        Optional<User> result = userPersistenceAdapter.loadByEmail(email);

        // then
        assertThat(result).isPresent();

        User user = result.get();
        assertThat(user.getUserId().value()).isEqualTo(userId);
        assertThat(user.getEmail().value()).isEqualTo(email);
        assertThat(user.getPasswordHash().value()).isEqualTo(PASSWORD_HASH);
        assertThat(user.isAlertConsent()).isTrue();
        assertThat(user.getProfileImageKey())
                .map(ProfileImageKey::value)
                .contains(profileImageKeyOf(userId));
        assertThat(user.getProfileVisibility()).isEqualTo(ProfileVisibility.FRIENDS);
        assertThat(user.getIntroduction().value()).isEqualTo("러닝을 좋아합니다");
    }

    @Test
    @DisplayName("이메일에 해당하는 유저가 없으면 빈 Optional을 반환한다")
    void loadByEmailReturnsEmpty() {
        // given
        String email = "none@example.com";

        givenUserQueryReturns(email, Stream.empty());

        // when
        Optional<User> result = userPersistenceAdapter.loadByEmail(email);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("introduction이 null이면 빈 문자열로 변환한다")
    void loadByEmailConvertsNullIntroduction() {
        // given
        String email = "test@example.com";

        UserJpaEntity entity = UserJpaEntity.create(
                UuidCreator.getTimeOrderedEpoch(), email, PASSWORD_HASH, false, null, ProfileVisibility.PUBLIC, null
        );

        givenUserQueryReturns(email, Stream.of(entity));

        // when
        Optional<User> result = userPersistenceAdapter.loadByEmail(email);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getIntroduction().value()).isEmpty();
    }

    @Test
    @DisplayName("provider와 providerId에 연동된 유저를 도메인 User로 변환해 반환한다")
    void loadByProviderReturnsUser() {
        // given -> 소셜 전용 계정은 password_hash와 introduction이 NULL로 저장된다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserJpaEntity entity = UserJpaEntity.create(
                userId, "kakao@example.com", null, false, null, ProfileVisibility.PUBLIC, null
        );

        givenProviderQueryReturns(PROVIDER_ID, Stream.of(entity));

        // when
        Optional<User> result = userPersistenceAdapter.loadByProvider(Provider.KAKAO, PROVIDER_ID);

        // then
        assertThat(result).isPresent();

        User user = result.get();
        assertThat(user.getUserId().value()).isEqualTo(userId);
        assertThat(user.getEmail().value()).isEqualTo("kakao@example.com");

        // NULL은 도메인이 허용하지 않으므로 빈 문자열로 복원되어야 한다
        assertThat(user.getPasswordHash().value()).isEmpty();
        assertThat(user.getIntroduction().value()).isEmpty();

        // 사진은 빈 문자열이 아니라 빈 Optional로 복원된다
        assertThat(user.getProfileImageKey()).isEmpty();
    }

    @Test
    @DisplayName("연동된 유저가 없으면 빈 Optional을 반환한다")
    void loadByProviderReturnsEmpty() {
        // given
        givenProviderQueryReturns(PROVIDER_ID, Stream.empty());

        // when
        Optional<User> result = userPersistenceAdapter.loadByProvider(Provider.KAKAO, PROVIDER_ID);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("소셜 연결이 있는 유저를 저장하면 oauth_user 행도 함께 저장한다")
    void saveAlsoPersistsOauthLink() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        User user = User.registerWithOauth(
                userId, "kakao@example.com", Provider.KAKAO, PROVIDER_ID
        );

        // when
        userPersistenceAdapter.save(user);

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(entityManager, times(2)).persist(captor.capture());

        List<Object> persisted = captor.getAllValues();
        assertThat(persisted.get(0)).isInstanceOf(UserJpaEntity.class);
        assertThat(persisted.get(1)).isInstanceOf(OauthUserJpaEntity.class);

        OauthUserJpaEntity link = (OauthUserJpaEntity) persisted.get(1);
        assertThat(link.getUserId()).isEqualTo(userId);
        assertThat(link.getProvider()).isEqualTo(Provider.KAKAO);
        assertThat(link.getProviderId()).isEqualTo(PROVIDER_ID);
    }

    @Test
    @DisplayName("소셜 전용 유저의 빈 비밀번호와 빈 설명은 NULL로 저장한다")
    void saveConvertsEmptyValuesToNull() {
        // given
        User user = User.registerWithOauth(
                UuidCreator.getTimeOrderedEpoch(), "kakao@example.com", Provider.KAKAO, PROVIDER_ID
        );

        // when
        userPersistenceAdapter.save(user);

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(entityManager, times(2)).persist(captor.capture());

        UserJpaEntity entity = (UserJpaEntity) captor.getAllValues().get(0);
        assertThat(entity.getPasswordHash()).isNull();
        assertThat(entity.getIntroduction()).isNull();
    }

    @Test
    @DisplayName("신규 유저를 저장하면 profile_visibility에 PUBLIC이 채워진다")
    void savePersistsDefaultProfileVisibility() {
        // given -> profile_visibility는 NOT NULL이라 저장 시 값이 비면 안 된다
        User user = new User(
                UuidCreator.getTimeOrderedEpoch(), "local@example.com", PASSWORD_HASH
        );

        // when
        userPersistenceAdapter.save(user);

        // then
        ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
        verify(entityManager).persist(captor.capture());

        assertThat(captor.getValue().getProfileVisibility()).isEqualTo(ProfileVisibility.PUBLIC);
    }

    @Test
    @DisplayName("신규 유저를 저장하면 profile_image_key는 NULL로 저장된다")
    void savePersistsNullProfileImageKeyForNewUser() {
        // given -> 가입 시점에는 프로필 사진이 없다
        User user = new User(
                UuidCreator.getTimeOrderedEpoch(), "local@example.com", PASSWORD_HASH
        );

        // when
        userPersistenceAdapter.save(user);

        // then
        ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
        verify(entityManager).persist(captor.capture());

        assertThat(captor.getValue().getProfileImageKey()).isNull();
    }

    @Test
    @DisplayName("프로필 사진이 있는 유저를 저장하면 profile_image_key에 key가 채워진다")
    void savePersistsProfileImageKey() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        User user = new User(
                userId, "local@example.com", PASSWORD_HASH, false,
                profileImageKeyOf(userId), ProfileVisibility.PUBLIC, ""
        );

        // when
        userPersistenceAdapter.save(user);

        // then
        ArgumentCaptor<UserJpaEntity> captor = ArgumentCaptor.forClass(UserJpaEntity.class);
        verify(entityManager).persist(captor.capture());

        assertThat(captor.getValue().getProfileImageKey()).isEqualTo(profileImageKeyOf(userId));
    }

    @Test
    @DisplayName("소셜 연결이 없는 로컬 유저는 users 행만 저장한다")
    void saveLocalUserPersistsOnlyUserRow() {
        // given
        User user = new User(
                UuidCreator.getTimeOrderedEpoch(), "local@example.com", PASSWORD_HASH
        );

        // when
        userPersistenceAdapter.save(user);

        // then
        verify(entityManager, times(1)).persist(any(UserJpaEntity.class));
        verify(entityManager, times(0)).persist(any(OauthUserJpaEntity.class));
    }

    @Test
    @DisplayName("userId로 유저를 조회해 도메인 User로 변환한다")
    void loadByIdReturnsUser() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserJpaEntity entity = UserJpaEntity.create(
                userId, "test@example.com", PASSWORD_HASH, false, null, ProfileVisibility.PUBLIC, null
        );

        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(entity);

        // when
        Optional<User> result = userPersistenceAdapter.loadById(new UserId(userId));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getUserId().value()).isEqualTo(userId);
        assertThat(result.get().getEmail().value()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("userId에 해당하는 유저가 없으면 빈 Optional을 반환한다")
    void loadByIdReturnsEmpty() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(null);

        // when
        Optional<User> result = userPersistenceAdapter.loadById(new UserId(userId));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("온보딩 행이 있으면 true를 반환한다")
    void existsByUserIdReturnsTrue() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        givenCountQueryReturns("userId", userId, 1L);

        // when
        boolean result = userPersistenceAdapter.existsByUserId(new UserId(userId));

        // then -> 이 검사가 새면 이미 온보딩한 유저가 PK 중복으로 500을 받는다
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("온보딩 행이 없으면 false를 반환한다")
    void existsByUserIdReturnsFalse() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        givenCountQueryReturns("userId", userId, 0L);

        // when
        boolean result = userPersistenceAdapter.existsByUserId(new UserId(userId));

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("같은 닉네임이 있으면 true를 반환한다")
    void existsByNicknameReturnsTrue() {
        // given
        givenCountQueryReturns("nickname", "러너킴", 1L);

        // when
        boolean result = userPersistenceAdapter.existsByNickname(new Nickname("러너킴"));

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("닉네임 조회에는 정규화된 값이 넘어간다")
    void existsByNicknamePassesTrimmedValue() {
        // given -> VO가 trim한 값으로 조회해야 저장 값과 어긋나지 않는다
        givenCountQueryReturns("nickname", "러너킴", 0L);

        // when
        boolean result = userPersistenceAdapter.existsByNickname(new Nickname("  러너킴  "));

        // then
        assertThat(result).isFalse();
        verify(countQuery).setParameter("nickname", "러너킴");
    }

    @Test
    @DisplayName("온보딩을 저장하면 user_onboardings 행을 영속화한다")
    void saveOnboardingPersistsOnboardingRow() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        User user = new User(userId, "test@example.com", PASSWORD_HASH);
        user.completeOnboarding(
                "러너킴", "MALE", LocalDate.of(1999, 5, 20),
                330, new BigDecimal("70.5"), new BigDecimal("175.0")
        );
        UserOnboarding onboarding = user.getOnboarding().orElseThrow();

        // when
        userPersistenceAdapter.saveOnboarding(onboarding);

        // then -> VO가 껍질을 벗고 원시 값으로 내려가야 한다
        ArgumentCaptor<UserOnboardingJpaEntity> captor =
                ArgumentCaptor.forClass(UserOnboardingJpaEntity.class);
        verify(entityManager).persist(captor.capture());

        UserOnboardingJpaEntity entity = captor.getValue();
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getNickname()).isEqualTo("러너킴");
        assertThat(entity.getGender()).isEqualTo(Gender.MALE);
        assertThat(entity.getBirthday()).isEqualTo(LocalDate.of(1999, 5, 20));
        assertThat(entity.getAvgPace()).isEqualTo(330);
        assertThat(entity.getWeight()).isEqualByComparingTo("70.5");
        assertThat(entity.getHeight()).isEqualByComparingTo("175.0");
    }

    @Test
    @DisplayName("프로필 이미지 key를 바꾸면 조회한 엔티티에 반영한다")
    void updateProfileImageChangesEntity() {
        // given -> save()는 persist라 신규 전용이므로 갱신은 변경 감지로 처리한다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserJpaEntity entity = UserJpaEntity.create(
                userId, "runner@runiverse.com", PASSWORD_HASH, true,
                profileImageKeyOf(userId), ProfileVisibility.PUBLIC, ""
        );
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(entity);
        String newKey = "profiles/" + userId + "/019ffa54-917f-7477-9482-5792597ef3b0.jpg";

        // when
        userPersistenceAdapter.updateProfileImage(new UserId(userId), new ProfileImageKey(newKey));

        // then -> 별도 저장 호출 없이 엔티티 상태만 바꾼다
        assertThat(entity.getProfileImageKey()).isEqualTo(newKey);
    }

    @Test
    @DisplayName("사용자가 없으면 프로필 이미지를 바꾸지 않고 예외를 던진다")
    void updateProfileImageThrowsWhenUserNotFound() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> userPersistenceAdapter.updateProfileImage(
                new UserId(userId), new ProfileImageKey(profileImageKeyOf(userId))))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("소개글을 빈 문자열로 지우면 컬럼을 null로 비운다")
    void updateIntroductionConvertsEmptyToNull() {
        // given -> 도메인은 소개글 없음을 빈 문자열로 들고 있지만 컬럼에는 남기지 않는다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserJpaEntity entity = UserJpaEntity.create(
                userId, "runner@runiverse.com", PASSWORD_HASH, true,
                null, ProfileVisibility.PUBLIC, "즐겁게 달려요"
        );
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(entity);

        // when
        userPersistenceAdapter.updateIntroduction(new UserId(userId), new Introduction(""));

        // then -> 가입 시 save()와 같은 방식이라 "없음"의 표현이 하나로 유지된다
        assertThat(entity.getIntroduction()).isNull();
    }

    @Test
    @DisplayName("프로필 이미지를 지우면 엔티티의 key를 null로 만든다")
    void clearProfileImageRemovesKey() {
        // given -> S3 객체는 그대로 두고 DB의 연결만 끊는다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserJpaEntity entity = UserJpaEntity.create(
                userId, "runner@runiverse.com", PASSWORD_HASH, true,
                profileImageKeyOf(userId), ProfileVisibility.PUBLIC, ""
        );
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(entity);

        // when
        userPersistenceAdapter.clearProfileImage(new UserId(userId));

        // then -> 별도 저장 호출 없이 변경 감지로 반영한다
        assertThat(entity.getProfileImageKey()).isNull();
    }

    @Test
    @DisplayName("이미 이미지가 없어도 지우기는 그대로 성공한다")
    void clearProfileImageIsIdempotent() {
        // given -> profile_image_key가 이미 null인 사용자다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserJpaEntity entity = UserJpaEntity.create(
                userId, "runner@runiverse.com", PASSWORD_HASH, true,
                null, ProfileVisibility.PUBLIC, ""
        );
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(entity);

        // when
        userPersistenceAdapter.clearProfileImage(new UserId(userId));

        // then
        assertThat(entity.getProfileImageKey()).isNull();
    }

    @Test
    @DisplayName("사용자가 없으면 프로필 이미지를 지우지 않고 예외를 던진다")
    void clearProfileImageThrowsWhenUserNotFound() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> userPersistenceAdapter.clearProfileImage(new UserId(userId)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 엔티티의 해시가 갱신된다")
    void updatePasswordChangesEntity() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserJpaEntity entity = UserJpaEntity.create(
                userId, "runner@runiverse.com", PASSWORD_HASH, true,
                null, ProfileVisibility.PUBLIC, ""
        );
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(entity);
        String newHash = "$argon2id$v=19$m=16384,t=2,p=1$YW5vdGhlcnNhbHQ$bmV3aGFzaA";

        // when
        userPersistenceAdapter.updatePassword(new UserId(userId), new PasswordHash(newHash));

        // then -> 유니크 제약이 없어 flush 없이 변경 감지에 맡긴다
        assertThat(entity.getPasswordHash()).isEqualTo(newHash);
    }

    @Test
    @DisplayName("사용자가 없으면 비밀번호를 바꾸지 않고 예외를 던진다")
    void updatePasswordThrowsWhenUserNotFound() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> userPersistenceAdapter.updatePassword(
                new UserId(userId), new PasswordHash(PASSWORD_HASH)))
                .isInstanceOf(UserNotFoundException.class);
    }

    private void givenCountQueryReturns(String parameterName, Object value, long count) {
        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenReturn(countQuery);

        when(countQuery.setParameter(parameterName, value))
                .thenReturn(countQuery);

        when(countQuery.getSingleResult())
                .thenReturn(count);
    }

    private void givenProviderQueryReturns(String providerId, Stream<UserJpaEntity> found) {
        when(entityManager.createQuery(anyString(), eq(UserJpaEntity.class)))
                .thenReturn(userQuery);

        when(userQuery.setParameter("provider", Provider.KAKAO))
                .thenReturn(userQuery);

        when(userQuery.setParameter("providerId", providerId))
                .thenReturn(userQuery);

        when(userQuery.getResultStream())
                .thenReturn(found);
    }

    @Test
    @DisplayName("설정을 바꾸면 담겨 온 컬럼만 갱신한다")
    void updateSettingsChangesOnlyGivenColumns() {
        // given -> 알림만 바꾸고 공개 범위는 건드리지 않는 요청이다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserJpaEntity entity = UserJpaEntity.create(
                userId, "runner@runiverse.com", PASSWORD_HASH, true,
                null, ProfileVisibility.PUBLIC, ""
        );
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(entity);

        // when
        userPersistenceAdapter.updateSettings(new UserId(userId), false, null);

        // then -> 별도 저장 호출 없이 변경 감지로 반영한다
        assertThat(entity.isAlertConsent()).isFalse();
        assertThat(entity.getProfileVisibility()).isEqualTo(ProfileVisibility.PUBLIC);
    }

    @Test
    @DisplayName("두 값을 함께 넘기면 둘 다 갱신한다")
    void updateSettingsChangesBothColumns() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserJpaEntity entity = UserJpaEntity.create(
                userId, "runner@runiverse.com", PASSWORD_HASH, true,
                null, ProfileVisibility.PUBLIC, ""
        );
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(entity);

        // when
        userPersistenceAdapter.updateSettings(
                new UserId(userId), false, ProfileVisibility.FRIENDS);

        // then
        assertThat(entity.isAlertConsent()).isFalse();
        assertThat(entity.getProfileVisibility()).isEqualTo(ProfileVisibility.FRIENDS);
    }

    @Test
    @DisplayName("설정을 바꿀 사용자가 없으면 예외를 던진다")
    void updateSettingsThrowsWhenUserNotFound() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(entityManager.find(UserJpaEntity.class, userId)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> userPersistenceAdapter.updateSettings(
                new UserId(userId), false, null))
                .isInstanceOf(UserNotFoundException.class);
    }

    private void givenUserQueryReturns(String email, Stream<UserJpaEntity> found) {
        when(entityManager.createQuery(anyString(), eq(UserJpaEntity.class)))
                .thenReturn(userQuery);

        when(userQuery.setParameter("email", email))
                .thenReturn(userQuery);

        when(userQuery.getResultStream())
                .thenReturn(found);
    }

}
