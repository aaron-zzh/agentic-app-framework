package com.xuejiai.aaf.framework.security.authorization;

import java.util.List;
import java.util.UUID;

/** PDP 返回的可解释授权决定。 */
public record AuthorizationDecision(
        AuthorizationEffect effect,
        List<LayerDecision> layerDecisions,
        List<ShadowDecision> shadowDecisions,
        UUID challengeId,
        String policyVersion) {

    public AuthorizationDecision {
        layerDecisions = layerDecisions == null ? List.of() : List.copyOf(layerDecisions);
        shadowDecisions = shadowDecisions == null ? List.of() : List.copyOf(shadowDecisions);
    }

    /** 只有明确 ALLOW 才可执行；INDETERMINATE 与 CHALLENGE 均故障关闭。 */
    public boolean allowed() {
        return effect == AuthorizationEffect.ALLOW;
    }

    public AuthorizationEffect enforcementEffect() {
        return allowed() ? AuthorizationEffect.ALLOW : AuthorizationEffect.DENY;
    }

    public String reason() {
        return layerDecisions.stream()
                .filter(layer -> layer.reason() != null && !layer.reason().isBlank())
                .reduce((first, second) -> second)
                .map(LayerDecision::reason)
                .orElse(null);
    }

    public record LayerDecision(
            AuthorizationLayer layer,
            AuthorizationEffect effect,
            String reason,
            List<ItemDecision> items,
            List<Long> policyIds) {
        public LayerDecision {
            items = items == null ? List.of() : List.copyOf(items);
            policyIds = policyIds == null ? List.of() : List.copyOf(policyIds);
        }

        public static LayerDecision of(
                AuthorizationLayer layer, AuthorizationEffect effect, String reason) {
            return new LayerDecision(layer, effect, reason, List.of(), List.of());
        }
    }

    public record ItemDecision(
            String key,
            AuthorizationEffect effect,
            String reason,
            AuthorizationConstraint constraint) {

        public ItemDecision(String key, AuthorizationEffect effect, String reason) {
            this(key, effect, reason, null);
        }
    }

    /** SHADOW 策略只进入此集合，不得影响正式 effect。 */
    public record ShadowDecision(
            Long policyId,
            long policyVersion,
            String snapshotVersion,
            AuthorizationEffect effect,
            String reason) {}
}
