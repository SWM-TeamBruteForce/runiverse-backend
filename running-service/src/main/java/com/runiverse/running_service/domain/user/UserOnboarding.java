package com.runiverse.running_service.domain.user;

import com.runiverse.running_service.domain.common.exception.UserIdRequiredException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.AvgPace;
import com.runiverse.running_service.domain.user.vo.Birthday;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.Weight;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Getter
public class UserOnboarding {

    private final UserId userId;
    private final Nickname nickname;
    private final Gender gender;
    private final Birthday birthday;
    private final AvgPace avgPace;
    private final Weight weight;
    private final Height height;

    // 온보딩 완료
    UserOnboarding(UserId userId, String nickname, String gender, LocalDate birthday,
                   int avgPace, BigDecimal weight, BigDecimal height) {
        this(userId,
                new Nickname(nickname),
                Gender.from(gender),
                new Birthday(birthday),
                new AvgPace(avgPace),
                new Weight(weight),
                new Height(height));
    }

    // 내부 전용 - 수정 시 기존 VO를 넘기기 위해 필요
    private UserOnboarding(UserId userId, Nickname nickname, Gender gender, Birthday birthday,
                           AvgPace avgPace, Weight weight, Height height) {
        if (userId == null) {
            throw new UserIdRequiredException();
        }
        this.userId = userId;
        this.nickname = nickname;
        this.gender = gender;
        this.birthday = birthday;
        this.avgPace = avgPace;
        this.weight = weight;
        this.height = height;
    }

    // 프로필 수정
    UserOnboarding change(String nickname, String gender, LocalDate birthday,
                          Integer avgPace, BigDecimal weight, BigDecimal height) {
        return new UserOnboarding(
                userId,
                nickname != null ? new Nickname(nickname) : this.nickname,
                gender != null ? Gender.from(gender) : this.gender,
                birthday != null ? new Birthday(birthday) : this.birthday,
                avgPace != null ? new AvgPace(avgPace) : this.avgPace,
                weight != null ? new Weight(weight) : this.weight,
                height != null ? new Height(height) : this.height
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserOnboarding other)) {
            return false;
        }
        return userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
