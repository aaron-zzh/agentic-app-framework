package com.xuejiai.aaf.framework.security.license;

import java.time.Instant;
import java.util.Set;

/** 全局授权状态单例。启动时由 LicenseLoader 初始化一次，运行时只读。 */
public final class License {

    private static final License INSTANCE = new License();

    private volatile boolean premium = false;
    private volatile boolean owner = false;
    private volatile boolean identityValid = false;
    private volatile long couplingSeed = 0L;
    private volatile String userId = null;
    private volatile String tier = "free";
    private volatile Instant expiresAt = null;
    private volatile Set<String> features = Set.of();

    private License() {}

    public static License get() {
        return INSTANCE;
    }

    public boolean isPremium() {
        return premium;
    }

    public boolean isOwner() {
        return owner;
    }

    public boolean isIdentityValid() {
        return identityValid;
    }

    public long getCouplingSeed() {
        return couplingSeed;
    }

    public String getUserId() {
        return userId;
    }

    public String getTier() {
        return tier;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Set<String> getFeatures() {
        return features;
    }

    public boolean hasFeature(String feature) {
        return premium && (features.contains(feature) || features.contains("*"));
    }

    /** 仅启动时调用一次，设置授权信息。 */
    void activate(String userId, String tier, Instant expiresAt) {
        activate(userId, tier, expiresAt, false);
    }

    /** 仅启动时调用一次，设置授权信息。 */
    void activate(String userId, String tier, Instant expiresAt, boolean owner) {
        activate(userId, tier, expiresAt, owner, null);
    }

    /** 仅启动时调用一次，设置授权信息。 */
    void activate(
            String userId,
            String tier,
            Instant expiresAt,
            boolean owner,
            LicenseIdentityService identityService) {
        activate(userId, tier, expiresAt, owner, identityService, Set.of());
    }

    /** 仅启动时调用一次，设置授权信息。 */
    void activate(
            String userId,
            String tier,
            Instant expiresAt,
            boolean owner,
            LicenseIdentityService identityService,
            Set<String> features) {
        this.userId = userId;
        this.tier = tier;
        this.expiresAt = expiresAt;
        this.owner = owner;
        this.identityValid = identityService != null && identityService.isValid(userId);
        this.couplingSeed = identityService != null ? identityService.couplingSeed(userId) : 0L;
        this.features = features == null ? Set.of() : Set.copyOf(features);
        this.premium = true;
    }

    /** 重置为免费模式（测试用）。 */
    void reset() {
        this.premium = false;
        this.owner = false;
        this.identityValid = false;
        this.couplingSeed = 0L;
        this.userId = null;
        this.tier = "free";
        this.expiresAt = null;
        this.features = Set.of();
    }
}
