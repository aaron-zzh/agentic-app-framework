package com.xuejiai.aaf.framework.security.authorization;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Framework 层策略合同；持久化实体由业务模块实现。 */
public record AuthorizationPolicy(
        Long id,
        long version,
        String name,
        AuthorizationTarget target,
        int priority,
        Lifecycle lifecycle,
        PolicyEffect effect,
        String conditionJson) {

    public AuthorizationPolicy {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(effect, "effect");
    }

    public enum Lifecycle {
        DRAFT,
        SHADOW,
        ENFORCE,
        DISABLED
    }

    public enum PolicyEffect {
        ALLOW,
        DENY,
        CHALLENGE;

        public AuthorizationEffect toAuthorizationEffect() {
            return switch (this) {
                case ALLOW -> AuthorizationEffect.ALLOW;
                case DENY -> AuthorizationEffect.DENY;
                case CHALLENGE -> AuthorizationEffect.CHALLENGE;
            };
        }
    }

    /** 同一全局版本下的不可变加载结果及 fact 白名单。 */
    public record Snapshot(
            String version, PolicyFactSchema factSchema, List<AuthorizationPolicy> policies) {
        public Snapshot {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("策略快照版本不能为空");
            }
            Objects.requireNonNull(factSchema, "factSchema");
            policies = policies == null ? List.of() : List.copyOf(policies);
            policies =
                    policies.stream()
                            .sorted(
                                    Comparator.comparingInt(AuthorizationPolicy::priority)
                                            .thenComparing(AuthorizationPolicy::id))
                            .toList();
        }
    }
}
