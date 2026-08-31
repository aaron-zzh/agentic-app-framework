package com.xuejiai.aaf.framework.engine.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.xuejiai.aaf.framework.engine.cache.CacheInvalidationBroadcaster;
import com.xuejiai.aaf.framework.engine.cache.TwoLevelCache;
import com.xuejiai.aaf.framework.engine.cache.TwoLevelCacheFactory;
import com.xuejiai.aaf.framework.org.OrgIgnore;

/** 已发布 Prompt 的两级缓存；数据库仍是唯一真理。 */
@Component
@OrgIgnore
public class PromptVersionCache {

    private static final String ACTIVE_CACHE = "prompt_active_version";
    private static final String VERSION_CACHE = "prompt_exact_version";
    private static final int MAX_SIZE = 200;
    private static final Duration LOCAL_TTL = Duration.ofMinutes(5);
    private static final Duration REDIS_TTL = Duration.ofHours(24);

    private final TwoLevelCacheFactory cacheFactory;
    private final CacheInvalidationBroadcaster broadcaster;
    private final PromptTemplateRepository templateRepository;
    private final PromptTemplateVersionRepository versionRepository;
    private final TransactionTemplate transactionTemplate;

    private final TwoLevelCache<String, CachedPromptVersion> activeCache;
    private final TwoLevelCache<String, CachedPromptVersion> versionCache;

    public PromptVersionCache(
            TwoLevelCacheFactory cacheFactory,
            CacheInvalidationBroadcaster broadcaster,
            PromptTemplateRepository templateRepository,
            PromptTemplateVersionRepository versionRepository,
            PlatformTransactionManager transactionManager) {
        this.cacheFactory = cacheFactory;
        this.broadcaster = broadcaster;
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
        this.activeCache =
                cacheFactory.create(
                        ACTIVE_CACHE, CachedPromptVersion.class, MAX_SIZE, LOCAL_TTL, REDIS_TTL);
        this.versionCache =
                cacheFactory.create(
                        VERSION_CACHE, CachedPromptVersion.class, MAX_SIZE, LOCAL_TTL, REDIS_TTL);
    }

    public CachedPromptVersion requireActive(String code) {
        var cached = activeCache.get(code, this::loadActive);
        if (cached == null) throw new IllegalStateException("Prompt 未发布: " + code);
        verify(cached);
        return cached;
    }

    public CachedPromptVersion requireVersion(String code, int version) {
        var key = versionKey(code, version);
        var cached = versionCache.get(key, ignored -> loadVersion(code, version));
        if (cached == null)
            throw new IllegalStateException("Prompt 已发布版本不存在: %s@%d".formatted(code, version));
        verify(cached);
        return cached;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublished(PromptVersionPublishedEvent event) {
        var version = event.version();
        verify(version);
        versionCache.put(versionKey(version.code(), version.templateVersion()), version);
        activeCache.put(version.code(), version);
        broadcaster.broadcast(ACTIVE_CACHE, version.code());
    }

    CachedPromptVersion loadActive(String code) {
        return transactionTemplate.execute(
                status ->
                        templateRepository
                                .findByCodeAndDeletedFalse(code)
                                .map(PromptTemplate::getCurrentVersion)
                                .filter(
                                        version ->
                                                version.getStatus()
                                                        == PromptVersionStatus.PUBLISHED)
                                .filter(version -> !Boolean.TRUE.equals(version.getDeleted()))
                                .map(this::snapshot)
                                .orElse(null));
    }

    CachedPromptVersion loadVersion(String code, int version) {
        return transactionTemplate.execute(
                status ->
                        versionRepository
                                .findByTemplateCodeAndTemplateVersionAndStatusAndDeletedFalse(
                                        code, version, PromptVersionStatus.PUBLISHED)
                                .map(this::snapshot)
                                .orElse(null));
    }

    private CachedPromptVersion snapshot(PromptTemplateVersion version) {
        return new CachedPromptVersion(
                version.getTemplate().getCode(),
                version.getTemplate().getKind(),
                version.getTemplate().getVisibility(),
                version.getTemplateVersion(),
                version.getContent(),
                version.getNegativePrompt(),
                version.getVariables(),
                version.getContentHash());
    }

    private void verify(CachedPromptVersion version) {
        if (!sha256(version.content()).equals(version.contentHash())) {
            activeCache.invalidate(version.code());
            versionCache.invalidate(versionKey(version.code(), version.templateVersion()));
            throw new IllegalStateException("Prompt 缓存内容摘要不匹配: " + version.code());
        }
    }

    private static String versionKey(String code, int version) {
        return code + "@" + version;
    }

    private static String sha256(String content) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
