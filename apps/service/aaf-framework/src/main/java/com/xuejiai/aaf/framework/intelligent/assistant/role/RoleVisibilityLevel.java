package com.xuejiai.aaf.framework.intelligent.assistant.role;

/**
 * Role 可见性。
 *
 * <p>{@link #SYSTEM} 平台内置，所有用户可见可绑定；{@link #PUBLIC} 管理员发布，所有用户可见可绑定； {@link #PRIVATE}
 * 用户私有，仅所有者可见可绑定。{@link #WORKSPACE}、{@link #ORG} 仅预留取值，服务端保存时一律拒绝。
 */
public enum RoleVisibilityLevel {
    SYSTEM,
    PUBLIC,
    PRIVATE,
    WORKSPACE,
    ORG;

    /** 是否为已启用的可见性（预留值不可保存）。 */
    public boolean enabled() {
        return this == SYSTEM || this == PUBLIC || this == PRIVATE;
    }

    /** 是否为全员可见的可见性。 */
    public boolean shared() {
        return this == SYSTEM || this == PUBLIC;
    }
}
