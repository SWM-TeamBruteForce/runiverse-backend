package com.runiverse.running_service.infrastructure.persistence.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 생성 후 수정되지 않는 테이블용 — 수정 시각이 필요하면 BaseTimeEntity를 상속한다
@Getter
@MappedSuperclass
public abstract class BaseCreatedAtEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
