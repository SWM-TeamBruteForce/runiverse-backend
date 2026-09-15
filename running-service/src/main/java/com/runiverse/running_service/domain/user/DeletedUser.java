package com.runiverse.running_service.domain.user;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.exception.GenderRequiredException;
import com.runiverse.running_service.domain.user.exception.JoinedAtRequiredException;
import com.runiverse.running_service.domain.user.exception.LoginTypeRequiredException;
import com.runiverse.running_service.domain.user.exception.OnboardingSnapshotIncompleteException;
import com.runiverse.running_service.domain.user.vo.AvgPace;
import com.runiverse.running_service.domain.user.vo.Birthday;
import com.runiverse.running_service.domain.user.vo.Bmi;
import com.runiverse.running_service.domain.user.vo.Email;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.LoginType;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.Weight;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Getter
public class DeletedUser {

    private final UserId userId;
    private final Email email;
    // 온보딩 스냅샷 — 다섯이 함께 채워지고 함께 빈다
    private final Nickname nickname;
    private final Gender gender;
    private final Integer birthYear;
    private final AvgPace avgPace;
    private final Bmi bmi;
    private final LoginType loginType;
    private final LocalDateTime joinedAt;

    // 온보딩 값은 VO로 받는다 — 미완료 탈퇴가 null을 넘긴다
    private DeletedUser(UUID userId, String email,
                        Nickname nickname, Gender gender, Integer birthYear,
                        AvgPace avgPace, Bmi bmi,
                        LoginType loginType, LocalDateTime joinedAt) {
        this.userId = new UserId(userId);
        this.email = new Email(email);
        // 온보딩 전 탈퇴면 다섯이 모두 비고, 완료 후면 모두 찬다. 섞인 상태는 통계를 망친다
        requireSnapshotAllOrNothing(nickname, gender, birthYear, avgPace, bmi);
        this.nickname = nickname;
        this.gender = gender;
        this.birthYear = birthYear;
        this.avgPace = avgPace;
        this.bmi = bmi;
        if (loginType == null) {
            throw new LoginTypeRequiredException();
        }
        this.loginType = loginType;
        if (joinedAt == null) {
            throw new JoinedAtRequiredException();
        }
        this.joinedAt = joinedAt;
    }

    private static void requireSnapshotAllOrNothing(Nickname nickname, Gender gender,
                                                    Integer birthYear, AvgPace avgPace, Bmi bmi) {
        boolean anyPresent = nickname != null || gender != null || birthYear != null
                || avgPace != null || bmi != null;
        boolean allPresent = nickname != null && gender != null && birthYear != null
                && avgPace != null && bmi != null;
        if (anyPresent && !allPresent) {
            throw new OnboardingSnapshotIncompleteException();
        }
    }

    // 체중·신장은 BMI로, 생년월일은 연도만 남겨 식별성을 낮춘다
    public static DeletedUser ofOnboarded(UUID userId, String email,
                                          String nickname, Gender gender, LocalDate birthday,
                                          int avgPace, BigDecimal weight, BigDecimal height,
                                          LoginType loginType, LocalDateTime joinedAt) {
        // 나머지 넷은 VO 생성자가 막는다 — enum이라 여기서 본다
        if (gender == null) {
            throw new GenderRequiredException();
        }
        return new DeletedUser(userId, email,
                new Nickname(nickname),
                gender,
                new Birthday(birthday).value().getYear(),
                new AvgPace(avgPace),
                Bmi.from(new Weight(weight), new Height(height)),
                loginType, joinedAt);
    }

    // 온보딩 전에 탈퇴 — 가입 수단과 체류 기간만 남는다
    public static DeletedUser ofNotOnboarded(UUID userId, String email,
                                             LoginType loginType, LocalDateTime joinedAt) {
        return new DeletedUser(userId, email, null, null, null, null, null, loginType, joinedAt);
    }

    public Optional<Nickname> getNickname() {
        return Optional.ofNullable(nickname);
    }

    public Optional<Gender> getGender() {
        return Optional.ofNullable(gender);
    }

    public Optional<Integer> getBirthYear() {
        return Optional.ofNullable(birthYear);
    }

    public Optional<AvgPace> getAvgPace() {
        return Optional.ofNullable(avgPace);
    }

    public Optional<Bmi> getBmi() {
        return Optional.ofNullable(bmi);
    }
}
