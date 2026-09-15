package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.user.port.out.AccountSnapshot;
import com.runiverse.running_service.application.user.port.out.DeleteUserPort;
import com.runiverse.running_service.application.user.port.out.LoadAccountSnapshotPort;
import com.runiverse.running_service.application.user.port.out.LoadDeletedUserIdsPort;
import com.runiverse.running_service.application.user.port.out.RedactDeletedUserPort;
import com.runiverse.running_service.application.user.port.out.SaveDeletedUserPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.DeletedUser;
import com.runiverse.running_service.domain.user.vo.AvgPace;
import com.runiverse.running_service.domain.user.vo.Bmi;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.LoginType;
import com.runiverse.running_service.domain.user.vo.Nickname;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 탈퇴가 읽는 계정과 남기는 스냅샷을 한 곳에서 들고 있다 — 실제로도 한 어댑터가 같은 테이블들을 함께 다룬다
public class InMemoryDeletedUserStore implements LoadAccountSnapshotPort, SaveDeletedUserPort,
        DeleteUserPort, LoadDeletedUserIdsPort, RedactDeletedUserPort {

    // 스냅샷 한 건. email·nickname만 보관 기간이 끝나면 비워지므로 나머지는 final이다
    public static final class DeletedRow {

        private String email;
        private String nickname;
        private final Gender gender;
        private final Integer birthYear;
        private final Integer avgPace;
        private final BigDecimal bmi;
        private final LoginType loginType;
        private final LocalDateTime joinedAt;
        private final LocalDateTime createdAt;

        private DeletedRow(String email, String nickname, Gender gender, Integer birthYear,
                           Integer avgPace, BigDecimal bmi, LoginType loginType,
                           LocalDateTime joinedAt, LocalDateTime createdAt) {
            this.email = email;
            this.nickname = nickname;
            this.gender = gender;
            this.birthYear = birthYear;
            this.avgPace = avgPace;
            this.bmi = bmi;
            this.loginType = loginType;
            this.joinedAt = joinedAt;
            this.createdAt = createdAt;
        }

        public Optional<String> email() {
            return Optional.ofNullable(email);
        }

        public Optional<String> nickname() {
            return Optional.ofNullable(nickname);
        }

        public Optional<Gender> gender() {
            return Optional.ofNullable(gender);
        }

        public Optional<Integer> birthYear() {
            return Optional.ofNullable(birthYear);
        }

        public Optional<Integer> avgPace() {
            return Optional.ofNullable(avgPace);
        }

        public Optional<BigDecimal> bmi() {
            return Optional.ofNullable(bmi);
        }

        public LoginType loginType() {
            return loginType;
        }

        public LocalDateTime joinedAt() {
            return joinedAt;
        }

        public LocalDateTime createdAt() {
            return createdAt;
        }
    }

    private final Map<UUID, AccountSnapshot> accounts = new LinkedHashMap<>();
    private final Map<UUID, DeletedRow> deletedRows = new LinkedHashMap<>();
    // 스냅샷 시각. 보관 기간이 지난 상황을 만들려면 과거로 당길 수 있어야 한다
    private LocalDateTime snapshotAt = LocalDateTime.now();

    @Override
    public Optional<AccountSnapshot> loadAccountSnapshot(UserId userId) {
        return Optional.ofNullable(accounts.get(userId.value()));
    }

    @Override
    public void saveDeletedUser(DeletedUser deletedUser) {
        deletedRows.put(deletedUser.getUserId().value(), new DeletedRow(
                deletedUser.getEmail().value(),
                deletedUser.getNickname().map(Nickname::value).orElse(null),
                deletedUser.getGender().orElse(null),
                deletedUser.getBirthYear().orElse(null),
                deletedUser.getAvgPace().map(AvgPace::secondPerKm).orElse(null),
                deletedUser.getBmi().map(Bmi::value).orElse(null),
                deletedUser.getLoginType(),
                deletedUser.getJoinedAt(),
                snapshotAt
        ));
    }

    @Override
    public void deleteUser(UserId userId) {
        accounts.remove(userId.value());
    }

    // 실제 어댑터와 같은 조건 — email이 남은 행만 걸린다
    @Override
    public List<UserId> loadDeletedBefore(LocalDateTime deletedBefore) {
        return deletedRows.entrySet().stream()
                .filter(entry -> entry.getValue().createdAt.isBefore(deletedBefore))
                .filter(entry -> entry.getValue().email != null)
                .map(entry -> new UserId(entry.getKey()))
                .toList();
    }

    @Override
    public void redact(UserId userId) {
        DeletedRow row = deletedRows.get(userId.value());
        if (row == null) {
            return;
        }
        row.email = null;
        row.nickname = null;
    }

    // 준비 전용
    public void register(AccountSnapshot account) {
        accounts.put(account.userId(), account);
    }

    public void snapshotAt(LocalDateTime at) {
        snapshotAt = at;
    }

    // 검증 전용
    public Optional<AccountSnapshot> findAccount(UUID userId) {
        return Optional.ofNullable(accounts.get(userId));
    }

    public Optional<DeletedRow> findDeleted(UUID userId) {
        return Optional.ofNullable(deletedRows.get(userId));
    }

    public int deletedCount() {
        return deletedRows.size();
    }
}
