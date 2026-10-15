package com.xuejiai.aaf.framework.intelligent.assistant.role;

import java.util.Objects;

/**
 * Role 可见性的唯一判断入口。
 *
 * <p>可见性是安全边界，固定在代码里，不依赖可被配置的记录规则：
 *
 * <ul>
 *   <li>SYSTEM / PUBLIC 对所有用户可见、可绑定
 *   <li>PRIVATE 仅所有者可见、可绑定、可修改，判断必须同时满足 {@code visibility = PRIVATE} 与 {@code ownerId} 相等
 *   <li>WORKSPACE / ORG 仅预留，任何人不可见
 * </ul>
 *
 * <p>所有读取 {@link Role} 的入口（用户面接口、助理加载、绑定校验、引用校验、运行时再校验）都必须通过本类或 {@link AiRoleRepository}
 * 中带可见性条件的查询方法，不得用无条件读取绕过。 越权统一视为"不存在"，不泄露存在性。
 */
public final class RoleVisibility {

    /** 枚举字面量前缀；JPQL 注解参数要求编译期常量，所以不用 {@code Class.getName()}。 */
    private static final String LEVEL =
            "com.xuejiai.aaf.framework.intelligent.assistant.role.RoleVisibilityLevel.";

    /** JPQL 条件：角色对 {@code :userId} 可见（别名 r）。 */
    static final String VISIBLE_TO_USER =
            "(r.visibility IN ("
                    + LEVEL
                    + "SYSTEM, "
                    + LEVEL
                    + "PUBLIC) OR (r.visibility = "
                    + LEVEL
                    + "PRIVATE AND r.ownerId = :userId))";

    /** JPQL 条件：角色是 {@code :ownerId} 的私有角色（别名 r）。 */
    static final String PRIVATE_OF_OWNER =
            "(r.visibility = " + LEVEL + "PRIVATE AND r.ownerId = :ownerId)";

    private RoleVisibility() {}

    /** 角色对用户是否可见。 */
    public static boolean canSee(Long userId, Role role) {
        Objects.requireNonNull(role, "role 不能为空");
        var level = role.getVisibility();
        if (level == null) {
            return false;
        }
        return switch (level) {
            case SYSTEM, PUBLIC -> true;
            case PRIVATE -> isOwner(userId, role);
            case WORKSPACE, ORG -> false;
        };
    }

    /** 角色是否可被用户绑定：可见且处于可用状态。 */
    public static boolean canBind(Long userId, Role role) {
        return canSee(userId, role) && Role.STATUS_ACTIVE.equals(role.getStatus());
    }

    /** 用户是否可以修改（编辑、删除）角色：只有私有角色的所有者可以。 */
    public static boolean canModify(Long userId, Role role) {
        Objects.requireNonNull(role, "role 不能为空");
        return role.getVisibility() == RoleVisibilityLevel.PRIVATE && isOwner(userId, role);
    }

    /** 是否为私有角色的所有者。 */
    public static boolean isOwner(Long userId, Role role) {
        return userId != null
                && role.getVisibility() == RoleVisibilityLevel.PRIVATE
                && userId.equals(role.getOwnerId());
    }
}
