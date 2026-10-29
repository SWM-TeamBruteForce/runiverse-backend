package com.runiverse.running_service.unit_test.infrastructure.persistence.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.persistence.user.UserLockAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("유저 잠금 어댑터 단위 테스트")
public class UserLockAdapterTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private UserLockAdapter adapter;

    @Test
    @DisplayName("트랜잭션이 끝나면 풀리는 advisory lock을 UUID 상·하위 비트를 XOR한 키로 잡는다")
    void locksWithFoldedKey() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        long key = userId.getMostSignificantBits() ^ userId.getLeastSignificantBits();
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("key"), eq(key))).thenReturn(query);

        // when
        adapter.lock(new UserId(userId));

        // then -> 세션 단위 잠금(pg_advisory_lock)을 쓰면 커밋한 뒤에도 커넥션에 남는다
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).contains("pg_advisory_xact_lock(:key)");
        verify(query).getSingleResult();
    }
}
