package com.xuejiai.aaf.framework.security.authorization;

/** L3 数据约束检查 SPI；实现必须同时返回裁决效果与 PEP 可消费的类型安全约束。 */
public interface DataAuthorizationProvider {

    DataAuthorizationResult evaluate(
            AuthorizationRequest request, AuthorizationPlan.DataRequirement requirement);
}
