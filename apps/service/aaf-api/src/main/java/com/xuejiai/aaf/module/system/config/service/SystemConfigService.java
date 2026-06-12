package com.xuejiai.aaf.module.system.config.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.config.domain.SystemConfig;
import com.xuejiai.aaf.module.system.config.repository.SystemConfigRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统配置服务
 *
 * <p>读取优先级：Redis 缓存 → 数据库 → defaultValue
 *
 * <p>写入时自动清除缓存。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final String CACHE_PREFIX = "sys:config:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final SystemConfigRepository configRepository;
    private final StringRedisTemplate redisTemplate;

    // ── 读取 ──────────────────────────────────────────────────

    public String getString(String key) {
        return getValue(key);
    }

    public String getString(String key, String defaultVal) {
        var v = getValue(key);
        return v != null ? v : defaultVal;
    }

    public Integer getInteger(String key) {
        var v = getValue(key);
        return v != null ? Integer.parseInt(v) : null;
    }

    public Integer getInteger(String key, int defaultVal) {
        var v = getValue(key);
        return v != null ? Integer.parseInt(v) : defaultVal;
    }

    public Boolean getBoolean(String key) {
        var v = getValue(key);
        return v != null ? Boolean.parseBoolean(v) : null;
    }

    public Boolean getBoolean(String key, boolean defaultVal) {
        var v = getValue(key);
        return v != null ? Boolean.parseBoolean(v) : defaultVal;
    }

    public Double getDouble(String key, double defaultVal) {
        var v = getValue(key);
        try {
            return v != null ? Double.parseDouble(v) : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    // ── 写入 ──────────────────────────────────────────────────

    @Transactional
    public void set(String key, String value) {
        var config =
                configRepository
                        .findByConfigKeyAndDeletedFalse(key)
                        .orElseThrow(() -> new IllegalArgumentException("配置项不存在: " + key));
        if (!config.getEditable()) {
            throw new IllegalStateException("配置项不可编辑: " + key);
        }
        config.setValue(value);
        configRepository.save(config);
        evictCache(key);
    }

    // ── 管理 ──────────────────────────────────────────────────

    public List<SystemConfig> listByCategory(String category) {
        return configRepository.findByCategoryAndDeletedFalse(category);
    }

    public Optional<SystemConfig> findByKey(String key) {
        return configRepository.findByConfigKeyAndDeletedFalse(key);
    }

    /**
     * 创建配置项。
     *
     * @param dto 创建请求
     * @return 新建配置
     */
    @Transactional
    public com.xuejiai.aaf.module.system.config.vo.SystemConfigVO create(
            com.xuejiai.aaf.module.system.config.vo.SystemConfigCreateDTO dto) {
        var config = new SystemConfig();
        config.setCategory(dto.category());
        config.setConfigKey(dto.configKey());
        config.setValue(dto.value());
        config.setDefaultValue(dto.defaultValue());
        config.setValueType(dto.valueType() != null ? dto.valueType() : "string");
        config.setName(dto.name());
        config.setDescription(dto.description());
        config.setVisible(dto.visible() != null ? dto.visible() : true);
        config.setEditable(dto.editable() != null ? dto.editable() : true);
        return toVO(configRepository.save(config));
    }

    /**
     * 删除配置项。
     *
     * @param id 配置 ID
     */
    @Transactional
    public void delete(Long id) {
        var config =
                configRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("配置项不存在"));
        evictCache(config.getConfigKey());
        configRepository.deleteById(id);
    }

    public com.xuejiai.aaf.module.system.config.vo.SystemConfigVO toVO(SystemConfig c) {
        // 敏感配置不返回 value
        var value = Boolean.TRUE.equals(c.getVisible()) ? c.getValue() : null;
        return new com.xuejiai.aaf.module.system.config.vo.SystemConfigVO(
                c.getId(),
                c.getCategory(),
                c.getConfigKey(),
                value,
                c.getDefaultValue(),
                c.getValueType(),
                c.getName(),
                c.getDescription(),
                c.getVisible(),
                c.getEditable(),
                c.getUpdateTime());
    }

    // ── 内部 ──────────────────────────────────────────────────

    private String getValue(String key) {
        // 1. Redis 缓存
        var cached = redisTemplate.opsForValue().get(CACHE_PREFIX + key);
        if (cached != null) return "".equals(cached) ? null : cached;

        // 2. 数据库
        var config = configRepository.findByConfigKeyAndDeletedFalse(key).orElse(null);
        if (config == null) return null;

        var value = config.getValue() != null ? config.getValue() : config.getDefaultValue();
        redisTemplate.opsForValue().set(CACHE_PREFIX + key, value != null ? value : "", CACHE_TTL);
        return value;
    }

    private void evictCache(String key) {
        redisTemplate.delete(CACHE_PREFIX + key);
    }

    /** 手动刷新指定配置的缓存（DBA 直接改库后使用） */
    public void evict(String key) {
        evictCache(key);
    }

    /** 刷新所有配置缓存 */
    public void evictAll() {
        var keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
