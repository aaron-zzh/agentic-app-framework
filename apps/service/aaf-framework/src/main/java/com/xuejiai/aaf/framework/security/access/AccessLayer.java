package com.xuejiai.aaf.framework.security.access;

/**
 * 权限处理层级——三层分层模型。
 *
 * <p>设计原则：前一层处理了，后续层不再重复校验。
 * 所有对外接口（REST/WebSocket/AG-UI/A2A/MCP/IoT）统一适用。
 *
 * <pre>
 * Layer 1: 注解层（@AccessControl）
 *   ├─ 声明式，编译期可见
 *   ├─ 适用：角色要求、功能开关、限流等级
 *   └─ 处理者：Spring Security + AOP
 *
 * Layer 2: 拦截器层（PermissionInterceptor）
 *   ├─ 运行时动态，基于请求上下文
 *   ├─ 适用：数据权限、租户隔离、资源归属校验
 *   └─ 处理者：HandlerInterceptor / WebSocket HandshakeInterceptor
 *
 * Layer 3: 服务层（ServicePermissionChecker）
 *   ├─ 业务逻辑内嵌，需要查库才能判断
 *   ├─ 适用：字段级权限、业务规则（如"只能编辑自己的草稿"）
 *   └─ 处理者：Service 方法内调用
 * </pre>
 */
public enum AccessLayer {

    /** 注解层：声明式权限（角色/功能开关/限流） */
    ANNOTATION,

    /** 拦截器层：动态权限（数据权限/租户隔离/资源归属） */
    INTERCEPTOR,

    /** 服务层：业务权限（字段级/业务规则） */
    SERVICE
}
