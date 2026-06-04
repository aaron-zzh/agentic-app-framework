package com.xuejiai.aaf.module.examples.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.security.access.PolicyEngine;
import com.xuejiai.aaf.framework.security.access.PolicyInput;
import com.xuejiai.aaf.framework.security.access.PolicyResult;

/** ABAC 策略引擎示例：通过配置开启，用于演示如何接入 L4 动态策略。 */
@Primary
@Component
@ConditionalOnProperty(name = "aaf.examples.security.policy-engine.enabled", havingValue = "true")
public class ExamplePolicyEngine implements PolicyEngine {

    @Override
    public PolicyResult evaluate(PolicyInput input) {
        if (input == null) {
            return PolicyResult.deny("策略输入为空");
        }
        if ("delete".equalsIgnoreCase(input.action()) && !sameOwner(input)) {
            return PolicyResult.deny("示例策略：只能删除自己拥有的资源");
        }
        return PolicyResult.allow();
    }

    private boolean sameOwner(PolicyInput input) {
        return input.operatorId() != null && input.operatorId().equals(input.ownerId());
    }
}
