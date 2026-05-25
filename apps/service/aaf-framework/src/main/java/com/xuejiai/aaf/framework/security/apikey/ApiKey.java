package com.xuejiai.aaf.framework.security.apikey;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * API Key 实体——用户的长期访问令牌（类似 GitHub PAT）。
 *
 * <p>Key 绑定用户，继承该用户的角色权限，可通过 scope 进一步限制。
 */
@Getter
@Setter
@Entity
@Table(name = "data_api_key")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Key 值（SHA-256 哈希存储，原文不落库） */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /** Key 前缀（用于展示，如 "aaf_dk_abc1..."） */
    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    /** 显示名称 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 关联用户 ID（该 Key 拥有此用户的权限） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 允许访问的表 slug 列表（逗号分隔，null 表示不限） */
    @Column(name = "allowed_tables", length = 1024)
    private String allowedTables;

    /** 权限范围（逗号分隔：ingest,read,write,admin） */
    @Column(name = "permissions", nullable = false, length = 64)
    private String permissions = "ingest,read";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return enabled && !isExpired();
    }

    public boolean hasScope(String scope) {
        return permissions != null && java.util.List.of(permissions.split(",")).contains(scope);
    }

    public boolean canAccessTable(String slug) {
        if (allowedTables == null || allowedTables.isBlank()) return true;
        return allowedTables.contains(slug);
    }
}
