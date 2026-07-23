package com.xuejiai.aaf.framework.security.authorization;

/** 业务层实现的策略快照加载 SPI。 */
public interface AuthorizationPolicyProvider {

    AuthorizationPolicy.Snapshot loadSnapshot(AuthorizationTarget target);
}
