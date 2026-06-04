package com.xuejiai.aaf.framework.security.access;

import org.springframework.stereotype.Component;

/** 默认 ABAC 策略引擎：无策略时放行。 */
@Component
public class DefaultPolicyEngine implements PolicyEngine {

    @Override
    public PolicyResult evaluate(PolicyInput input) {
        return PolicyResult.allow();
    }
}
