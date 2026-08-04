package com.xuejiai.aaf.framework.org;

/**
 * 组织上下文，存储当前请求/任务的组织 ID、工作区 ID与全局读取状态。
 *
 * <p>下沉到 {@code aaf-framework}（而非 {@code aaf-api}），使 framework 层的定时任务等场景也能 读取/设置组织上下文、声明豁免组织过滤（见
 * {@link OrgIgnore}）。
 *
 * <p>workspaceId 与 orgId 是两个独立维度（组织下可有多个工作区），但共用同一个上下文类而不新建
 * WorkspaceContext——两者生命周期一致（同一次请求内设置、请求结束一起清理），拆开只会增加概念数量， 不带来实际隔离收益。
 */
public final class OrgContext {

    private static final ThreadLocal<Long> CURRENT_ORG_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_WORKSPACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ALL_ORGANIZATIONS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ALL_WORKSPACES = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE = new ThreadLocal<>();

    private OrgContext() {}

    public static void setCurrentOrgId(Long orgId) {
        ALL_ORGANIZATIONS.remove();
        ALL_WORKSPACES.remove();
        CURRENT_ORG_ID.set(orgId);
    }

    public static Long getCurrentOrgId() {
        return CURRENT_ORG_ID.get();
    }

    public static void setCurrentWorkspaceId(Long workspaceId) {
        if (isAllOrganizations() && workspaceId != null) {
            throw new IllegalStateException("全组织上下文不能设置工作区");
        }
        ALL_WORKSPACES.remove();
        CURRENT_WORKSPACE_ID.set(workspaceId);
    }

    public static Long getCurrentWorkspaceId() {
        return CURRENT_WORKSPACE_ID.get();
    }

    /** 进入全组织读取上下文；只允许认证过滤器在校验 super_admin 后调用。 */
    public static void useAllOrganizations() {
        CURRENT_ORG_ID.remove();
        CURRENT_WORKSPACE_ID.remove();
        ALL_WORKSPACES.remove();
        ALL_ORGANIZATIONS.set(true);
    }

    /** 当前请求是否显式选择全部组织。 */
    public static boolean isAllOrganizations() {
        return Boolean.TRUE.equals(ALL_ORGANIZATIONS.get());
    }

    /** 进入当前组织的全工作区读取上下文。 */
    public static void useAllWorkspaces() {
        if (getCurrentOrgId() == null || isAllOrganizations()) {
            throw new IllegalStateException("全工作区上下文必须绑定具体组织");
        }
        CURRENT_WORKSPACE_ID.remove();
        ALL_WORKSPACES.set(true);
    }

    /** 当前请求是否显式选择当前组织下的全部工作区。 */
    public static boolean isAllWorkspaces() {
        return Boolean.TRUE.equals(ALL_WORKSPACES.get());
    }

    /** 设置是否忽略组织过滤（见 {@link OrgIgnore}），由对应切面维护，不建议业务代码直接调用。 */
    public static void setIgnore(Boolean ignore) {
        IGNORE.set(ignore);
    }

    /** 当前是否忽略组织过滤。 */
    public static boolean isIgnore() {
        return Boolean.TRUE.equals(IGNORE.get());
    }

    /**
     * 在忽略组织过滤的上下文中执行给定逻辑，执行完毕后还原原有状态。
     *
     * <p>用于非 Spring AOP 场景（如测试用例的 {@code setUp}）——{@link OrgIgnore} 依赖方法级 AOP 拦截，
     * 对测试代码里的普通方法调用不生效，需要用本方法显式包裹。
     */
    public static void runIgnoring(Runnable runnable) {
        var oldIgnore = isIgnore();
        try {
            setIgnore(true);
            runnable.run();
        } finally {
            setIgnore(oldIgnore);
        }
    }

    /** {@link #runIgnoring(Runnable)} 的有返回值版本。 */
    public static <T> T runIgnoring(java.util.function.Supplier<T> supplier) {
        var oldIgnore = isIgnore();
        try {
            setIgnore(true);
            return supplier.get();
        } finally {
            setIgnore(oldIgnore);
        }
    }

    public static void clear() {
        CURRENT_ORG_ID.remove();
        CURRENT_WORKSPACE_ID.remove();
        ALL_ORGANIZATIONS.remove();
        ALL_WORKSPACES.remove();
        IGNORE.remove();
    }
}
