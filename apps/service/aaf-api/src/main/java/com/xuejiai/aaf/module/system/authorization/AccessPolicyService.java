package com.xuejiai.aaf.module.system.authorization;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationEffect;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPolicy;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPolicyProvider;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationRequest;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationTarget;
import com.xuejiai.aaf.framework.security.authorization.PermissionVersionService;
import com.xuejiai.aaf.framework.security.authorization.PolicyDslCompiler;
import com.xuejiai.aaf.framework.security.authorization.PolicyExpressionEvaluator;
import com.xuejiai.aaf.framework.security.authorization.PolicyFactSchema;
import com.xuejiai.aaf.module.system.ErrorCodeConstants;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** 访问策略生命周期管理与已发布快照 Provider。 */
@Service
@RequiredArgsConstructor
public class AccessPolicyService implements AuthorizationPolicyProvider {

    private static final List<String> ACTIVE_LIFECYCLES = List.of("SHADOW", "ENFORCE");

    private final AccessPolicyRepository repository;
    private final AccessPolicySnapshotRepository snapshotRepository;
    private final PermissionVersionService versionService;
    private final PolicyDslCompiler policyCompiler;
    private final PolicyExpressionEvaluator policyEvaluator;
    private final AuthorizationAuditService auditService;
    private final ConcurrentHashMap<String, AuthorizationPolicy.Snapshot> snapshotCache =
            new ConcurrentHashMap<>();

    @Transactional
    public AccessPolicyVO create(AccessPolicyCreateDTO dto) {
        validate(dto);
        var entity = new AccessPolicy();
        apply(entity, dto);
        entity.setLifecycle(AuthorizationPolicy.Lifecycle.DRAFT.name());
        return toVO(repository.save(entity));
    }

    @Transactional
    public AccessPolicyVO update(Long id, AccessPolicyCreateDTO dto) {
        validate(dto);
        var entity = getEntity(id);
        var active = ACTIVE_LIFECYCLES.contains(entity.getLifecycle());
        apply(entity, dto);
        if (active) {
            entity.setLifecycle(AuthorizationPolicy.Lifecycle.DRAFT.name());
        }
        var saved = repository.save(entity);
        if (active) {
            publishChangeAfterCommit("POLICY_DRAFTED", saved, "编辑活动策略后撤回为草稿");
        }
        return toVO(saved);
    }

    @Transactional
    public void delete(Long id) {
        var policy = getEntity(id);
        repository.delete(policy);
        publishChangeAfterCommit("POLICY_DELETED", policy, "策略删除并移出运行时快照");
    }

    @Transactional(readOnly = true)
    public List<AccessPolicyVO> list() {
        return repository.findAllByOrderByPriority().stream().map(this::toVO).toList();
    }

    @Transactional
    public AccessPolicyVO publish(Long id, AccessPolicyPublishDTO dto) {
        var lifecycle = parsePublishLifecycle(dto.mode());
        var policy = getEntity(id);
        var factSchema = factSchema(policy.getFactSchemaJson());
        policyCompiler.compile(policy.getConditionJson(), factSchema);
        var nextVersion = policy.getPublishedVersion() + 1;
        snapshotRepository.save(AccessPolicySnapshot.from(policy, nextVersion, lifecycle.name()));
        policy.setPublishedVersion(nextVersion);
        policy.setLifecycle(lifecycle.name());
        repository.save(policy);
        publishChangeAfterCommit(
                "POLICY_PUBLISHED", policy, "发布不可变策略快照，lifecycle=" + lifecycle.name());
        return toVO(policy);
    }

    @Transactional
    public AccessPolicyVO disable(Long id) {
        var policy = getEntity(id);
        policy.setLifecycle(AuthorizationPolicy.Lifecycle.DISABLED.name());
        repository.save(policy);
        publishChangeAfterCommit("POLICY_DISABLED", policy, "策略移出运行时快照");
        return toVO(policy);
    }

    @Transactional
    public AccessPolicyVO toDraft(Long id) {
        var policy = getEntity(id);
        policy.setLifecycle(AuthorizationPolicy.Lifecycle.DRAFT.name());
        repository.save(policy);
        publishChangeAfterCommit("POLICY_DRAFTED", policy, "策略撤回为草稿并移出运行时快照");
        return toVO(policy);
    }

    @Transactional(readOnly = true)
    public PolicyTestResultVO test(PolicyTestDTO dto) {
        var request =
                new AuthorizationRequest(
                        AuthorizationSubject.unresolved(),
                        new AuthorizationTarget(dto.resourceType(), dto.action(), null),
                        AuthorizationPlan.authenticated(),
                        dto.context(),
                        Duration.ofMinutes(10));
        var matched = new ArrayList<String>();
        var effects = new ArrayList<AuthorizationEffect>();
        for (var policy : repository.findAllByOrderByPriority()) {
            if (!matchesTarget(policy, request.target())) {
                continue;
            }
            var expression =
                    policyCompiler.compile(
                            policy.getConditionJson(), factSchema(policy.getFactSchemaJson()));
            if (!policyEvaluator.evaluate(expression, request)) {
                continue;
            }
            matched.add(policy.getName());
            effects.add(parseEffect(policy.getEffect()).toAuthorizationEffect());
        }
        var effect =
                effects.isEmpty()
                        ? AuthorizationEffect.NOT_APPLICABLE
                        : AuthorizationEffect.strongest(effects);
        return new PolicyTestResultVO(effect.name(), "安全 JSON DSL 测试完成", List.copyOf(matched));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationPolicy.Snapshot loadSnapshot(AuthorizationTarget target) {
        if (target == null || !target.hasPolicyTarget()) {
            throw new IllegalArgumentException("策略快照读取必须提供 resource 和 action");
        }
        var relevant =
                repository.findByLifecycleInOrderByPriority(ACTIVE_LIFECYCLES).stream()
                        .filter(policy -> matchesTarget(policy, target))
                        .toList();
        var snapshotVersion = snapshotVersion(versionService.policyVersion(), relevant);
        return snapshotCache.computeIfAbsent(
                snapshotVersion, ignored -> loadSnapshot(snapshotVersion, target, relevant));
    }

    private AuthorizationPolicy.Snapshot loadSnapshot(
            String snapshotVersion,
            AuthorizationTarget target,
            List<AccessPolicy> active) {
        if (active.isEmpty()) {
            return new AuthorizationPolicy.Snapshot(
                    snapshotVersion, PolicyFactSchema.builtInsOnly(), List.of());
        }

        var policyIds = active.stream().map(AccessPolicy::getId).toList();
        var versions = active.stream().map(AccessPolicy::getPublishedVersion).toList();
        var persisted = snapshotRepository.findByPolicyIdInAndPolicyVersionIn(policyIds, versions);
        var snapshots = new ArrayList<AccessPolicySnapshot>(active.size());
        for (var policy : active) {
            var snapshot =
                    persisted.stream()
                            .filter(
                                    candidate ->
                                            candidate.getPolicyId().equals(policy.getId())
                                                    && candidate
                                                            .getPolicyVersion()
                                                            .equals(policy.getPublishedVersion()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "已发布策略快照缺失: policyId="
                                                            + policy.getId()
                                                            + ", version="
                                                            + policy.getPublishedVersion()));
            if (!policy.getLifecycle().equals(snapshot.getLifecycle())) {
                throw new IllegalStateException("策略定义与快照生命周期不一致: " + policy.getId());
            }
            snapshots.add(snapshot);
        }

        var factSchema = mergedFactSchema(snapshots);
        var policies =
                snapshots.stream()
                        .map(snapshot -> toRuntimePolicy(snapshot, factSchema))
                        .filter(policy -> matchesTarget(policy.target(), target))
                        .toList();
        return new AuthorizationPolicy.Snapshot(snapshotVersion, factSchema, policies);
    }

    private String snapshotVersion(String globalVersion, List<AccessPolicy> active) {
        if (globalVersion == null || globalVersion.isBlank()) {
            throw new IllegalStateException("Redis 全局策略版本不能为空");
        }
        var ordered = new ArrayList<>(active);
        for (var policy : ordered) {
            if (policy == null
                    || policy.getId() == null
                    || policy.getPublishedVersion() == null
                    || policy.getLifecycle() == null
                    || policy.getLifecycle().isBlank()
                    || policy.getTargetResource() == null
                    || policy.getTargetResource().isBlank()
                    || policy.getTargetAction() == null
                    || policy.getTargetAction().isBlank()) {
                throw new IllegalStateException("活跃策略版本字段和 target 不能为空");
            }
        }
        ordered.sort((left, right) -> left.getId().compareTo(right.getId()));
        var digest = sha256();
        updateDigest(digest, globalVersion);
        for (var policy : ordered) {
            updateDigest(digest, policy.getId().toString());
            updateDigest(digest, policy.getPublishedVersion().toString());
            updateDigest(digest, policy.getLifecycle());
            updateDigest(digest, policy.getTargetResource());
            updateDigest(digest, policy.getTargetAction());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private AuthorizationPolicy toRuntimePolicy(
            AccessPolicySnapshot snapshot, PolicyFactSchema factSchema) {
        final AuthorizationPolicy.Lifecycle lifecycle;
        final AuthorizationPolicy.PolicyEffect effect;
        try {
            lifecycle = AuthorizationPolicy.Lifecycle.valueOf(snapshot.getLifecycle());
            effect = AuthorizationPolicy.PolicyEffect.valueOf(snapshot.getEffect());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("策略快照枚举损坏: " + snapshot.getId(), ex);
        }
        if (lifecycle != AuthorizationPolicy.Lifecycle.SHADOW
                && lifecycle != AuthorizationPolicy.Lifecycle.ENFORCE) {
            throw new IllegalStateException("运行时快照包含非法生命周期: " + lifecycle);
        }
        var target =
                new AuthorizationTarget(
                        requireText(snapshot.getTargetResource(), "targetResource"),
                        requireText(snapshot.getTargetAction(), "targetAction"),
                        null);
        policyCompiler.compile(snapshot.getConditionJson(), factSchema);
        return new AuthorizationPolicy(
                snapshot.getPolicyId(),
                snapshot.getPolicyVersion(),
                requireText(snapshot.getName(), "name"),
                target,
                snapshot.getPriority(),
                lifecycle,
                effect,
                snapshot.getConditionJson());
    }

    private PolicyFactSchema mergedFactSchema(List<AccessPolicySnapshot> snapshots) {
        var merged = new LinkedHashMap<String, PolicyFactSchema.ValueType>();
        for (var snapshot : snapshots) {
            declaredFactTypes(snapshot.getFactSchemaJson())
                    .forEach(
                            (name, type) -> {
                                var existing = merged.putIfAbsent(name, type);
                                if (existing != null && existing != type) {
                                    throw new IllegalStateException(
                                            "策略事实类型冲突: %s (%s/%s)"
                                                    .formatted(name, existing, type));
                                }
                            });
        }
        return new PolicyFactSchema(merged);
    }

    private void validate(AccessPolicyCreateDTO dto) {
        try {
            var factSchema = factSchema(dto.factSchema());
            policyCompiler.compile(dto.conditionJson(), factSchema);
            parseEffect(dto.effect());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    private void apply(AccessPolicy entity, AccessPolicyCreateDTO dto) {
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setConditionJson(dto.conditionJson());
        entity.setFactSchemaJson(JsonUtils.toJsonString(normalizeFactSchema(dto.factSchema())));
        entity.setEffect(parseEffect(dto.effect()).name());
        entity.setPriority(dto.priority() == null ? 100 : dto.priority());
        entity.setTargetResource(dto.targetResource());
        entity.setTargetAction(dto.targetAction());
    }

    private PolicyFactSchema factSchema(String factSchemaJson) {
        try {
            var declarations =
                    JsonUtils.parseObject(
                            factSchemaJson, new TypeReference<Map<String, String>>() {});
            if (declarations == null) {
                throw new IllegalStateException("策略事实白名单为空");
            }
            return factSchema(declarations);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("策略事实白名单损坏", ex);
        }
    }

    private PolicyFactSchema factSchema(Map<String, String> declarations) {
        return new PolicyFactSchema(declaredFactTypes(declarations));
    }

    private Map<String, PolicyFactSchema.ValueType> declaredFactTypes(String json) {
        var declarations =
                JsonUtils.parseObject(json, new TypeReference<Map<String, String>>() {});
        if (declarations == null) {
            throw new IllegalStateException("策略事实白名单为空");
        }
        return declaredFactTypes(declarations);
    }

    private Map<String, PolicyFactSchema.ValueType> declaredFactTypes(
            Map<String, String> declarations) {
        var types = new LinkedHashMap<String, PolicyFactSchema.ValueType>();
        normalizeFactSchema(declarations)
                .forEach(
                        (name, type) ->
                                types.put(name, PolicyFactSchema.ValueType.valueOf(type)));
        return Map.copyOf(types);
    }

    private Map<String, String> normalizeFactSchema(Map<String, String> declarations) {
        if (declarations == null || declarations.isEmpty()) {
            return Map.of();
        }
        var normalized = new LinkedHashMap<String, String>();
        declarations.forEach(
                (name, type) -> {
                    if (name == null || type == null) {
                        throw new IllegalArgumentException("策略事实名称和类型不能为空");
                    }
                    try {
                        normalized.put(
                                name,
                                PolicyFactSchema.ValueType.valueOf(
                                                type.trim().toUpperCase(Locale.ROOT))
                                        .name());
                    } catch (RuntimeException ex) {
                        throw new IllegalArgumentException("不支持的策略事实类型: " + type, ex);
                    }
                });
        new PolicyFactSchema(
                normalized.entrySet().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry ->
                                                PolicyFactSchema.ValueType.valueOf(
                                                        entry.getValue()))));
        return Map.copyOf(normalized);
    }

    private boolean matchesTarget(AccessPolicy policy, AuthorizationTarget target) {
        return matches(policy.getTargetResource(), target.resource())
                && matches(policy.getTargetAction(), target.action());
    }

    private boolean matchesTarget(AuthorizationTarget configured, AuthorizationTarget actual) {
        return matches(configured.resource(), actual.resource())
                && matches(configured.action(), actual.action())
                && matches(configured.objectId(), actual.objectId());
    }

    private boolean matches(String configured, String actual) {
        return configured == null || "*".equals(configured) || configured.equals(actual);
    }

    private AuthorizationPolicy.Lifecycle parsePublishLifecycle(String lifecycle) {
        try {
            var parsed =
                    AuthorizationPolicy.Lifecycle.valueOf(
                            lifecycle.trim().toUpperCase(Locale.ROOT));
            if (parsed == AuthorizationPolicy.Lifecycle.SHADOW
                    || parsed == AuthorizationPolicy.Lifecycle.ENFORCE) {
                return parsed;
            }
        } catch (RuntimeException ignored) {
            // 统一映射为参数错误，不暴露枚举解析细节。
        }
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "发布模式仅允许 SHADOW 或 ENFORCE");
    }

    private AuthorizationPolicy.PolicyEffect parseEffect(String effect) {
        try {
            return AuthorizationPolicy.PolicyEffect.valueOf(
                    effect.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "策略效果仅允许 ALLOW、DENY 或 CHALLENGE");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("策略快照字段损坏: " + field);
        }
        return value;
    }

    private AccessPolicy getEntity(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> exception(ErrorCodeConstants.ACCESS_POLICY_NOT_FOUND));
    }

    private AccessPolicyVO toVO(AccessPolicy policy) {
        return new AccessPolicyVO(
                policy.getId(),
                policy.getName(),
                policy.getDescription(),
                policy.getConditionJson(),
                normalizeFactSchema(
                        JsonUtils.parseObject(
                                policy.getFactSchemaJson(),
                                new TypeReference<Map<String, String>>() {})),
                policy.getEffect(),
                policy.getPriority(),
                policy.getTargetResource(),
                policy.getTargetAction(),
                policy.getLifecycle(),
                policy.getPublishedVersion());
    }

    private void publishChangeAfterCommit(
            String eventType, AccessPolicy policy, String reason) {
        var callback =
                (Runnable)
                        () -> {
                            try {
                                versionService.bumpPolicyVersion();
                            } finally {
                                snapshotCache.clear();
                            }
                            auditService.recordPolicyLifecycle(
                                    eventType, policy, versionService.policyVersion(), reason);
                        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            callback.run();
                        }
                    });
            return;
        }
        callback.run();
    }
}
