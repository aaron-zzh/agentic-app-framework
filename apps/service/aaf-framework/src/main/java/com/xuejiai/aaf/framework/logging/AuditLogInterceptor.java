package com.xuejiai.aaf.framework.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;

/**
 * 审计日志事件记录。
 *
 * <p>通过 Spring 事件发布审计变更，由业务层异步持久化。 注意：JPA EntityListener 不支持直接注入 Spring Bean，
 * 需通过 {@link AuditLogInterceptorHelper} 静态引用获取 publisher。
 */
@Slf4j
public class AuditLogInterceptor {

    @PostPersist
    public void onInsert(Object entity) {
        if (!isAuditable(entity)) return;
        publishAuditEvent(entity, "INSERT", null, entityToMap(entity));
    }

    @PostUpdate
    public void onUpdate(Object entity) {
        if (!isAuditable(entity)) return;
        // PostUpdate 无法获取旧值，变更详情由业务层 EntityChangeEvent 补充
        publishAuditEvent(entity, "UPDATE", null, entityToMap(entity));
    }

    @PostRemove
    public void onDelete(Object entity) {
        if (!isAuditable(entity)) return;
        publishAuditEvent(entity, "DELETE", entityToMap(entity), null);
    }

    private boolean isAuditable(Object entity) {
        return entity.getClass().isAnnotationPresent(Auditable.class);
    }

    private Set<String> getExcludeFields(Object entity) {
        var annotation = entity.getClass().getAnnotation(Auditable.class);
        return annotation != null ? Set.of(annotation.excludeFields()) : Set.of();
    }

    private Map<String, Object> entityToMap(Object entity) {
        var excludes = getExcludeFields(entity);
        var map = new HashMap<String, Object>();
        for (var field : entity.getClass().getDeclaredFields()) {
            if (excludes.contains(field.getName())) continue;
            field.setAccessible(true);
            try {
                map.put(field.getName(), field.get(entity));
            } catch (IllegalAccessException e) {
                // 忽略不可访问字段
            }
        }
        return map;
    }

    private void publishAuditEvent(Object entity, String action, Map<String, Object> before, Map<String, Object> after) {
        var publisher = AuditLogInterceptorHelper.getPublisher();
        if (publisher == null) {
            log.debug("ApplicationEventPublisher 未就绪，跳过审计记录");
            return;
        }
        var entityType = entity.getClass().getSimpleName();
        Long entityId = extractId(entity);
        publisher.publishEvent(new AuditChangeEvent(entityType, entityId, action, before, after, LocalDateTime.now()));
    }

    private Long extractId(Object entity) {
        try {
            var idField = entity.getClass().getSuperclass() != null
                    ? findIdField(entity.getClass())
                    : null;
            if (idField != null) {
                idField.setAccessible(true);
                var val = idField.get(entity);
                return val instanceof Long l ? l : null;
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }

    private java.lang.reflect.Field findIdField(Class<?> clazz) {
        while (clazz != null) {
            for (var f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    return f;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /** 计算内容 SHA-256 哈希（用于链式校验）。 */
    public static String computeHash(String previousHash, String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var input = (previousHash != null ? previousHash : "") + content;
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }
}
