package com.xuejiai.aaf.framework.security.authorization;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationChallengeStore.PendingChallenge;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationDecision.ItemDecision;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationDecision.LayerDecision;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationDecision.ShadowDecision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 默认 PDP：按显式计划执行 L1-L4，并以安全优先级合并结果。 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class DefaultAuthorizationService implements AuthorizationService {

    private final OperatorContext operatorContext;
    private final ObjectProvider<FunctionPermissionChecker> functionPermissionChecker;
    private final ObjectProvider<RelationPermissionChecker> relationPermissionChecker;
    private final ObjectProvider<DataAuthorizationProvider> dataAuthorizationProvider;
    private final ObjectProvider<AuthorizationPolicyProvider> policyProvider;
    private final ObjectProvider<AuthorizationChallengeStore> challengeStore;
    private final ObjectProvider<AuthorizationAuditSink> auditSinks;
    private final PolicyDslCompiler policyCompiler;
    private final PolicyExpressionEvaluator policyEvaluator;

    @Override
    public AuthorizationDecision authorize(AuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        return authorizeInternal(request, null);
    }

    private AuthorizationDecision authorizeInternal(
            AuthorizationRequest request, VerifiedChallenge verifiedChallenge) {
        var effective = completeSubject(request);
        var layers = new ArrayList<LayerDecision>(4);
        layers.add(evaluateL1(effective));
        layers.add(evaluateL2(effective));
        layers.add(evaluateL3(effective));

        var policyEvaluation = evaluateL4(effective, verifiedChallenge);
        layers.add(policyEvaluation.layer());
        var effect = combineLayers(layers);
        UUID challengeId = null;
        if (effect == AuthorizationEffect.CHALLENGE
                && policyEvaluation.challengePolicy() != null) {
            var challengeResult = createChallenge(effective, policyEvaluation);
            layers.set(3, challengeResult.layer());
            effect = combineLayers(layers);
            challengeId = challengeResult.challengeId();
        }

        var decision =
                new AuthorizationDecision(
                        effect,
                        layers,
                        policyEvaluation.shadows(),
                        challengeId,
                        policyEvaluation.snapshotVersion());
        auditFinal(effective, decision);
        return decision;
    }

    @Override
    public boolean approveChallenge(UUID challengeId) {
        try {
            var subjectId = operatorContext.currentOwnerId().orElse(null);
            var store = challengeStore.getIfAvailable();
            return subjectId != null
                    && store != null
                    && store.approve(challengeId, subjectId, Instant.now());
        } catch (RuntimeException ex) {
            log.error("challenge 批准失败: challengeId={}", challengeId, ex);
            return false;
        }
    }

    @Override
    public AuthorizationService.ContinuationSelection selectContinuation(
            UUID challengeId, AuthorizationRequest request) {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(request, "request");
        try {
            var effective = completeSubject(request);
            var store = challengeStore.getIfAvailable();
            if (store == null || effective.subject().subjectId() == null) {
                return AuthorizationService.ContinuationSelection.INDETERMINATE;
            }
            var approved =
                    store.findApproved(
                            challengeId, effective.subject().subjectId(), Instant.now());
            if (approved.isEmpty()) {
                return AuthorizationService.ContinuationSelection.INDETERMINATE;
            }
            var challenge = approved.get();
            if (!Objects.equals(challenge.id(), challengeId)
                    || !hasCompleteBinding(challenge)) {
                return AuthorizationService.ContinuationSelection.INDETERMINATE;
            }
            return matchesRequestBinding(challenge, effective)
                    ? AuthorizationService.ContinuationSelection.MATCH
                    : AuthorizationService.ContinuationSelection.NOT_MATCH;
        } catch (RuntimeException ex) {
            return AuthorizationService.ContinuationSelection.INDETERMINATE;
        }
    }

    @Override
    public AuthorizationDecision resume(UUID challengeId, AuthorizationRequest request) {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(request, "request");
        var effective = completeSubject(request);

        final AuthorizationChallengeStore store;
        try {
            store = challengeStore.getIfAvailable();
        } catch (RuntimeException ex) {
            return terminalDecision(AuthorizationEffect.INDETERMINATE, "challenge Store 获取失败");
        }
        if (store == null || effective.subject().subjectId() == null) {
            return terminalDecision(AuthorizationEffect.INDETERMINATE, "challenge 存储或主体不可用");
        }

        final AuthorizationChallengeStore.Challenge challenge;
        try {
            var approved =
                    store.findApproved(
                            challengeId, effective.subject().subjectId(), Instant.now());
            if (approved.isEmpty()) {
                return terminalDecision(
                        AuthorizationEffect.DENY, "challenge 不存在、未批准、已消费或已过期");
            }
            challenge = approved.get();
        } catch (RuntimeException ex) {
            return terminalDecision(AuthorizationEffect.INDETERMINATE, "challenge 查询失败");
        }

        if (!Objects.equals(challenge.id(), challengeId) || !matchesBinding(challenge, effective)) {
            return terminalDecision(AuthorizationEffect.DENY, "challenge 与当前请求绑定不一致");
        }
        try {
            if (!store.consume(challengeId, effective.subject().subjectId(), Instant.now())) {
                return terminalDecision(AuthorizationEffect.DENY, "challenge 已被消费");
            }
        } catch (RuntimeException ex) {
            return terminalDecision(AuthorizationEffect.INDETERMINATE, "challenge 消费失败");
        }

        var verifiedChallenge =
                new VerifiedChallenge(
                        challenge.id(),
                        challenge.policyId(),
                        challenge.policyVersion(),
                        challenge.snapshotVersion());
        return authorizeInternal(effective, verifiedChallenge);
    }

    private LayerDecision evaluateL1(AuthorizationRequest request) {
        var requirement = request.plan().l1();
        if (request.subject().subjectId() == null) {
            return LayerDecision.of(
                    AuthorizationLayer.L1_FUNCTION, AuthorizationEffect.DENY, "主体未认证");
        }
        if (requirement.mode() == AuthorizationPlan.FunctionMode.AUTHENTICATED) {
            return LayerDecision.of(
                    AuthorizationLayer.L1_FUNCTION, AuthorizationEffect.ALLOW, "主体已认证");
        }
        if (hasSuperAdminAuthority()) {
            return LayerDecision.of(
                    AuthorizationLayer.L1_FUNCTION, AuthorizationEffect.ALLOW, "超级管理员");
        }

        final FunctionPermissionChecker checker;
        try {
            checker = functionPermissionChecker.getIfAvailable();
        } catch (RuntimeException ex) {
            return LayerDecision.of(
                    AuthorizationLayer.L1_FUNCTION,
                    AuthorizationEffect.INDETERMINATE,
                    "L1 权限 Provider 获取失败");
        }
        if (checker == null) {
            return LayerDecision.of(
                    AuthorizationLayer.L1_FUNCTION,
                    AuthorizationEffect.INDETERMINATE,
                    "L1 权限 Provider 不可用");
        }

        var items = new ArrayList<ItemDecision>();
        for (var permissionCode : requirement.permissionCodes()) {
            try {
                if (!checker.isRegistered(permissionCode)) {
                    items.add(
                            new ItemDecision(
                                    permissionCode,
                                    AuthorizationEffect.DENY,
                                    "L1 权限码未注册"));
                    continue;
                }
                var allowed =
                        checker.hasPermission(request.subject().subjectId(), permissionCode);
                items.add(
                        new ItemDecision(
                                permissionCode,
                                allowed ? AuthorizationEffect.ALLOW : AuthorizationEffect.DENY,
                                "L1 功能权限"));
            } catch (RuntimeException ex) {
                items.add(
                        new ItemDecision(
                                permissionCode,
                                AuthorizationEffect.INDETERMINATE,
                                "L1 权限检查失败"));
            }
        }
        return combinedLayer(
                AuthorizationLayer.L1_FUNCTION,
                AuthorizationPlan.CombinationMode.ALL_APPLICABLE,
                items,
                "L1 功能权限组合");
    }

    private LayerDecision evaluateL2(AuthorizationRequest request) {
        var plan = request.plan().l2();
        if (plan == null) {
            return LayerDecision.of(
                    AuthorizationLayer.L2_RELATION,
                    AuthorizationEffect.NOT_APPLICABLE,
                    "未声明 L2");
        }
        final RelationPermissionChecker checker;
        try {
            checker = relationPermissionChecker.getIfAvailable();
        } catch (RuntimeException ex) {
            return LayerDecision.of(
                    AuthorizationLayer.L2_RELATION,
                    AuthorizationEffect.INDETERMINATE,
                    "L2 Provider 获取失败");
        }
        if (checker == null) {
            return LayerDecision.of(
                    AuthorizationLayer.L2_RELATION,
                    AuthorizationEffect.INDETERMINATE,
                    "L2 Provider 不可用");
        }
        if (request.subject().subjectId() == null) {
            return LayerDecision.of(
                    AuthorizationLayer.L2_RELATION, AuthorizationEffect.DENY, "主体未认证");
        }
        if (hasSuperAdminAuthority()) {
            return LayerDecision.of(
                    AuthorizationLayer.L2_RELATION, AuthorizationEffect.ALLOW, "超级管理员");
        }

        var items = new ArrayList<ItemDecision>();
        for (var requirement : plan.requirements()) {
            var key =
                    "%s:%s#%s"
                            .formatted(
                                    requirement.objectType(),
                                    requirement.objectId(),
                                    requirement.permission());
            try { var allowed =
                    checker.hasPermission(
                            request.subject().subjectId(),
                            requirement.objectType(),
                            requirement.objectId(),
                            requirement.permission());
            items.add(
                    new ItemDecision(
                            key,
                            allowed ? AuthorizationEffect.ALLOW : AuthorizationEffect.DENY,
                            "L2 关系权限")); } catch (RuntimeException ex) { items.add(new ItemDecision(key, AuthorizationEffect.INDETERMINATE, "L2 关系检查失败")); }
        }
        return combinedLayer(
                AuthorizationLayer.L2_RELATION, plan.combination(), items, "L2 关系权限组合");
    }

    private LayerDecision evaluateL3(AuthorizationRequest request) {
        var plan = request.plan().l3();
        if (plan == null) {
            return LayerDecision.of(
                    AuthorizationLayer.L3_DATA,
                    AuthorizationEffect.NOT_APPLICABLE,
                    "未声明 L3");
        }
        final DataAuthorizationProvider provider;
        try {
            provider = dataAuthorizationProvider.getIfAvailable();
        } catch (RuntimeException ex) {
            return LayerDecision.of(
                    AuthorizationLayer.L3_DATA,
                    AuthorizationEffect.INDETERMINATE,
                    "L3 Provider 获取失败");
        }
        if (provider == null) {
            return LayerDecision.of(
                    AuthorizationLayer.L3_DATA,
                    AuthorizationEffect.INDETERMINATE,
                    "L3 Provider 不可用");
        }

        var items = new ArrayList<ItemDecision>();
        for (var requirement : plan.requirements()) {
            try { var result = provider.evaluate(request, requirement);
            if (result == null) {
                throw new IllegalStateException("L3 Provider 返回空结果");
            }
            var effect = normalizeDeclared(result.effect());
            if (effect == AuthorizationEffect.ALLOW && result.constraint() == null) {
                throw new IllegalStateException("L3 ALLOW 缺少授权约束");
            }
            items.add(
                    new ItemDecision(
                            requirement.key(),
                            effect,
                            result.reason(),
                            effect == AuthorizationEffect.ALLOW
                                    ? result.constraint()
                                    : null)); } catch (RuntimeException ex) { items.add(new ItemDecision(requirement.key(), AuthorizationEffect.INDETERMINATE, "L3 数据约束检查失败")); }
        }
        return combinedLayer(
                AuthorizationLayer.L3_DATA, plan.combination(), items, "L3 数据约束组合");
    }

    private PolicyEvaluation evaluateL4(
            AuthorizationRequest request, VerifiedChallenge verifiedChallenge) {
        if (request.plan().l4() == null) {
            return PolicyEvaluation.notApplicable("未声明 L4");
        }
        if (!request.target().hasPolicyTarget()) {
            return PolicyEvaluation.indeterminate("已声明 L4 但缺少 resource/action");
        }

        final AuthorizationPolicyProvider provider;
        try {
            provider = policyProvider.getIfAvailable();
        } catch (RuntimeException ex) {
            return PolicyEvaluation.indeterminate("L4 Provider 获取失败");
        }
        if (provider == null) {
            return PolicyEvaluation.indeterminate("L4 Provider 不可用");
        }

        final AuthorizationPolicy.Snapshot snapshot;
        try {
            snapshot = provider.loadSnapshot(request.target());
        } catch (RuntimeException ex) {
            return PolicyEvaluation.indeterminate("L4 策略快照加载失败");
        }
        if (snapshot == null) {
            return PolicyEvaluation.indeterminate("L4 Provider 返回空快照");
        }

        var items = new ArrayList<ItemDecision>();
        var policyIds = new ArrayList<Long>();
        var shadows = new ArrayList<ShadowDecision>();
        AuthorizationPolicy challengePolicy = null;
        for (var policy : snapshot.policies()) {
            if (!matchesTarget(policy.target(), request.target())) {
                continue;
            }
            switch (policy.lifecycle()) {
                case DRAFT, DISABLED -> {
                    continue;
                }
                case SHADOW -> evaluateShadow(request, snapshot, policy, shadows);
                case ENFORCE -> {
                    var item =
                            evaluateEnforced(
                                    request, snapshot, policy, verifiedChallenge);
                    if (item == null) {
                        continue;
                    }
                    items.add(item);
                    policyIds.add(policy.id());
                    if (item.effect() == AuthorizationEffect.CHALLENGE
                            && challengePolicy == null) {
                        challengePolicy = policy;
                    }
                    auditPolicy(
                            request,
                            policy,
                            snapshot.version(),
                            item.effect(),
                            false,
                            null,
                            item.reason());
                }
            }
        }
        if (items.isEmpty()) {
            return new PolicyEvaluation(
                    LayerDecision.of(
                            AuthorizationLayer.L4_POLICY,
                            AuthorizationEffect.NOT_APPLICABLE,
                            "无匹配 ENFORCE 策略"),
                    shadows,
                    null,
                    snapshot.version());
        }
        var effect = AuthorizationEffect.strongest(items.stream().map(ItemDecision::effect).toList());
        var reason = "L4 ENFORCE 策略组合结果: " + effect;
        return new PolicyEvaluation(
                new LayerDecision(
                        AuthorizationLayer.L4_POLICY, effect, reason, items, policyIds),
                shadows,
                effect == AuthorizationEffect.CHALLENGE ? challengePolicy : null,
                snapshot.version());
    }

    private void evaluateShadow(
            AuthorizationRequest request,
            AuthorizationPolicy.Snapshot snapshot,
            AuthorizationPolicy policy,
            List<ShadowDecision> shadows) {
        try {
            var expression = policyCompiler.compile(policy.conditionJson(), snapshot.factSchema());
            if (!policyEvaluator.evaluate(expression, request)) {
                return;
            }
            var effect = policy.effect().toAuthorizationEffect();
            shadows.add(
                    new ShadowDecision(
                            policy.id(), policy.version(), snapshot.version(), effect, "SHADOW 命中"));
            auditPolicy(
                    request,
                    policy,
                    snapshot.version(),
                    effect,
                    true,
                    null,
                    "SHADOW 命中");
        } catch (RuntimeException ex) {
            shadows.add(
                    new ShadowDecision(
                            policy.id(),
                            policy.version(),
                            snapshot.version(),
                            AuthorizationEffect.INDETERMINATE,
                            "SHADOW 策略编译或求值失败"));
            auditPolicy(
                    request,
                    policy,
                    snapshot.version(),
                    AuthorizationEffect.INDETERMINATE,
                    true,
                    null,
                    "SHADOW 策略编译或求值失败");
        }
    }

    private ItemDecision evaluateEnforced(
            AuthorizationRequest request,
            AuthorizationPolicy.Snapshot snapshot,
            AuthorizationPolicy policy,
            VerifiedChallenge verifiedChallenge) {
        try {
            var expression = policyCompiler.compile(policy.conditionJson(), snapshot.factSchema());
            if (!policyEvaluator.evaluate(expression, request)) {
                return null;
            }
            var effect = policy.effect().toAuthorizationEffect();
            if (effect == AuthorizationEffect.CHALLENGE
                    && verifiedChallenge != null
                    && verifiedChallenge.matchesSnapshot(snapshot)) {
                effect = AuthorizationEffect.ALLOW;
            }
            return new ItemDecision(
                    "policy:" + policy.id(), effect, "ENFORCE 策略命中");
        } catch (RuntimeException ex) {
            return new ItemDecision(
                    "policy:" + policy.id(),
                    AuthorizationEffect.INDETERMINATE,
                    "ENFORCE 策略编译或求值失败");
        }
    }

    private ChallengeResult createChallenge(
            AuthorizationRequest request, PolicyEvaluation evaluation) {
        final AuthorizationChallengeStore store;
        try {
            store = challengeStore.getIfAvailable();
        } catch (RuntimeException ex) {
            return new ChallengeResult(
                    replaceEffect(
                            evaluation.layer(),
                            AuthorizationEffect.INDETERMINATE,
                            "challenge Store 获取失败"),
                    null);
        }
        var policy = evaluation.challengePolicy();
        if (store == null || request.subject().subjectId() == null || policy == null) {
            return new ChallengeResult(
                    replaceEffect(
                            evaluation.layer(),
                            AuthorizationEffect.INDETERMINATE,
                            "challenge Store、主体或策略不可用"),
                    null);
        }
        try {
            var challengeId =
                    store.create(
                            new PendingChallenge(
                                    request.subject(),
                                    request.target(),
                                    request.digest(),
                                    policy.id(),
                                    policy.version(),
                                    evaluation.snapshotVersion(),
                                    Instant.now().plus(request.challengeTtl())));
            auditPolicy(
                    request,
                    policy,
                    evaluation.snapshotVersion(),
                    AuthorizationEffect.CHALLENGE,
                    false,
                    challengeId,
                    "等待一次性确认");
            return new ChallengeResult(evaluation.layer(), challengeId);
        } catch (RuntimeException ex) {
            return new ChallengeResult(
                    replaceEffect(
                            evaluation.layer(),
                            AuthorizationEffect.INDETERMINATE,
                            "challenge 持久化失败"),
                    null);
        }
    }

    private LayerDecision combinedLayer(
            AuthorizationLayer layer,
            AuthorizationPlan.CombinationMode combination,
            List<ItemDecision> items,
            String reason) {
        var effects = items.stream().map(ItemDecision::effect).toList();
        var effect =
                switch (combination) {
                    case ALL_APPLICABLE ->
                            effects.stream().allMatch(AuthorizationEffect.ALLOW::equals)
                                    ? AuthorizationEffect.ALLOW
                                    : AuthorizationEffect.strongest(effects);
                    case ANY_APPLICABLE -> AuthorizationEffect.strongest(effects);
                };
        return new LayerDecision(layer, effect, reason, items, List.of());
    }

    private AuthorizationEffect normalizeDeclared(AuthorizationEffect effect) {
        return effect == null || effect == AuthorizationEffect.NOT_APPLICABLE
                ? AuthorizationEffect.INDETERMINATE
                : effect;
    }

    private AuthorizationEffect combineLayers(List<LayerDecision> layers) {
        return AuthorizationEffect.strongest(
                layers.stream().map(LayerDecision::effect).toList());
    }

    private AuthorizationRequest completeSubject(AuthorizationRequest request) {
        var subject =
                request.subject()
                        .resolve(
                                operatorContext.currentOperatorId().orElse(null),
                                operatorContext.currentOwnerId().orElse(null));
        return request.withSubject(subject);
    }

    private boolean matchesTarget(AuthorizationTarget configured, AuthorizationTarget actual) {
        return matches(configured.resource(), actual.resource())
                && matches(configured.action(), actual.action())
                && matches(configured.objectId(), actual.objectId());
    }

    private boolean matches(String configured, String actual) {
        return configured == null || "*".equals(configured) || Objects.equals(configured, actual);
    }

    private boolean matchesBinding(
            AuthorizationChallengeStore.Challenge challenge, AuthorizationRequest request) {
        return hasCompleteBinding(challenge) && matchesRequestBinding(challenge, request);
    }

    private boolean hasCompleteBinding(AuthorizationChallengeStore.Challenge challenge) {
        return challenge.id() != null
                && challenge.subject() != null
                && challenge.subject().subjectId() != null
                && challenge.target() != null
                && challenge.target().hasPolicyTarget()
                && challenge.requestDigest() != null
                && !challenge.requestDigest().isBlank()
                && challenge.policyId() != null
                && challenge.policyVersion() > 0
                && challenge.snapshotVersion() != null
                && !challenge.snapshotVersion().isBlank()
                && challenge.expiresAt() != null;
    }

    private boolean matchesRequestBinding(
            AuthorizationChallengeStore.Challenge challenge, AuthorizationRequest request) {
        return Objects.equals(challenge.subject(), request.subject())
                && Objects.equals(challenge.target(), request.target())
                && Objects.equals(challenge.requestDigest(), request.digest());
    }

    private boolean hasSuperAdminAuthority() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_SUPER_ADMIN"::equals);
    }

    private LayerDecision replaceEffect(
            LayerDecision layer, AuthorizationEffect effect, String reason) {
        return new LayerDecision(
                layer.layer(), effect, reason, layer.items(), layer.policyIds());
    }

    private AuthorizationDecision terminalDecision(AuthorizationEffect effect, String reason) {
        return new AuthorizationDecision(
                effect,
                List.of(LayerDecision.of(AuthorizationLayer.L4_POLICY, effect, reason)),
                List.of(),
                null,
                null);
    }

    private void auditPolicy(
            AuthorizationRequest request,
            AuthorizationPolicy policy,
            String snapshotVersion,
            AuthorizationEffect effect,
            boolean shadow,
            UUID challengeId,
            String reason) {
        audit(
                new AuthorizationAuditSink.Event(
                        "POLICY_EVALUATED",
                        request.subject(),
                        request.target(),
                        AuthorizationLayer.L4_POLICY,
                        effect,
                        policy.id(),
                        policy.version(),
                        snapshotVersion,
                        shadow,
                        challengeId,
                        reason,
                        Instant.now()));
    }

    private void auditFinal(AuthorizationRequest request, AuthorizationDecision decision) {
        audit(
                new AuthorizationAuditSink.Event(
                        "AUTHORIZATION_DECIDED",
                        request.subject(),
                        request.target(),
                        null,
                        decision.effect(),
                        null,
                        null,
                        decision.policyVersion(),
                        false,
                        decision.challengeId(),
                        decision.reason(),
                        Instant.now()));
    }

    private void audit(AuthorizationAuditSink.Event event) {
        try {
            auditSinks.orderedStream()
                    .forEach(
                            sink -> {
                                try {
                                    sink.record(event);
                                } catch (RuntimeException ex) {
                                    log.error(
                                            "授权审计写入失败: eventType={}",
                                            event.eventType(),
                                            ex);
                                }
                            });
        } catch (RuntimeException ex) {
            log.error("授权审计 Sink 获取失败: eventType={}", event.eventType(), ex);
        }
    }

    private record PolicyEvaluation(
            LayerDecision layer,
            List<ShadowDecision> shadows,
            AuthorizationPolicy challengePolicy,
            String snapshotVersion) {
        private PolicyEvaluation {
            shadows = List.copyOf(shadows);
        }

        private static PolicyEvaluation notApplicable(String reason) {
            return new PolicyEvaluation(
                    LayerDecision.of(
                            AuthorizationLayer.L4_POLICY,
                            AuthorizationEffect.NOT_APPLICABLE,
                            reason),
                    List.of(),
                    null,
                    null);
        }

        private static PolicyEvaluation indeterminate(String reason) {
            return new PolicyEvaluation(
                    LayerDecision.of(
                            AuthorizationLayer.L4_POLICY,
                            AuthorizationEffect.INDETERMINATE,
                            reason),
                    List.of(),
                    null,
                    null);
        }
    }

    private record VerifiedChallenge(
            UUID challengeId, Long policyId, long policyVersion, String snapshotVersion) {
        private VerifiedChallenge {
            Objects.requireNonNull(challengeId, "challengeId");
        }

        /** challenge 已与完整请求摘要绑定；仅在同一快照且原确认策略版本仍存在时放行。 */
        private boolean matchesSnapshot(AuthorizationPolicy.Snapshot currentSnapshot) {
            if (!Objects.equals(snapshotVersion, currentSnapshot.version())) {
                return false;
            }
            return currentSnapshot.policies().stream()
                    .anyMatch(
                            policy ->
                                    Objects.equals(policy.id(), policyId)
                                            && policy.version() == policyVersion);
        }
    }

    private record ChallengeResult(LayerDecision layer, UUID challengeId) {}
}
