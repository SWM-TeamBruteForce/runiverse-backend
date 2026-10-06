package com.runiverse.running_service.infrastructure.persistence.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedAtEntity {

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
