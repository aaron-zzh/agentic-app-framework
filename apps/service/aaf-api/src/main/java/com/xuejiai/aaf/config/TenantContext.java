package com.xuejiai.aaf.config;

/** 租户上下文，存储当前请求的组织 ID。 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_ORG_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentOrgId(Long orgId) {
        CURRENT_ORG_ID.set(orgId);
    }

    public static Long getCurrentOrgId() {
        return CURRENT_ORG_ID.get();
    }

    public static void clear() {
        CURRENT_ORG_ID.remove();
    }
}
