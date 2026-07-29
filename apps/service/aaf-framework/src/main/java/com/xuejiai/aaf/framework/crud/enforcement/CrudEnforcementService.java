package com.xuejiai.aaf.framework.crud.enforcement;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceExposure;
import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.scope.ScopeContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionContextHolder;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationChallengeRequiredException;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationDecision;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationEffect;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationLayer;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationRequest;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationTarget;
import com.xuejiai.aaf.framework.security.authorization.continuation.AuthorizationContinuationResolver;

import lombok.RequiredArgsConstructor;

/** BaseCrud 的统一策略执行点；任何缺失主体、租户或策略均拒绝。 */
@Service
@RequiredArgsConstructor
public final class CrudEnforcementService {

    public static final String FACT_NAMESPACE = "crud";
    public static final String FACT_CURRENT = "current";
    public static final String FACT_PROPOSED = "proposed";
    public static final String FACT_CREATED = "created";
    public static final String FACT_CREATED_BATCH = "createdBatch";
    public static final String FACT_BATCH_SIZE = "batchSize";
    public static final String FACT_PAYLOAD_DIGEST = "payloadDigest";
    public static final String FACT_PAYLOAD_DIGESTS = "payloadDigests";
    public static final String FACT_COMMAND_TYPE = "commandType";
    public static final String FACT_MODIFIED_FIELDS = "modifiedFields";

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);

    private final OperatorContext operatorContext;
    private final AuthorizationService authorizationService;

    /** 列表、批量和无对象操作的请求级授权；L4 只评估请求事实，不逐行评估结果。 */
    public <E extends BaseEntity> CrudEnforcementDecision<E> enforceRequest(
            CrudResourceCatalogEntry entry, CrudOperation operation, AccessMode accessMode) {
        return enforcePreflight(entry, operation, accessMode, true, false);
    }

    /** 单对象 CRUD 的前置授权；对象尚未加载，因此只执行 L1/L3，不提前执行 L4。 */
    public <E extends BaseEntity> CrudEnforcementDecision<E> enforceObjectPreflight(
            CrudResourceCatalogEntry entry, CrudOperation operation, AccessMode accessMode) {
        return enforcePreflight(entry, operation, accessMode, false, false);
    }

    /** 具名自定义 UPDATE 的固定前置授权；使用 UPDATE permission，但不暴露标准 PUT capability。 */
    public <E extends BaseEntity> CrudEnforcementDecision<E> enforceCustomUpdatePreflight(
            CrudResourceCatalogEntry entry,
            AccessMode accessMode,
            String commandType,
            java.util.Set<String> modifiedFields) {
        entry.definition().mutation().requireCustomUpdateCommand(commandType, modifiedFields);
        return enforcePreflight(entry, CrudOperation.UPDATE, accessMode, false, true);
    }

    /** 以服务端加载实体的 CURRENT 快照执行唯一一次对象级 L4；关系回退要求合并到同一次 PDP。 */
    public boolean allowsCurrentTarget(
            CrudResourceCatalogEntry entry,
            CrudEnforcementDecision<?> preflight,
            BaseEntity entity,
            Map<String, Object> currentAttributes,
            AuthorizationPlan.RelationRequirement relationRequirement) {
        var objectId = requireObjectId(entity);
        if (relationRequirement != null && !objectId.equals(relationRequirement.objectId())) {
            return false;
        }
        var immutableCurrent = immutableAttributes(currentAttributes);
        requireBoundObjectId(objectId, immutableCurrent);
        var targetFacts = new LinkedHashMap<String, Object>();
        targetFacts.put(FACT_CURRENT, immutableCurrent);
        return isAllowed(
                targetRequest(entry, preflight, objectId, targetFacts, relationRequirement));
    }

    /** UPDATE 保存前，以可信快照、原命令摘要、命令类型和显式字段执行唯一一次对象级 L4。 */
    public void requireUpdatedTarget(
            CrudResourceCatalogEntry entry,
            CrudEnforcementDecision<?> preflight,
            String objectId,
            Map<String, Object> currentAttributes,
            Map<String, Object> proposedAttributes,
            String payloadDigest,
            String commandType,
            java.util.Set<String> modifiedFields) {
        if (objectId == null || objectId.isBlank()) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        var immutableCurrent = immutableAttributes(currentAttributes);
        var immutableProposed = immutableAttributes(proposedAttributes);
        requireBoundObjectId(objectId, immutableCurrent);
        requireBoundObjectId(objectId, immutableProposed);
        var targetFacts = new LinkedHashMap<String, Object>();
        targetFacts.put(FACT_CURRENT, immutableCurrent);
        targetFacts.put(FACT_PROPOSED, immutableProposed);
        targetFacts.put(FACT_PAYLOAD_DIGEST, requirePayloadDigest(payloadDigest));
        targetFacts.put(FACT_COMMAND_TYPE, requireCommandType(commandType));
        targetFacts.put(FACT_MODIFIED_FIELDS, requireModifiedFields(modifiedFields));
        requireAuthorized(targetRequest(entry, preflight, objectId, targetFacts, null));
    }

    /** CREATE 已应用租户和 owner 但尚未保存时，以服务端 CREATED 和原始请求摘要执行唯一一次对象级 L4。 */
    public void requireCreatedTarget(
            CrudResourceCatalogEntry entry,
            CrudEnforcementDecision<?> preflight,
            BaseEntity entity,
            Map<String, Object> createdAttributes,
            String payloadDigest) {
        var immutableCreated = immutableAttributes(createdAttributes);
        if (entity == null) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        var objectId = entity.getId() == null ? null : requireObjectId(entity);
        if (objectId != null) {
            requireBoundObjectId(objectId, immutableCreated);
        }
        var targetFacts = new LinkedHashMap<String, Object>();
        targetFacts.put(FACT_CREATED, immutableCreated);
        targetFacts.put(FACT_PAYLOAD_DIGEST, requirePayloadDigest(payloadDigest));
        requireAuthorized(targetRequest(entry, preflight, objectId, targetFacts, null));
    }

    /** 批量 CREATE 将全部服务端 CREATED 快照和对应请求摘要绑定到一次 target PDP。 */
    public void requireCreatedBatchTarget(
            CrudResourceCatalogEntry entry,
            CrudEnforcementDecision<?> preflight,
            List<Map<String, Object>> createdAttributes,
            List<String> payloadDigests) {
        if (createdAttributes == null
                || payloadDigests == null
                || createdAttributes.isEmpty()
                || createdAttributes.size() != payloadDigests.size()) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        var immutableCreated = createdAttributes.stream().map(this::immutableAttributes).toList();
        var immutableDigests = payloadDigests.stream().map(this::requirePayloadDigest).toList();
        var targetFacts = new LinkedHashMap<String, Object>();
        targetFacts.put(FACT_CREATED_BATCH, immutableCreated);
        targetFacts.put(FACT_BATCH_SIZE, immutableCreated.size());
        targetFacts.put(FACT_PAYLOAD_DIGESTS, immutableDigests);
        requireAuthorized(targetRequest(entry, preflight, null, targetFacts, null));
    }

    private <E extends BaseEntity> CrudEnforcementDecision<E> enforcePreflight(
            CrudResourceCatalogEntry entry,
            CrudOperation operation,
            AccessMode accessMode,
            boolean requestLevelPolicy,
            boolean customUpdate) {
        requireAccessModeContext(accessMode);
        var definition = entry.definition();
        if (!customUpdate) {
            requireOperation(definition, operation);
        }
        var subjectId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED));
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId = OrgContext.getCurrentWorkspaceId();

        var authorizationDecision =
                requirePreflightAuthorization(
                        definition,
                        operation,
                        accessMode,
                        subjectId,
                        orgId,
                        workspaceId,
                        requestLevelPolicy);
        var dataConstraint =
                requireCrudDataConstraint(authorizationDecision, definition.entitySlug());

        var fieldPolicy = applyFieldConstraint(entry, dataConstraint);
        var tenantScope = this.<E>tenantSpec(definition.tenantScope(), orgId, workspaceId);
        var recordScope = dataConstraint.<E>typedRecordScope();
        var personalScope = this.<E>personalSpec(definition.personalScope(), subjectId, accessMode);
        var scope = Specification.<E>allOf(tenantScope, recordScope, personalScope);
        return new CrudEnforcementDecision<>(
                subjectId,
                orgId,
                workspaceId,
                operation,
                accessMode,
                tenantScope,
                scope,
                fieldPolicy,
                dataConstraint.accessVersion());
    }

    private AuthorizationRequest targetRequest(
            CrudResourceCatalogEntry entry,
            CrudEnforcementDecision<?> preflight,
            String objectId,
            Map<String, Object> targetFacts,
            AuthorizationPlan.RelationRequirement relationRequirement) {
        var definition = entry.definition();
        var relationPlan =
                relationRequirement == null
                        ? null
                        : AuthorizationPlan.RelationPlan.all(relationRequirement);
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        relationPlan,
                        null,
                        new AuthorizationPlan.PolicyPlan());
        var crudFacts = new LinkedHashMap<String, Object>();
        crudFacts.put("phase", "TARGET");
        crudFacts.putAll(targetFacts);
        var facts =
                AuthorizationRequest.immutableData(
                        Map.of(
                                "accessMode",
                                preflight.accessMode().name(),
                                FACT_NAMESPACE,
                                crudFacts));
        return new AuthorizationRequest(
                subject(preflight.subjectId(), preflight.orgId(), preflight.workspaceId()),
                new AuthorizationTarget(
                        definition.entitySlug(),
                        preflight.operation().action().permissionSegment(),
                        objectId),
                plan,
                facts,
                CHALLENGE_TTL);
    }

    private void requireOperation(CrudResourceDefinition<?> definition, CrudOperation operation) {
        if (operation == CrudOperation.REFERENCE) {
            if (!definition.exposures().contains(CrudResourceExposure.REFERENCE)) {
                throw exception(
                        GlobalErrorCode.CRUD_OPERATION_UNSUPPORTED,
                        definition.displayName(),
                        operation.capability());
            }
            return;
        }
        if (!definition.capabilities().operations().contains(operation)) {
            throw exception(
                    GlobalErrorCode.CRUD_OPERATION_UNSUPPORTED,
                    definition.displayName(),
                    operation.capability());
        }
    }

    private AuthorizationDecision requirePreflightAuthorization(
            CrudResourceDefinition<?> definition,
            CrudOperation operation,
            AccessMode accessMode,
            Long subjectId,
            Long orgId,
            Long workspaceId,
            boolean requestLevelPolicy) {
        var actionPermission = definition.permissionCode(operation.action());
        var functionRequirement =
                accessMode == AccessMode.DEFAULT
                        ? AuthorizationPlan.FunctionRequirement.permission(actionPermission)
                        : AuthorizationPlan.FunctionRequirement.all(
                                actionPermission, definition.accessModePermissionCode(accessMode));
        var plan =
                new AuthorizationPlan(
                        functionRequirement,
                        null,
                        AuthorizationPlan.DataPlan.all(
                                CrudDataAuthorizationProvider.requirement(
                                        definition.tenantScope(), accessMode)),
                        requestLevelPolicy ? new AuthorizationPlan.PolicyPlan() : null);
        var request =
                new AuthorizationRequest(
                        subject(subjectId, orgId, workspaceId),
                        new AuthorizationTarget(
                                definition.entitySlug(),
                                operation.action().permissionSegment(),
                                null),
                        plan,
                        Map.of("accessMode", accessMode.name()),
                        CHALLENGE_TTL);
        var decision = authorize(request);
        requireAllowed(decision);
        return decision;
    }

    private void requireAccessModeContext(AccessMode accessMode) {
        if (accessMode != AccessMode.SYSTEM_JOB) {
            return;
        }
        var internal = PermissionExecutionContextHolder.get();
        if (internal == null || internal.reason() == null || internal.reason().isBlank()) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    private AuthorizationSubject subject(Long subjectId, Long orgId, Long workspaceId) {
        return new AuthorizationSubject(
                operatorContext.currentOperatorId().orElse(subjectId),
                subjectId,
                orgId,
                workspaceId);
    }

    private void requireAuthorized(AuthorizationRequest request) {
        requireAllowed(authorize(request));
    }

    private boolean isAllowed(AuthorizationRequest request) {
        var decision = authorize(request);
        if (decision != null && decision.challengeId() != null) {
            throw new AuthorizationChallengeRequiredException(decision.challengeId());
        }
        return decision != null && decision.allowed();
    }

    private AuthorizationDecision authorize(AuthorizationRequest request) {
        var continuationId = AuthorizationContinuationResolver.resolve();
        try {
            if (continuationId.isPresent()) {
                var challengeId = continuationId.orElseThrow();
                var selection = authorizationService.selectContinuation(challengeId, request);
                return switch (selection) {
                    case MATCH -> {
                        var decision = authorizationService.resume(challengeId, request);
                        if (decision != null && decision.allowed()) {
                            AuthorizationContinuationResolver.markConsumed(challengeId);
                        }
                        yield decision;
                    }
                    case NOT_MATCH -> authorizationService.authorize(request);
                    case INDETERMINATE -> null;
                };
            }
            return authorizationService.authorize(request);
        } catch (AuthorizationChallengeRequiredException cause) {
            throw cause;
        } catch (RuntimeException cause) {
            return null;
        }
    }

    private void requireAllowed(AuthorizationDecision decision) {
        if (decision != null && decision.allowed()) {
            return;
        }
        if (decision != null && decision.challengeId() != null) {
            throw new AuthorizationChallengeRequiredException(decision.challengeId());
        }
        throw exception(GlobalErrorCode.FORBIDDEN);
    }

    private CrudDataAuthorizationConstraint requireCrudDataConstraint(
            AuthorizationDecision decision, String resourceKey) {
        CrudDataAuthorizationConstraint found = null;
        for (var layer : decision.layerDecisions()) {
            if (layer.layer() != AuthorizationLayer.L3_DATA) {
                continue;
            }
            for (var item : layer.items()) {
                if (!CrudDataAuthorizationProvider.REQUIREMENT_KEY.equals(item.key())) {
                    continue;
                }
                if (layer.effect() != AuthorizationEffect.ALLOW
                        || item.effect() != AuthorizationEffect.ALLOW
                        || !(item.constraint()
                                instanceof CrudDataAuthorizationConstraint constraint)
                        || !resourceKey.equals(constraint.resourceKey())
                        || found != null) {
                    throw exception(GlobalErrorCode.FORBIDDEN);
                }
                found = constraint;
            }
        }
        if (found == null) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        return found;
    }

    private CompiledFieldPolicy applyFieldConstraint(
            CrudResourceCatalogEntry entry, CrudDataAuthorizationConstraint constraint) {
        try {
            return entry.fieldPolicy().deny(constraint.deniedFields());
        } catch (RuntimeException cause) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    private Map<String, Object> immutableAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        try {
            return AuthorizationRequest.immutableData(attributes);
        } catch (RuntimeException cause) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    private String requireObjectId(BaseEntity entity) {
        if (entity == null || entity.getId() == null || entity.getId() <= 0) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        return entity.getId().toString();
    }

    private void requireBoundObjectId(String objectId, Map<String, Object> attributes) {
        var id = attributes.get("id");
        if (!(id instanceof Number number)) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        try {
            var expected = new java.math.BigDecimal(objectId).stripTrailingZeros();
            var actual = new java.math.BigDecimal(number.toString()).stripTrailingZeros();
            if (expected.compareTo(actual) != 0) {
                throw exception(GlobalErrorCode.FORBIDDEN);
            }
        } catch (NumberFormatException cause) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    private String requirePayloadDigest(String payloadDigest) {
        if (payloadDigest == null || !payloadDigest.matches("[0-9a-fA-F]{64}")) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        return payloadDigest;
    }

    private String requireCommandType(String commandType) {
        if (commandType == null || !commandType.matches("[A-Z][A-Z0-9_]{1,127}")) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        return commandType;
    }

    private List<String> requireModifiedFields(java.util.Set<String> modifiedFields) {
        if (modifiedFields == null || modifiedFields.isEmpty()) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        var normalized =
                modifiedFields.stream()
                        .map(field -> field == null ? "" : field.trim())
                        .filter(field -> !field.isBlank())
                        .sorted()
                        .toList();
        if (normalized.size() != modifiedFields.size()) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        return normalized;
    }

    private <E extends BaseEntity> Specification<E> tenantSpec(
            TenantScope scope, Long orgId, Long workspaceId) {
        return switch (scope) {
            case GLOBAL ->
                    (root, query, cb) ->
                            cb.and(
                                    cb.isNull(root.get("orgId")),
                                    cb.isNull(root.get("workspaceId")));
            case ORG_REQUIRED -> (root, query, cb) -> cb.equal(root.get("orgId"), orgId);
            case WORKSPACE_REQUIRED ->
                    (root, query, cb) ->
                            cb.and(
                                    cb.equal(root.get("orgId"), orgId),
                                    cb.equal(root.get("workspaceId"), workspaceId));
            case ORG_SHARED_WORKSPACE_OPTIONAL ->
                    (root, query, cb) -> {
                        var org = cb.equal(root.get("orgId"), orgId);
                        var workspace =
                                workspaceId == null
                                        ? cb.isNull(root.get("workspaceId"))
                                        : cb.or(
                                                cb.isNull(root.get("workspaceId")),
                                                cb.equal(root.get("workspaceId"), workspaceId));
                        return cb.and(org, workspace);
                    };
        };
    }

    private <E extends BaseEntity> Specification<E> personalSpec(
            PersonalScope scope, Long subjectId, AccessMode accessMode) {
        if (!scope.enabled() || ScopeContext.isAllScope() || accessMode.bypassesPersonalScope()) {
            return unrestrictedSpec();
        }
        return (root, query, cb) -> cb.equal(root.get(scope.property()), subjectId);
    }

    private <E extends BaseEntity> Specification<E> unrestrictedSpec() {
        return (root, query, cb) -> null;
    }
}
