package com.xuejiai.aaf.module.system.file.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_CONFIG_NOT_FOUND;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_STORAGE_MASTER_NOT_FOUND;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_STORAGE_PUBLIC_ASSET_NOT_FOUND;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.storage.LocalStorageSpec;
import com.xuejiai.aaf.framework.storage.OssStorageSpec;
import com.xuejiai.aaf.framework.storage.S3StorageSpec;
import com.xuejiai.aaf.framework.storage.StorageClient;
import com.xuejiai.aaf.framework.storage.StorageType;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;
import com.xuejiai.aaf.module.system.file.repository.FileConfigRepository;

/** 按数据库配置路由对象存储；配置短时缓存、客户端由注册表按版本复用。 */
@Service
public class StorageRouter {

    private static final long MASTER_CACHE_KEY = 0L;
    private static final long PUBLIC_ASSET_CACHE_KEY = -1L;
    private static final Duration CONFIG_REFRESH_INTERVAL = Duration.ofSeconds(10);

    private final FileConfigRepository fileConfigRepository;
    private final StorageClientRegistry registry;
    private final LoadingCache<Long, CachedStorageConfig> configCache;

    public StorageRouter(
            FileConfigRepository fileConfigRepository, StorageClientRegistry registry) {
        this.fileConfigRepository = fileConfigRepository;
        this.registry = registry;
        this.configCache =
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .refreshAfterWrite(CONFIG_REFRESH_INTERVAL)
                        .build(this::loadConfig);
    }

    public ResolvedStorage currentMaster() {
        return resolved(configCache.get(MASTER_CACHE_KEY));
    }

    /** 解析唯一启用且允许公开访问的 OSS 配置。 */
    public ResolvedStorage publicAsset() {
        return resolved(configCache.get(PUBLIC_ASSET_CACHE_KEY));
    }

    public ResolvedStorage byConfigId(Long storageConfigId) {
        if (storageConfigId == null || storageConfigId <= 0) {
            throw new IllegalArgumentException("storageConfigId 不能为空");
        }
        return resolved(configCache.get(storageConfigId));
    }

    /** 配置提交后清理当前节点缓存；用途路由键一并失效，避免返回旧路由。 */
    public void invalidate(Long storageConfigId) {
        configCache.invalidate(storageConfigId);
        configCache.invalidate(MASTER_CACHE_KEY);
        configCache.invalidate(PUBLIC_ASSET_CACHE_KEY);
        registry.invalidate(storageConfigId);
    }

    public void invalidateMaster() {
        configCache.invalidate(MASTER_CACHE_KEY);
    }

    private CachedStorageConfig loadConfig(Long cacheKey) {
        var config =
                cacheKey == MASTER_CACHE_KEY
                        ? fileConfigRepository
                                .findByMasterTrue()
                                .orElseThrow(() -> exception(FILE_STORAGE_MASTER_NOT_FOUND))
                        : cacheKey == PUBLIC_ASSET_CACHE_KEY
                                ? requirePublicAssetConfig()
                                : fileConfigRepository
                                        .findById(cacheKey)
                                        .orElseThrow(() -> exception(FILE_CONFIG_NOT_FOUND));
        return cached(config);
    }

    private FileConfig requirePublicAssetConfig() {
        var candidates =
                fileConfigRepository.findAll().stream()
                        .filter(config -> StorageType.OSS.name().equals(config.getStorageType()))
                        .filter(this::isPublicOss)
                        .toList();
        if (candidates.size() != 1) {
            throw exception(FILE_STORAGE_PUBLIC_ASSET_NOT_FOUND);
        }
        return candidates.getFirst();
    }

    private boolean isPublicOss(FileConfig config) {
        var spec = JsonUtils.parseObject(config.getConfig(), OssStorageSpec.class);
        return spec.publicAccessEnabled();
    }

    private CachedStorageConfig cached(FileConfig config) {
        var storageType = StorageType.valueOf(config.getStorageType());
        return switch (storageType) {
            case LOCAL -> {
                var spec = JsonUtils.parseObject(config.getConfig(), LocalStorageSpec.class);
                yield new CachedStorageConfig(
                        config, storageType, spec.domain(), false, Duration.ZERO);
            }
            case OSS -> {
                var spec = JsonUtils.parseObject(config.getConfig(), OssStorageSpec.class);
                yield new CachedStorageConfig(
                        config,
                        storageType,
                        spec.domainOrDefault(),
                        spec.publicAccessEnabled(),
                        spec.downloadUrlExpiry());
            }
            case S3 -> {
                var spec = JsonUtils.parseObject(config.getConfig(), S3StorageSpec.class);
                yield new CachedStorageConfig(
                        config,
                        storageType,
                        spec.domainOrDefault(),
                        spec.publicAccessEnabled(),
                        spec.downloadUrlExpiry());
            }
        };
    }

    private ResolvedStorage resolved(CachedStorageConfig cached) {
        return new ResolvedStorage(
                cached.config().getId(),
                cached.storageType(),
                cached.domain(),
                cached.publicAccess(),
                cached.downloadUrlExpiry(),
                registry.resolve(cached.config()));
    }

    private record CachedStorageConfig(
            FileConfig config,
            StorageType storageType,
            String domain,
            boolean publicAccess,
            Duration downloadUrlExpiry) {}

    public record ResolvedStorage(
            Long storageConfigId,
            StorageType storageType,
            String domain,
            boolean publicAccess,
            Duration downloadUrlExpiry,
            StorageClient client) {}
}
