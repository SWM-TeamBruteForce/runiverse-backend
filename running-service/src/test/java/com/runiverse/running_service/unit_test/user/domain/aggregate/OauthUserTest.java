package com.runiverse.running_service.domain.user;

import com.runiverse.running_service.domain.user.OauthUser;
import com.runiverse.running_service.domain.user.exception.ProviderIdRequiredException;
import com.runiverse.running_service.domain.user.exception.ProviderRequiredException;
import com.runiverse.running_service.domain.common.exception.UserIdRequiredException;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// OauthUser의 생성자가 package-private 이라 같은 패키지에 둔다
public class OauthUserTest {

    private static final UserId USER_ID =
            new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-123456789abc"));
    private static final UserId OTHER_USER_ID =
            new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-cba987654321"));
    private static final String KAKAO_ID = "3812345678";

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("소셜 연결을 생성할 수 있다")
        void createOauthUserSuccess() {
            // when
            OauthUser oauthUser = new OauthUser(USER_ID, Provider.KAKAO, KAKAO_ID);

            // then
            assertThat(oauthUser.getUserId()).isEqualTo(USER_ID);
            assertThat(oauthUser.getProvider()).isEqualTo(Provider.KAKAO);
            assertThat(oauthUser.getProviderId().value()).isEqualTo(KAKAO_ID);
        }

        @Test
        @DisplayName("providerId의 앞뒤 공백은 VO에서 제거된다")
        void createOauthUserTrimsProviderId() {
            // when
            OauthUser oauthUser = new OauthUser(USER_ID, Provider.KAKAO, "  " + KAKAO_ID + "  ");

            // then
            assertThat(oauthUser.getProviderId().value()).isEqualTo(KAKAO_ID);
        }

        @Test
        @DisplayName("userId가 null이면 예외가 발생한다")
        void createOauthUserWithNullUserIdFails() {
            // when & then
            assertThatThrownBy(() -> new OauthUser(null, Provider.KAKAO, KAKAO_ID))
                    .isInstanceOf(UserIdRequiredException.class);
        }

        @Test
        @DisplayName("provider가 null이면 예외가 발생한다")
        void createOauthUserWithNullProviderFails() {
            // when & then
            assertThatThrownBy(() -> new OauthUser(USER_ID, null, KAKAO_ID))
                    .isInstanceOf(ProviderRequiredException.class);
        }

        @Test
        @DisplayName("providerId가 null이면 예외가 발생한다")
        void createOauthUserWithNullProviderIdFails() {
            // when & then -> ProviderId VO가 검증한다
            assertThatThrownBy(() -> new OauthUser(USER_ID, Provider.KAKAO, null))
                    .isInstanceOf(ProviderIdRequiredException.class);
        }
    }

    @Nested
    @DisplayName("동등성 테스트")
    class EqualityTest {

        @Test
        @DisplayName("userId가 같으면 providerId가 달라도 같은 엔티티이다")
        void sameIdentityMeansEqual() {
            // given -> Entity의 동등성은 식별자(user_id)로만 판단한다
            OauthUser one = new OauthUser(USER_ID, Provider.KAKAO, "111");
            OauthUser another = new OauthUser(USER_ID, Provider.KAKAO, "222");

            // when & then
            assertThat(one).isEqualTo(another);
            assertThat(one).hasSameHashCodeAs(another);
        }

        @Test
        @DisplayName("userId가 같으면 provider가 달라도 같은 엔티티이다")
        void sameUserIdMeansEqualRegardlessOfProvider() {
            // given -> PK가 user_id 단독이라 유저당 oauth_user는 1건, provider는 식별에 관여하지 않는다
            OauthUser kakao = new OauthUser(USER_ID, Provider.KAKAO, KAKAO_ID);
            OauthUser google = new OauthUser(USER_ID, Provider.GOOGLE, KAKAO_ID);

            // when & then
            assertThat(kakao).isEqualTo(google);
            assertThat(kakao).hasSameHashCodeAs(google);
        }

        @Test
        @DisplayName("userId가 다르면 다른 엔티티이다")
        void differentUserIdMeansNotEqual() {
            // given
            OauthUser mine = new OauthUser(USER_ID, Provider.KAKAO, KAKAO_ID);
            OauthUser others = new OauthUser(OTHER_USER_ID, Provider.KAKAO, KAKAO_ID);

            // when & then
            assertThat(mine).isNotEqualTo(others);
        }

        @Test
        @DisplayName("null이나 다른 타입과는 같지 않다")
        void notEqualToNullOrOtherType() {
            // given
            OauthUser oauthUser = new OauthUser(USER_ID, Provider.KAKAO, KAKAO_ID);

            // when & then
            assertThat(oauthUser).isNotEqualTo(null);
            assertThat(oauthUser).isNotEqualTo("not an OauthUser");
        }
    }

    @Nested
    @DisplayName("isSameProvider 테스트")
    class IsSameProviderTest {

        @Test
        @DisplayName("같은 provider면 true를 반환한다")
        void returnsTrueForSameProvider() {
            // given
            OauthUser oauthUser = new OauthUser(USER_ID, Provider.KAKAO, KAKAO_ID);

            // when & then
            assertThat(oauthUser.isSameProvider(Provider.KAKAO)).isTrue();
        }

        @Test
        @DisplayName("다른 provider면 false를 반환한다")
        void returnsFalseForDifferentProvider() {
            // given
            OauthUser oauthUser = new OauthUser(USER_ID, Provider.KAKAO, KAKAO_ID);

            // when & then
            assertThat(oauthUser.isSameProvider(Provider.GOOGLE)).isFalse();
        }
    }
}
