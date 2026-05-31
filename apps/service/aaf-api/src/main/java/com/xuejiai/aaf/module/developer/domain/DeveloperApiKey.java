package com.xuejiai.aaf.module.developer.domain;

import java.time.Instant;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 开发者调用 AAF Model Gateway 的 API Key。 */
@Getter
@Setter
@Entity
@Table(name = "developer_api_key")
@SQLDelete(
        sql =
                "UPDATE developer_api_key SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class DeveloperApiKey extends BaseEntity {

    @Column(name = "developer_id", nullable = false)
    private Long developerId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 24)
    private String keyPrefix;

    @Column(name = "scopes", length = 255)
    private String scopes = "gateway:chat";

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public boolean isValid() {
        return Boolean.TRUE.equals(enabled) && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
