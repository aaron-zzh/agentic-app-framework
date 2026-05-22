package com.xuejiai.aaf.framework.security.license;

import java.time.Instant;

/** 全局授权状态单例。启动时由 LicenseLoader 初始化一次，运行时只读。 */
public final class License {

    private static final License INSTANCE = new License();

    private volatile boolean premium = false;
    private volatile String userId = null;
    private volatile String tier = "free";
    private volatile Instant expiresAt = null;

    private License() {}

    public static License get() {
        return INSTANCE;
    }

    public boolean isPremium() {
        return premium;
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

    /** 仅启动时调用一次，设置授权信息。 */
    void activate(String userId, String tier, Instant expiresAt) {
        this.userId = userId;
        this.tier = tier;
        this.expiresAt = expiresAt;
        this.premium = true;
    }

    /** 重置为免费模式（测试用）。 */
    void reset() {
        this.premium = false;
        this.userId = null;
        this.tier = "free";
        this.expiresAt = null;
    }
}
