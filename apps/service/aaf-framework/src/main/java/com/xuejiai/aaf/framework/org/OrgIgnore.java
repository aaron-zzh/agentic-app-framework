package com.xuejiai.aaf.framework.org;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法（如定时任务）不受组织过滤强制约束——跳过 {@code orgId} 缺失时的 fail-closed 拒绝， 也不启用 {@code orgFilter}，按原有全量语义查询。
 *
 * <p>适用场景：本身需要扫描全部组织数据的全局性后台任务（如积分过期检查、记忆衰减清理）， 这类任务运行在没有 HTTP 请求上下文的独立线程，{@code OrgContext} 中不会有
 * orgId。
 *
 * <p>不适用场景：需要按组织分别处理数据的任务——那种场景应遍历组织列表，对每个组织单独设置 {@link OrgContext#setCurrentOrgId(Long)}
 * 后执行，不应该整体豁免。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OrgIgnore {}
