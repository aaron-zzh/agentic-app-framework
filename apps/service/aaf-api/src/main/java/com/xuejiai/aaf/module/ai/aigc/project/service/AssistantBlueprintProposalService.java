package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizationGrant;
import com.xuejiai.aaf.framework.intelligent.agent.port.AuthorizationGrantPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.FunctionPermissionChecker;
import com.xuejiai.aaf.module.ai.aigc.AigcAuthorities;
import com.xuejiai.aaf.module.ai.aigc.AigcCanonicalRequest;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintCreationContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintProposalCommands;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintProposalInput;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintProposalView;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AssistantBlueprintProposal;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AssistantBlueprintProposalRepository;

/** Assistant Blueprint Proposal 的唯一状态机与安全边界。 */
@Service
public class AssistantBlueprintProposalService {

    public static final String VALIDATOR_VERSION = "3";
    private static final int EXPIRY_HOURS = 24;
    private static final String TASK_SCOPE = "TASK";

    private final AssistantBlueprintProposalRepository repository;
    private final AigcProjectRepository projects;
    private final BlueprintSnapshotNormalizer normalizer;
    private final BlueprintSnapshotValidator validator;
    private final OperatorContext operatorContext;
    private final FunctionPermissionChecker permissions;
    private final AiCreditGuard creditGuard;
    private final AuthorizationGrantPort authorizationGrants;

    public AssistantBlueprintProposalService(
            AssistantBlueprintProposalRepository repository,
            AigcProjectRepository projects,
            BlueprintSnapshotNormalizer normalizer,
            BlueprintSnapshotValidator validator,
            OperatorContext operatorContext,
            FunctionPermissionChecker permissions,
            AiCreditGuard creditGuard,
            AuthorizationGrantPort authorizationGrants) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.projects = Objects.requireNonNull(projects, "projects 不能为空");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer 不能为空");
        this.validator = Objects.requireNonNull(validator, "validator 不能为空");
        this.operatorContext = Objects.requireNonNull(operatorContext, "operatorContext 不能为空");
        this.permissions = Objects.requireNonNull(permissions, "permissions 不能为空");
        this.creditGuard = Objects.requireNonNull(creditGuard, "creditGuard 不能为空");
        this.authorizationGrants =
                Objects.requireNonNull(authorizationGrants, "authorizationGrants 不能为空");
    }

    public AssistantBlueprintCreationContext freezeCreationContext(
            AssistantBlueprintCreationContext creationContext) {
        return normalizer.normalizeCreationContext(creationContext);
    }

    @Transactional
    public AssistantBlueprintProposalView createPlaceholder(
            String planningId,
            String proposalId,
            AssistantBlueprintCreationContext creationContext,
            String instruction,
            String assistantId,
            String taskId) {
        var identity = currentIdentity();
        requireProjectCreate(identity.ownerId());
        var frozenCreationContext = normalizer.normalizeCreationContext(creationContext);
        var proposal = new AssistantBlueprintProposal();
        proposal.setOrgId(identity.orgId());
        proposal.setWorkspaceId(identity.workspaceId());
        proposal.setOwnerId(identity.ownerId());
        proposal.setPlanningId(text(planningId, "planningId", 64));
        proposal.setProposalId(text(proposalId, "proposalId", 128));
        proposal.setProposalVersion(1);
        proposal.setProjectName(text(frozenCreationContext.name(), "creationContext.name", 200));
        proposal.setCreationContext(frozenCreationContext);
        proposal.setPlanningInstruction(text(instruction, "instruction", 4000));
        proposal.setAssistantId(text(assistantId, "assistantId", 128));
        proposal.setStatus(Status.PLANNING.name());
        proposal.setSourceTaskId(text(taskId, "taskId", 128));
        proposal.setValidatorVersion(VALIDATOR_VERSION);
        proposal.setValidationResult(Map.of("valid", false, "state", "PLANNING"));
        proposal.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS));
        return toView(repository.save(proposal));
    }

    @Transactional(noRollbackFor = ExpiredProposalException.class)
    public void authorizePlanningTask(String taskId) {
        var proposal =
                repository
                        .findLockedBySourceTaskId(text(taskId, "taskId", 128))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND,
                                                "规划 Task 未绑定 Assistant Blueprint Proposal"));
        var identity = currentIdentity();
        requireScope(proposal, identity.ownerId(), identity.orgId(), identity.workspaceId());
        requireProjectCreate(identity.ownerId());
        requireNotExpired(proposal);

        var action = SubmitAssistantBlueprintProposalToolHandler.TOOL_NAME;
        var tenantId = new TenantId(proposal.getOrgId().toString());
        var canonicalTaskId = new TaskId(proposal.getSourceTaskId());
        var expiresAt = proposal.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
        var grantedAt =
                proposal.getExpiresAt()
                        .minusHours(EXPIRY_HOURS)
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
        var grantId = blueprintToolGrantId(tenantId, canonicalTaskId, action);
        authorizationGrants.grant(
                new AuthorizationGrant(
                        grantId,
                        tenantId,
                        canonicalTaskId,
                        action,
                        action,
                        TASK_SCOPE,
                        expiresAt,
                        Map.of(),
                        true,
                        null,
                        identity.ownerId().toString(),
                        grantedAt,
                        null));
    }

    @Transactional(noRollbackFor = ExpiredProposalException.class)
    public AssistantBlueprintProposalView cancel(
            AssistantBlueprintProposalCommands.Cancel command) {
        Objects.requireNonNull(command, "command 不能为空");
        var proposal = requireLocked(command.proposalId(), command.proposalVersion());
        var identity = currentIdentity();
        requireScope(proposal, identity.ownerId(), identity.orgId(), identity.workspaceId());
        requireProjectCreate(identity.ownerId());
        requireNotExpired(proposal);
        if (!Objects.equals(proposal.getProposalHash(), command.proposalHash())) {
            throw new BusinessException(409, "Blueprint Proposal hash 已失效");
        }
        if (!java.util.Set.of(
                        Status.PLANNING.name(),
                        Status.AWAITING_CONFIRMATION.name(),
                        Status.CONFIRMED.name())
                .contains(proposal.getStatus())) {
            throw new BusinessException(409, "当前 Blueprint Proposal 状态不可取消");
        }

        proposal.setStatus("CANCELED");
        var canceled = repository.save(proposal);
        var action = SubmitAssistantBlueprintProposalToolHandler.TOOL_NAME;
        var tenantId = new TenantId(proposal.getOrgId().toString());
        var canonicalTaskId = new TaskId(proposal.getSourceTaskId());
        authorizationGrants.revoke(
                tenantId,
                blueprintToolGrantId(tenantId, canonicalTaskId, action),
                identity.ownerId().toString(),
                java.time.Instant.now());
        return toView(canceled);
    }

    private String blueprintToolGrantId(TenantId tenantId, TaskId taskId, String action) {
        return AigcCanonicalRequest.canonicalSha256(
                Map.of(
                        "tenantId", tenantId.value(),
                        "taskId", taskId.value(),
                        "action", action,
                        "resource", action,
                        "scope", TASK_SCOPE));
    }

    @Transactional(noRollbackFor = ExpiredProposalException.class)
    public AssistantBlueprintProposalView createRevision(
            AssistantBlueprintProposalCommands.Revise command, String planningId, String taskId) {
        Objects.requireNonNull(command, "command 不能为空");
        var current = requireLocked(command.proposalId(), command.proposalVersion());
        requireCurrentScope(current);
        requireNotExpired(current);
        requireHash(current, command.proposalHash());
        if (!SetLike.REVISABLE.contains(current.getStatus())) {
            throw new BusinessException(409, "当前 Blueprint Proposal 状态不可修订");
        }
        current.setStatus(Status.SUPERSEDED.name());
        repository.save(current);

        var next = new AssistantBlueprintProposal();
        next.setOrgId(current.getOrgId());
        next.setWorkspaceId(current.getWorkspaceId());
        next.setOwnerId(current.getOwnerId());
        next.setPlanningId(text(planningId, "planningId", 64));
        next.setProposalId(current.getProposalId());
        next.setProposalVersion(current.getProposalVersion() + 1);
        next.setProjectName(current.getProjectName());
        next.setCreationContext(current.getCreationContext());
        next.setPlanningInstruction(text(command.instruction(), "instruction", 4000));
        next.setAssistantId(current.getAssistantId());
        next.setStatus(Status.PLANNING.name());
        next.setSourceTaskId(text(taskId, "taskId", 128));
        next.setValidatorVersion(VALIDATOR_VERSION);
        next.setValidationResult(Map.of("valid", false, "state", "PLANNING"));
        next.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS));
        return toView(repository.save(next));
    }

    @Transactional(noRollbackFor = ExpiredProposalException.class)
    public RevisionSeed revisionSeed(AssistantBlueprintProposalCommands.Revise command) {
        Objects.requireNonNull(command, "command 不能为空");
        var proposal = requireLocked(command.proposalId(), command.proposalVersion());
        requireCurrentScope(proposal);
        requireNotExpired(proposal);
        requireHash(proposal, command.proposalHash());
        if (!SetLike.REVISABLE.contains(proposal.getStatus())) {
            throw new BusinessException(409, "当前 Blueprint Proposal 状态不可修订");
        }
        return new RevisionSeed(
                proposal.getProposalId(),
                proposal.getProposalVersion() + 1,
                proposal.getCreationContext(),
                proposal.getAssistantId());
    }

    @Transactional(noRollbackFor = ExpiredProposalException.class)
    public AssistantBlueprintProposalView fillFromTrustedTask(
            String taskId,
            String executionId,
            Long ownerId,
            Long orgId,
            Long workspaceId,
            AssistantBlueprintProposalInput rawProposal) {
        var proposal =
                repository
                        .findLockedBySourceTaskId(text(taskId, "taskId", 128))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND,
                                                "规划 Task 未绑定 Assistant Blueprint Proposal"));
        requireScope(proposal, ownerId, orgId, workspaceId);
        requireProjectCreate(ownerId);
        requireNotExpired(proposal);
        if (!Status.PLANNING.name().equals(proposal.getStatus())) {
            throw new BusinessException(409, "规划 Task 已提交过 Blueprint Proposal");
        }
        var sourceRef = sourceRef(proposal);
        var creationContext = normalizer.normalizeCreationContext(proposal.getCreationContext());
        var normalized = normalizer.normalizeAssistant(rawProposal, sourceRef, creationContext);
        validator.validate(normalized);
        var semanticCatalogVersion = semanticCatalogVersion(normalized);
        var estimatedCredits = normalizer.estimatedCredits(normalized);
        var proposalHash =
                proposalHash(
                        proposal.getProposalId(),
                        proposal.getProposalVersion(),
                        normalized,
                        creationContext,
                        semanticCatalogVersion,
                        estimatedCredits);
        proposal.setRawProposal(rawProposal);
        proposal.setNormalizedSnapshot(normalized);
        proposal.setProposalHash(proposalHash);
        proposal.setSemanticCatalogVersion(semanticCatalogVersion);
        proposal.setEstimatedCredits(estimatedCredits);
        proposal.setSourceExecutionId(text(executionId, "executionId", 128));
        proposal.setValidationResult(
                Map.of(
                        "valid",
                        true,
                        "validatorVersion",
                        VALIDATOR_VERSION,
                        "snapshotHash",
                        validator.canonicalHash(normalized),
                        "semanticCatalogVersion",
                        semanticCatalogVersion));
        proposal.setStatus(Status.AWAITING_CONFIRMATION.name());
        return toView(repository.save(proposal));
    }

    @Transactional(noRollbackFor = ExpiredProposalException.class)
    public AssistantBlueprintProposalView confirm(
            AssistantBlueprintProposalCommands.Confirm command) {
        Objects.requireNonNull(command, "command 不能为空");
        var proposal = requireLocked(command.proposalId(), command.proposalVersion());
        var identity = currentIdentity();
        requireProjectCreate(identity.ownerId());
        requireCurrentScope(proposal);
        requireNotExpired(proposal);
        requireHash(proposal, command.proposalHash());
        if (Status.CONFIRMED.name().equals(proposal.getStatus())) {
            return toView(proposal);
        }
        if (!Status.AWAITING_CONFIRMATION.name().equals(proposal.getStatus())) {
            throw new BusinessException(409, "当前 Blueprint Proposal 状态不可确认");
        }
        proposal.setStatus(Status.CONFIRMED.name());
        proposal.setConfirmedBy(identity.ownerId());
        proposal.setConfirmedByType("HUMAN");
        proposal.setConfirmedTime(LocalDateTime.now());
        proposal.setConfirmationReceiptHash(confirmationReceiptHash(proposal, identity.ownerId()));
        return toView(repository.save(proposal));
    }

    @Transactional
    public List<AssistantBlueprintProposalView> listCurrent() {
        var identity = currentIdentity();
        var proposals =
                repository
                        .findByOwnerIdAndOrgIdAndWorkspaceIdOrderByCreateTimeDescProposalVersionDesc(
                                identity.ownerId(), identity.orgId(), identity.workspaceId());
        proposals.forEach(this::expireIfNeeded);
        repository.saveAll(proposals);
        return proposals.stream().map(this::toView).toList();
    }

    /** 物化事务内重新加载、锁定并重算全部服务端事实。 */
    @Transactional(noRollbackFor = ExpiredProposalException.class)
    public Materialization resolveForMaterialization(
            AssistantBlueprintProposalCommands.Reference reference) {
        Objects.requireNonNull(reference, "reference 不能为空");
        var proposal = requireLocked(reference.proposalId(), reference.proposalVersion());
        var identity = currentIdentity();
        requireProjectCreate(identity.ownerId());
        requireCurrentScope(proposal);
        requireHash(proposal, reference.proposalHash());
        requireNotExpired(proposal);
        var creationContext = normalizer.normalizeCreationContext(proposal.getCreationContext());
        if (proposal.getLinkedProjectId() != null) {
            var linked =
                    projects.findById(proposal.getLinkedProjectId())
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    GlobalErrorCode.NOT_FOUND, "Proposal 关联项目不存在"));
            requireLinkedProjectScope(linked, proposal);
            return new Materialization(
                    proposal, linked, proposal.getNormalizedSnapshot(), creationContext, false);
        }
        if (!Status.CONFIRMED.name().equals(proposal.getStatus())
                || proposal.getConfirmedBy() == null
                || proposal.getConfirmedByType() == null
                || proposal.getConfirmedTime() == null
                || proposal.getConfirmationReceiptHash() == null
                || !proposal.getConfirmationReceiptHash()
                        .equals(confirmationReceiptHash(proposal, proposal.getConfirmedBy()))) {
            throw new BusinessException(409, "Blueprint Proposal 尚未有效确认");
        }
        if (proposal.getRawProposal() == null || proposal.getNormalizedSnapshot() == null) {
            throw new BusinessException(409, "Blueprint Proposal 缺少已验证结构");
        }
        var recalculated =
                normalizer.normalizeAssistant(
                        proposal.getRawProposal(), sourceRef(proposal), creationContext);
        validator.validate(recalculated);
        var semanticCatalogVersion = semanticCatalogVersion(recalculated);
        var estimatedCredits = normalizer.estimatedCredits(recalculated);
        var recalculatedHash =
                proposalHash(
                        proposal.getProposalId(),
                        proposal.getProposalVersion(),
                        recalculated,
                        creationContext,
                        semanticCatalogVersion,
                        estimatedCredits);
        if (!Objects.equals(recalculatedHash, proposal.getProposalHash())
                || !Objects.equals(
                        validator.canonicalHash(recalculated),
                        validator.canonicalHash(proposal.getNormalizedSnapshot()))
                || !Objects.equals(semanticCatalogVersion, proposal.getSemanticCatalogVersion())) {
            throw new BusinessException(409, "Blueprint Proposal 引用版本或发布态已变化，请重新规划");
        }
        var budget =
                estimatedCredits == null
                        ? 0L
                        : estimatedCredits.setScale(0, RoundingMode.CEILING).longValueExact();
        if (!creditGuard.hasBudget(identity.ownerId(), budget)) {
            throw new BusinessException(409, "当前积分预算不足以物化 Blueprint Proposal");
        }
        return new Materialization(proposal, null, recalculated, creationContext, true);
    }

    /** 与项目创建处于同一事务；锁定记录只能首次回填一个项目。 */
    @Transactional
    public void linkMaterialized(AssistantBlueprintProposal proposal, Long projectId) {
        if (proposal.getLinkedProjectId() != null) {
            if (!Objects.equals(proposal.getLinkedProjectId(), projectId)) {
                throw new BusinessException(409, "Blueprint Proposal 已物化为其他项目");
            }
            return;
        }
        proposal.setLinkedProjectId(projectId);
        proposal.setStatus(Status.MATERIALIZED.name());
        repository.save(proposal);
    }

    @Transactional
    public void markPlanningFailed(String taskId, String reason) {
        repository
                .findLockedBySourceTaskId(taskId)
                .ifPresent(
                        proposal -> {
                            if (Status.PLANNING.name().equals(proposal.getStatus())) {
                                proposal.setStatus(Status.FAILED.name());
                                proposal.setValidationResult(
                                        Map.of(
                                                "valid", false, "state", "FAILED", "reason",
                                                reason));
                                repository.save(proposal);
                            }
                        });
    }

    private String confirmationReceiptHash(AssistantBlueprintProposal proposal, Long confirmedBy) {
        return AigcCanonicalRequest.canonicalSha256(
                Map.of(
                        "proposalId", proposal.getProposalId(),
                        "proposalVersion", proposal.getProposalVersion(),
                        "proposalHash", proposal.getProposalHash(),
                        "confirmedBy", confirmedBy));
    }

    private String semanticCatalogVersion(
            com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload
                    payload) {
        var semantics = new LinkedHashMap<String, Object>();
        semantics.put("projectTypeCode", payload.projectSpecification().projectTypeCode());
        semantics.put("projectTypeVersion", payload.projectSpecification().projectTypeVersion());
        semantics.put(
                "objects",
                payload.objectSpecs().stream()
                        .map(
                                object ->
                                        Map.of(
                                                "key", object.stableKey(),
                                                "type", object.objectType(),
                                                "semanticRole", object.semanticRole(),
                                                "outputContract", object.outputContract()))
                        .toList());
        semantics.put(
                "relations",
                payload.relationSpecs().stream()
                        .map(
                                relation ->
                                        Map.of(
                                                "type", relation.relationType(),
                                                "layer", relation.canonicalLayer(),
                                                "direction", relation.direction()))
                        .toList());
        semantics.put(
                "actions", payload.actions().stream().map(value -> value.actionKey()).toList());
        return AigcCanonicalRequest.canonicalSha256(semantics);
    }

    private String proposalHash(
            String proposalId,
            Integer proposalVersion,
            com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload payload,
            AssistantBlueprintCreationContext creationContext,
            String semanticCatalogVersion,
            java.math.BigDecimal estimatedCredits) {
        var value = new LinkedHashMap<String, Object>();
        value.put("proposalId", proposalId);
        value.put("proposalVersion", proposalVersion);
        value.put("snapshotHash", validator.canonicalHash(payload));
        value.put("creationContextHash", AigcCanonicalRequest.canonicalSha256(creationContext));
        value.put("semanticCatalogVersion", semanticCatalogVersion);
        value.put("estimatedCredits", estimatedCredits);
        return AigcCanonicalRequest.canonicalSha256(value);
    }

    private AssistantBlueprintProposal requireLocked(String proposalId, Integer proposalVersion) {
        if (proposalVersion == null || proposalVersion < 1) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "proposalVersion 非法");
        }
        return repository
                .findLocked(text(proposalId, "proposalId", 128), proposalVersion)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.NOT_FOUND,
                                        "Assistant Blueprint Proposal 不存在"));
    }

    private void requireHash(AssistantBlueprintProposal proposal, String providedHash) {
        if (proposal.getProposalHash() == null
                || !proposal.getProposalHash().equals(text(providedHash, "proposalHash", 64))) {
            throw new BusinessException(409, "Blueprint Proposal hash 已失效");
        }
    }

    private void requireNotExpired(AssistantBlueprintProposal proposal) {
        expireIfNeeded(proposal);
        if (Status.EXPIRED.name().equals(proposal.getStatus())) {
            repository.save(proposal);
            throw new ExpiredProposalException();
        }
    }

    private void expireIfNeeded(AssistantBlueprintProposal proposal) {
        if (proposal.getExpiresAt().isBefore(LocalDateTime.now())
                && SetLike.EXPIRABLE.contains(proposal.getStatus())) {
            proposal.setStatus(Status.EXPIRED.name());
        }
    }

    private void requireScope(
            AssistantBlueprintProposal proposal, Long ownerId, Long orgId, Long workspaceId) {
        if (ownerId == null || orgId == null || workspaceId == null) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "Blueprint Proposal 缺少可信作用域");
        }
        if (!Objects.equals(proposal.getOwnerId(), ownerId)
                || !Objects.equals(proposal.getOrgId(), orgId)
                || !Objects.equals(proposal.getWorkspaceId(), workspaceId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权访问该 Blueprint Proposal");
        }
    }

    private void requireCurrentScope(AssistantBlueprintProposal proposal) {
        var identity = currentIdentity();
        requireScope(proposal, identity.ownerId(), identity.orgId(), identity.workspaceId());
    }

    private void requireLinkedProjectScope(
            AigcProject project, AssistantBlueprintProposal proposal) {
        if (!Objects.equals(project.getOwnerId(), proposal.getOwnerId())
                || !Objects.equals(project.getOrgId(), proposal.getOrgId())
                || !Objects.equals(project.getWorkspaceId(), proposal.getWorkspaceId())) {
            throw new BusinessException(409, "Proposal 关联项目范围不一致");
        }
    }

    private void requireProjectCreate(Long ownerId) {
        if (!permissions.hasPermission(ownerId, AigcAuthorities.PROJECT_CREATE)) {
            throw new AccessDeniedException("缺少项目创建权限");
        }
    }

    private Identity currentIdentity() {
        var ownerId = operatorContext.currentOwnerId().orElseThrow();
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId = OrgContext.getCurrentWorkspaceId();
        if (orgId == null || workspaceId == null) {
            throw new AccessDeniedException("缺少组织或工作区上下文");
        }
        return new Identity(ownerId, orgId, workspaceId);
    }

    private String sourceRef(AssistantBlueprintProposal proposal) {
        return "assistant-proposal:%s@%d"
                .formatted(proposal.getProposalId(), proposal.getProposalVersion());
    }

    private AssistantBlueprintProposalView toView(AssistantBlueprintProposal proposal) {
        return new AssistantBlueprintProposalView(
                proposal.getPlanningId(),
                proposal.getProposalId(),
                proposal.getProposalVersion(),
                proposal.getProposalHash(),
                proposal.getProjectName(),
                proposal.getCreationContext(),
                proposal.getStatus(),
                proposal.getNormalizedSnapshot(),
                proposal.getValidationResult(),
                proposal.getSourceTaskId(),
                proposal.getSourceExecutionId(),
                proposal.getEstimatedCredits(),
                proposal.getExpiresAt(),
                proposal.getConfirmedTime(),
                proposal.getLinkedProjectId());
    }

    private static String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        var normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    public static final class ExpiredProposalException extends BusinessException {
        public ExpiredProposalException() {
            super(409, "Blueprint Proposal 已过期");
        }
    }

    public record RevisionSeed(
            String proposalId,
            Integer proposalVersion,
            AssistantBlueprintCreationContext creationContext,
            String assistantId) {}

    public record Materialization(
            AssistantBlueprintProposal proposal,
            AigcProject linkedProject,
            com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload snapshot,
            AssistantBlueprintCreationContext creationContext,
            boolean create) {}

    private record Identity(Long ownerId, Long orgId, Long workspaceId) {}

    private enum Status {
        PLANNING,
        AWAITING_CONFIRMATION,
        CONFIRMED,
        EXPIRED,
        SUPERSEDED,
        MATERIALIZED,
        FAILED
    }

    private static final class SetLike {
        private static final java.util.Set<String> REVISABLE =
                java.util.Set.of(Status.AWAITING_CONFIRMATION.name(), Status.CONFIRMED.name());
        private static final java.util.Set<String> EXPIRABLE =
                java.util.Set.of(
                        Status.PLANNING.name(),
                        Status.AWAITING_CONFIRMATION.name(),
                        Status.CONFIRMED.name());

        private SetLike() {}
    }
}
