package com.xuejiai.aaf.framework.scope;

import com.xuejiai.aaf.framework.crud.BaseCrudService;

/**
 * 查询视角上下文，存储当前请求是否显式声明"查询全部数据"（Domain 层，参考 Odoo 的 domain 机制）。
 *
 * <p>与行级数据权限（{@code sys_data_access_rule}，对应 Odoo 的 record rule）是两条独立的线： 行级规则面向普通用户强制隔离，对
 * super_admin 由统一 {@code AuthorizationService} 快速通道放行，这条线不因本上下文改变。 本上下文实现的是"个人视角"这一查询层显式收窄——如
 * studio 场景下，即使是管理员角色，也只应看到自己的数据， 与角色无关，通过 {@code X-Scope} 请求头声明。
 *
 * <p><b>默认值是收窄（fail-safe）</b>：未声明 {@code X-Scope: all} 时默认视为个人视角，即使请求完全绕过前端 封装的 HTTP 客户端（如脚本直连、未来的
 * SSR fetch），后端也保底收窄到当前用户，不依赖调用方配合传头。 只有显式声明 {@code X-Scope: all} 才不额外收窄，交由行级数据权限（L3）决定可见范围。 由
 * {@link BaseCrudService#ownScopeSpec} 消费。
 *
 * <p>下沉到 {@code aaf-framework}（而非 {@code aaf-api}），使 BaseCrudService 能直接读取，无需反向依赖业务模块。
 */
public final class ScopeContext {

    private static final ThreadLocal<Boolean> ALL_SCOPE = new ThreadLocal<>();

    private ScopeContext() {}

    /** 设置当前请求是否显式声明查询全部，由 Filter 从 {@code X-Scope} 请求头解析后写入。 */
    public static void setAllScope(boolean allScope) {
        ALL_SCOPE.set(allScope);
    }

    /** 当前请求是否显式声明查询全部（默认 false，即默认收窄到个人视角）。 */
    public static boolean isAllScope() {
        return Boolean.TRUE.equals(ALL_SCOPE.get());
    }

    public static void clear() {
        ALL_SCOPE.remove();
    }
}
