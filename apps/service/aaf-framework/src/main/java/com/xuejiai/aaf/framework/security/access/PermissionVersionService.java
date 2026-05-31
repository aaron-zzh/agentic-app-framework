package com.xuejiai.aaf.framework.security.access;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 权限版本服务。版本进入缓存 key，用于权限变更时快速失效旧授权结果。 */
@Service
@RequiredArgsConstructor
public class PermissionVersionService {

    private static final String PERMISSION_VERSION = "access_version:permission";
    private static final String RELATION_SCHEMA_VERSION = "access_version:relation_schema";
    private static final String RULE_VERSION_PREFIX = "access_version:rule:";
    private static final String POLICY_VERSION = "access_version:policy";

    private final StringRedisTemplate redisTemplate;

    public String permissionVersion() {
        return version(PERMISSION_VERSION);
    }

    public String relationSchemaVersion() {
        return version(RELATION_SCHEMA_VERSION);
    }

    public String ruleVersion(String entitySlug) {
        return version(RULE_VERSION_PREFIX + entitySlug);
    }

    public String policyVersion() {
        return version(POLICY_VERSION);
    }

    public void bumpPermissionVersion() {
        bump(PERMISSION_VERSION);
    }

    public void bumpRelationSchemaVersion() {
        bump(RELATION_SCHEMA_VERSION);
    }

    public void bumpRuleVersion(String entitySlug) {
        bump(RULE_VERSION_PREFIX + entitySlug);
    }

    public void bumpPolicyVersion() {
        bump(POLICY_VERSION);
    }

    private String version(String key) {
        var value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            return value;
        }
        redisTemplate.opsForValue().setIfAbsent(key, "1");
        return "1";
    }

    private void bump(String key) {
        redisTemplate.opsForValue().increment(key);
    }
}
