package com.xuejiai.aaf.framework.org;

import java.util.Collection;
import java.util.Set;

/**
 * 组织上下文，存储当前请求/任务的组织 ID、工作区 ID与聚合读取状态。
 *
 * <p>下沉到 {@code aaf-framework}（而非 {@code aaf-api}），使 framework 层的定时任务等场景也能读取/设置组织上下文、声明豁免组织过滤（见
 * {@link OrgIgnore}）。
 */
public final class OrgContext {

    private static final ThreadLocal<Long> CURRENT_ORG_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_WORKSPACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ALL_ORGANIZATIONS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ALL_ORGANIZATIONS_UNRESTRICTED = new ThreadLocal<>();
    private static final ThreadLocal<Set<Long>> ACCESSIBLE_ORG_IDS = new ThreadLocal<>();
    private static final ThreadLocal<Set<Long>> ACCESSIBLE_WORKSPACE_IDS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ALL_WORKSPACES = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE = new ThreadLocal<>();

    private OrgContext() {}

    public static void setCurrentOrgId(Long orgId) {
        clearAllOrganizationsScope();
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

    /** 进入 super_admin 平台全组织读取上下文。 */
    public static void useAllOrganizations() {
        enterAllOrganizations();
        ALL_ORGANIZATIONS_UNRESTRICTED.set(true);
        ACCESSIBLE_ORG_IDS.set(Set.of());
        ACCESSIBLE_WORKSPACE_IDS.set(Set.of());
    }

    /** 进入普通用户的成员组织与已加入工作区聚合读取上下文。 */
    public static void useAllOrganizations(
            Collection<Long> organizationIds, Collection<Long> workspaceIds) {
        var organizations = Set.copyOf(organizationIds);
        if (organizations.isEmpty()) {
            throw new IllegalArgumentException("成员组织集合不能为空");
        }
        enterAllOrganizations();
        ALL_ORGANIZATIONS_UNRESTRICTED.set(false);
        ACCESSIBLE_ORG_IDS.set(organizations);
        ACCESSIBLE_WORKSPACE_IDS.set(Set.copyOf(workspaceIds));
    }

    /** 当前请求是否显式选择全部组织。 */
    public static boolean isAllOrganizations() {
        return Boolean.TRUE.equals(ALL_ORGANIZATIONS.get());
    }

    /** 当前全部组织上下文是否为 super_admin 平台全量。 */
    public static boolean isAllOrganizationsUnrestricted() {
        return Boolean.TRUE.equals(ALL_ORGANIZATIONS_UNRESTRICTED.get());
    }

    public static Set<Long> getAccessibleOrgIds() {
        var ids = ACCESSIBLE_ORG_IDS.get();
        return ids == null ? Set.of() : ids;
    }

    public static Set<Long> getAccessibleWorkspaceIds() {
        var ids = ACCESSIBLE_WORKSPACE_IDS.get();
        return ids == null ? Set.of() : ids;
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

    public static void runIgnoring(Runnable runnable) {
        var oldIgnore = isIgnore();
        try {
            setIgnore(true);
            runnable.run();
        } finally {
            setIgnore(oldIgnore);
        }
    }

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
        clearAllOrganizationsScope();
        ALL_WORKSPACES.remove();
        IGNORE.remove();
    }

    private static void enterAllOrganizations() {
        CURRENT_ORG_ID.remove();
        CURRENT_WORKSPACE_ID.remove();
        ALL_WORKSPACES.remove();
        ALL_ORGANIZATIONS.set(true);
    }

    private static void clearAllOrganizationsScope() {
        ALL_ORGANIZATIONS.remove();
        ALL_ORGANIZATIONS_UNRESTRICTED.remove();
        ACCESSIBLE_ORG_IDS.remove();
        ACCESSIBLE_WORKSPACE_IDS.remove();
    }
}
