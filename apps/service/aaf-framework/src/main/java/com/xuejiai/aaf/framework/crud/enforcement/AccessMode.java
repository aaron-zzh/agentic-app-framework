package com.xuejiai.aaf.framework.crud.enforcement;

/** CRUD 安全执行模式；非默认模式必须经过专用权限校验且始终保留租户范围。 */
public enum AccessMode {
    DEFAULT("default", false, false),
    ADMIN_MAINTENANCE("admin-maintenance", true, true),
    SYSTEM_JOB("system-job", true, true);

    private final String permissionSegment;
    private final boolean bypassRecordScope;
    private final boolean bypassPersonalScope;

    AccessMode(
            String permissionSegment,
            boolean bypassRecordScope,
            boolean bypassPersonalScope) {
        this.permissionSegment = permissionSegment;
        this.bypassRecordScope = bypassRecordScope;
        this.bypassPersonalScope = bypassPersonalScope;
    }

    public String permissionSegment() {
        return permissionSegment;
    }

    public boolean bypassesRecordScope() {
        return bypassRecordScope;
    }

    public boolean bypassesPersonalScope() {
        return bypassPersonalScope;
    }
}
