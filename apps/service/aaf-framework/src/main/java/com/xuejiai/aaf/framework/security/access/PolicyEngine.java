package com.xuejiai.aaf.framework.security.access;

/** L4 ABAC 策略引擎 SPI。默认实现应保持无策略放行，业务可替换为 SpEL/规则表实现。 */
public interface PolicyEngine {

    PolicyResult evaluate(PolicyInput input);
}
