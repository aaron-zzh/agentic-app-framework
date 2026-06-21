package com.xuejiai.aaf.framework.security.apikey;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

/**
 * API Key 管理接口。
 *
 * <ul>
 *   <li>用户：在个人主页生成/查看/删除自己的 Key
 *   <li>管理员：查看所有 Key、禁用/启用
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyRepository repository;
    private final OperatorContext operatorContext;

    // ========== 用户接口 ==========

    /** 生成 API Key（当前用户）。 */
    @PostMapping
    public Map<String, String> create(@RequestBody CreateRequest req) {
        var userId = operatorContext.currentUserId().orElseThrow();
        var rawKey = "aaf_dk_" + UUID.randomUUID().toString().replace("-", "");

        var apiKey = new ApiKey();
        apiKey.setKeyHash(ApiKeyAuthFilter.sha256(rawKey));
        apiKey.setKeyPrefix(rawKey.substring(0, 14) + "...");
        apiKey.setName(req.name());
        apiKey.setUserId(userId);
        apiKey.setPermissions(req.permissions() != null ? req.permissions() : "ingest,read");
        apiKey.setAllowedTables(req.allowedTables());
        if (req.expiresInDays() != null) {
            apiKey.setExpiresAt(Instant.now().plusSeconds(req.expiresInDays() * 86400L));
        }
        repository.save(apiKey);

        return Map.of("key", rawKey, "prefix", apiKey.getKeyPrefix(), "name", req.name());
    }

    /** 列出当前用户的 Key。 */
    @GetMapping
    public List<ApiKeyVO> listMine() {
        var userId = operatorContext.currentUserId().orElseThrow();
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toVO)
                .toList();
    }

    /** 删除自己的 Key。 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        var userId = operatorContext.currentUserId().orElseThrow();
        var key = repository.findById(id).orElseThrow();
        if (!key.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除");
        }
        repository.delete(key);
    }

    // ========== 管理员接口 ==========

    /** 管理员：查看所有 Key。 */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<ApiKeyVO> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toVO).toList();
    }

    /** 管理员：禁用 Key。 */
    @PostMapping("/admin/{id}/disable")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public void disable(@PathVariable Long id) {
        var key = repository.findById(id).orElseThrow();
        key.setEnabled(false);
        repository.save(key);
    }

    /** 管理员：启用 Key。 */
    @PostMapping("/admin/{id}/enable")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public void enable(@PathVariable Long id) {
        var key = repository.findById(id).orElseThrow();
        key.setEnabled(true);
        repository.save(key);
    }

    // ========== DTO ==========

    record CreateRequest(
            String name, String permissions, String allowedTables, Integer expiresInDays) {}

    record ApiKeyVO(
            Long id,
            String prefix,
            String name,
            Long userId,
            String permissions,
            Instant createdAt,
            Instant expiresAt,
            boolean enabled,
            Instant lastUsedAt) {}

    private ApiKeyVO toVO(ApiKey k) {
        return new ApiKeyVO(
                k.getId(),
                k.getKeyPrefix(),
                k.getName(),
                k.getUserId(),
                k.getPermissions(),
                k.getCreatedAt(),
                k.getExpiresAt(),
                k.isEnabled(),
                k.getLastUsedAt());
    }
}
