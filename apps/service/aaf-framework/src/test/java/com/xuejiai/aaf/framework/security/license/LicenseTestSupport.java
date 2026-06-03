package com.xuejiai.aaf.framework.security.license;

import java.time.Instant;

/** 测试辅助类，暴露 License 的 package-private 方法供跨包测试使用。 */
public final class LicenseTestSupport {

    private LicenseTestSupport() {}

    public static void reset() {
        License.get().reset();
    }

    public static void activate(String userId, String tier, Instant expiresAt) {
        License.get()
                .activate(
                        userId,
                        tier,
                        expiresAt,
                        false,
                        new LicenseIdentityService(new LicenseIdentityProperties()) {
                            @Override
                            public boolean isValid(String uid) {
                                return true;
                            }

                            @Override
                            public long couplingSeed(String uid) {
                                return uid == null ? 0L : (long) uid.hashCode();
                            }
                        });
    }
}
