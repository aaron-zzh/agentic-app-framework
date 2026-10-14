package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.FunctionPermissionChecker;
import com.xuejiai.aaf.module.ai.aigc.AigcAuthorities;
import com.xuejiai.aaf.module.ai.aigc.brand.api.AigcBrandApi;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcConfigurationApi;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcConfigurationResolveCommand;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcProjectInitializationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedConfiguration;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcUploadedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcBlueprintSourceType;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverMode;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverStatus;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentContextMode;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentInput;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentReferenceType;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMaterializeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectSpecification;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintProposalCommands;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectChannelRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDocumentRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectMediaRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectObject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectProfileRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRelation;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRevision;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCoverGenerationRequestedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectChannelRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectDocumentRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectMediaRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectObjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectProfileRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRelationRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRevisionRepository;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** 将已确认的 CATALOG 或 ASSISTANT_GENERATED 快照物化为唯一项目聚合及 revision 1。 */
@Service
@RequiredArgsConstructor
public class AigcProjectMaterializer {

    private final AigcConfigurationApi configurationApi;
    private final AigcBrandApi brandApi;
    private final AigcProjectRepository projectRepository;
    private final ProjectBlueprintSnapshotService snapshotService;
    private final AigcProjectProfileRefRepository profileRefRepository;
    private final AigcProjectChannelRefRepository channelRefRepository;
    private final AigcProjectDocumentRefRepository documentRefRepository;
    private final AigcProjectObjectRepository objectRepository;
    private final AigcProjectRelationRepository relationRepository;
    private final AigcProjectRevisionRepository revisionRepository;
    private final DocumentVersionApi documentVersionApi;
    private final AigcMediaApi mediaApi;
    private final AigcProjectMediaRefRepository mediaRefRepository;
    private final AigcProjectGenerationProposalService generationProposals;
    private final AssistantBlueprintProposalService assistantBlueprintProposals;
    private final OperatorContext operatorContext;
    private final FunctionPermissionChecker permissionChecker;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(noRollbackFor = AssistantBlueprintProposalService.ExpiredProposalException.class)
    public AigcProject materialize(AigcProjectMaterializeCommand command) {
        var currentOwnerId = operatorContext.currentOwnerId().orElseThrow();
        if (!permissionChecker.hasPermission(currentOwnerId, AigcAuthorities.PROJECT_CREATE)) {
            throw new AccessDeniedException("缺少项目创建权限");
        }
        var plan = materializationPlan(command);
        var currentOrgId = OrgContext.getCurrentOrgId();
        var currentWorkspaceId = OrgContext.getCurrentWorkspaceId();
        if (currentOrgId == null
                || currentWorkspaceId == null
                || !Objects.equals(currentWorkspaceId, plan.workspaceId())) {
            throw new AccessDeniedException("项目物化组织或工作区范围不一致");
        }
        if (plan.linkedProject() != null) {
            return plan.linkedProject();
        }
        var projectName = requireProjectName(plan.projectName());
        validateCover(plan);
        var payload = plan.snapshot();
        var specification = payload.projectSpecification();
        validateProjectInitialization(specification, payload.projectInitialization());
        validateProjectDocuments(plan.documents(), payload.projectInitialization());
        var ownerId = operatorContext.currentOwnerId().orElseThrow();
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId = plan.workspaceId();
        var profiles = brandApi.requireVersions(plan.brandProfileVersionIds(), workspaceId);

        var project = new AigcProject();
        project.setOrgId(orgId);
        project.setWorkspaceId(workspaceId);
        project.setOwnerId(ownerId);
        project.setUserId(ownerId);
        project.setName(projectName);
        project.setDescription(specification.description());
        project.setBackground(specification.background());
        project.setTargetAudience(specification.targetAudience());
        project.setRequirements(specification.requirements());
        project.setConstraints(specification.constraints());
        project.setKeyFacts(specification.keyFacts());
        project.setKeyMessages(specification.keyMessages());
        project.setStyleGuidance(specification.styleGuidance());
        project.setSuccessCriteria(specification.successCriteria());
        project.setDeliveryRequirements(specification.deliveryRequirements());
        project.setLocale(specification.locale());
        project.setAdditionalContext(specification.additionalContext());
        project.setProjectTypeCode(specification.projectTypeCode());
        project.setBlueprintCode(plan.blueprintCode());
        project.setBlueprintVersion(plan.blueprintVersion());
        project.setProductionMode(specification.productionMode());
        project.setGenerationMode("manual");
        project.setStatus(AigcProjectLifecycle.CREATING);
        project.setGraphRevision(1);
        project.setPrimaryBrandProfileId(
                profiles.isEmpty() ? null : profiles.getFirst().profileId());
        project.setCostUsed(BigDecimal.ZERO);
        project.setLastActiveTime(LocalDateTime.now());
        projectRepository.save(project);
        if (plan.coverMode() == AigcProjectCoverMode.UPLOAD) {
            var cover =
                    mediaApi.createFromUploadedFile(
                            new AigcUploadedMediaCommand(
                                    ownerId,
                                    projectName + "封面",
                                    AigcMediaType.IMAGE,
                                    plan.coverFileId(),
                                    project.getId()));
            var mediaVersionId = cover.currentVersion().id();
            var reference = new AigcProjectMediaRef();
            copyScope(project, reference);
            reference.setProjectId(project.getId());
            reference.setMediaVersionId(mediaVersionId);
            reference.setRole("cover");
            reference.setAdoptionStatus("adopted");
            mediaRefRepository.save(reference);
            project.setCoverMediaVersionId(mediaVersionId);
            projectRepository.save(project);
        }

        var snapshot =
                snapshotService.createInitialSnapshot(
                        project,
                        payload,
                        payload.sourceRef(),
                        plan.blueprintVersion(),
                        plan.compatibilityResult());
        project.setConfigSnapshotId(snapshot.getId());
        projectRepository.save(project);

        for (var index = 0; index < profiles.size(); index++) {
            var profile = profiles.get(index);
            var reference = new AigcProjectProfileRef();
            copyScope(project, reference);
            reference.setProjectId(project.getId());
            reference.setBrandProfileVersionId(profile.versionId());
            reference.setRefScope(index == 0 ? "primary" : "auxiliary");
            profileRefRepository.save(reference);
        }
        for (var index = 0; index < specification.channelSpecVersionIds().size(); index++) {
            var reference = new AigcProjectChannelRef();
            copyScope(project, reference);
            reference.setProjectId(project.getId());
            reference.setChannelSpecId(specification.channelSpecVersionIds().get(index));
            reference.setPrimaryChannel(index == 0);
            channelRefRepository.save(reference);
        }
        materializeDocuments(project, plan.documents(), payload.projectInitialization());

        var objects = new LinkedHashMap<String, AigcProjectObject>();
        for (var spec : payload.objectSpecs()) {
            var object = new AigcProjectObject();
            copyScope(project, object);
            object.setProjectId(project.getId());
            object.setStableKey(spec.stableKey());
            object.setBlueprintTemplateKey(spec.blueprintTemplateKey());
            object.setInstanceNo(spec.instanceNo());
            object.setContractRole(spec.contractRole());
            object.setSemanticRole(spec.semanticRole());
            object.setOutputContract(spec.outputContract());
            object.setDefaultActionKey(spec.defaultActionKey());
            object.setObjectType(spec.objectType());
            object.setSortOrder(spec.orderNo() == null ? 0 : spec.orderNo());
            object.setTitle(spec.displayName());
            object.setStatus("EMPTY");
            object.setSource("blueprint");
            object.setSchemaVersion("1.0.0");
            object.setPayload(Map.copyOf(parseMap(spec.initialPayloadJson())));
            objectRepository.save(object);
            objects.put(spec.stableKey(), object);
        }
        for (var spec : payload.objectSpecs()) {
            if (spec.parentKey() == null) {
                continue;
            }
            var object = objects.get(spec.stableKey());
            var parent = objects.get(spec.parentKey());
            if (object == null || parent == null) {
                throw new IllegalArgumentException("蓝图对象父级不存在: " + spec.parentKey());
            }
            object.setParentId(parent.getId());
            objectRepository.save(object);
        }

        var relationIds = new ArrayList<Long>();
        for (var spec : payload.relationSpecs()) {
            var source = objects.get(spec.sourceKey());
            var target = objects.get(spec.targetKey());
            if (source == null || target == null) {
                throw new IllegalArgumentException("蓝图关系引用了不存在的对象");
            }
            var relation = new AigcProjectRelation();
            copyScope(project, relation);
            relation.setProjectId(project.getId());
            relation.setRelationType(spec.relationType());
            relation.setLayer(spec.canonicalLayer());
            relation.setSourceObjectId(source.getId());
            relation.setTargetObjectId(target.getId());
            relation.setRelationMeta(parseMap(spec.metadataJson()));
            relationRepository.save(relation);
            relationIds.add(relation.getId());
        }

        var revision = new AigcProjectRevision();
        revision.setProjectId(project.getId());
        revision.setConfigSnapshotId(project.getConfigSnapshotId());
        revision.setRevisionNo(1);
        revision.setProjectVersion(project.getVersion());
        revision.setGraphRevision(project.getGraphRevision());
        revision.setChangedObjectIds(
                objects.values().stream().map(AigcProjectObject::getId).toList());
        revision.setChangedRelationIds(List.copyOf(relationIds));
        revision.setActorType("HUMAN");
        revision.setActorId(ownerId);
        revision.setSummary("项目蓝图物化");
        revisionRepository.save(revision);
        linkSourceProposal(plan, project.getId());
        return project;
    }

    private MaterializationPlan materializationPlan(AigcProjectMaterializeCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        return switch (command) {
            case AigcProjectMaterializeCommand.Catalog catalog -> catalogPlan(catalog);
            case AigcProjectMaterializeCommand.AssistantProposal reference ->
                    assistantPlan(reference);
        };
    }

    private MaterializationPlan catalogPlan(AigcProjectMaterializeCommand.Catalog command) {
        var resolved =
                configurationApi.resolve(
                        new AigcConfigurationResolveCommand(
                                command.projectTypeCode(),
                                command.blueprintVersionId(),
                                command.channelSpecVersionIds(),
                                command.productionMode(),
                                command.slotOverrides()));
        var sourceRef =
                "blueprint:%d@%s"
                        .formatted(resolved.blueprintVersionId(), resolved.blueprintVersion());
        var payload = blueprintSnapshot(command, resolved, sourceRef);
        return new MaterializationPlan(
                command.workspaceId() == null
                        ? OrgContext.getCurrentWorkspaceId()
                        : command.workspaceId(),
                command.name(),
                payload,
                resolved.blueprintCode(),
                resolved.blueprintVersion(),
                command.blueprintVersionId(),
                command.brandProfileVersionIds(),
                command.documents(),
                command.coverMode(),
                command.coverFileId(),
                command.coverPrompt(),
                command.coverIdempotencyKey(),
                command.approvedCreationProposal(),
                null,
                null,
                Map.of(
                        "compatible", true,
                        "blueprintId", resolved.blueprintVersionId(),
                        "blueprintVersion", resolved.blueprintVersion()));
    }

    private MaterializationPlan assistantPlan(
            AigcProjectMaterializeCommand.AssistantProposal reference) {
        var resolved =
                assistantBlueprintProposals.resolveForMaterialization(
                        new AssistantBlueprintProposalCommands.Reference(
                                reference.proposalId(),
                                reference.proposalVersion(),
                                reference.proposalHash()));
        var proposal = resolved.proposal();
        var creationContext = resolved.creationContext();
        return new MaterializationPlan(
                proposal.getWorkspaceId(),
                creationContext.name(),
                resolved.snapshot(),
                null,
                null,
                null,
                creationContext.brandProfileVersionIds(),
                creationContext.documentInputs(),
                creationContext.coverMode(),
                creationContext.coverFileId(),
                creationContext.coverPrompt(),
                creationContext.coverIdempotencyKey(),
                null,
                proposal,
                resolved.linkedProject(),
                Map.of(
                        "compatible", true,
                        "proposalId", proposal.getProposalId(),
                        "proposalVersion", proposal.getProposalVersion(),
                        "semanticCatalogVersion", proposal.getSemanticCatalogVersion()));
    }

    /** 在物化核心事务内回填且只回填当前来源 Proposal。 */
    private void linkSourceProposal(MaterializationPlan plan, Long projectId) {
        if (plan.assistantProposal() != null) {
            assistantBlueprintProposals.linkMaterialized(plan.assistantProposal(), projectId);
            return;
        }
        var reference = plan.catalogCreationProposal();
        if (reference == null) {
            return;
        }
        var providedFieldKeys =
                AigcProjectInitializationSpec.supportedFieldKeys().stream()
                        .filter(
                                key ->
                                        hasInitializationValue(
                                                initializationValue(
                                                        plan.snapshot().projectSpecification(),
                                                        key)))
                        .collect(java.util.stream.Collectors.toSet());
        generationProposals.markMaterialized(
                reference.draftId(),
                reference.proposalId(),
                reference.proposalVersion(),
                reference.proposalHash(),
                plan.catalogBlueprintVersionId(),
                projectId,
                providedFieldKeys);
    }

    private record MaterializationPlan(
            Long workspaceId,
            String projectName,
            AigcProjectBlueprintSnapshotPayload snapshot,
            String blueprintCode,
            String blueprintVersion,
            Long catalogBlueprintVersionId,
            List<Long> brandProfileVersionIds,
            List<AigcProjectDocumentInput> documents,
            AigcProjectCoverMode coverMode,
            Long coverFileId,
            String coverPrompt,
            String coverIdempotencyKey,
            AigcProjectMaterializeCommand.CreationProposalRef catalogCreationProposal,
            com.xuejiai.aaf.module.ai.aigc.project.domain.AssistantBlueprintProposal
                    assistantProposal,
            AigcProject linkedProject,
            Map<String, Object> compatibilityResult) {

        private MaterializationPlan {
            brandProfileVersionIds = List.copyOf(brandProfileVersionIds);
            documents = List.copyOf(documents);
            compatibilityResult = Map.copyOf(compatibilityResult);
        }
    }

    /**
     * 收尾封面状态并（如需）发起 AI 封面生成事件；独立事务保证 {@code AFTER_COMMIT} 监听器语义正确。
     *
     * <p>调用方 {@code AigcProjectService.materialize} 以 {@code Propagation.NOT_SUPPORTED} 运行，
     * 若在其中直接发布事件将导致事件在无事务上下文下由 {@code fallbackExecution} 立即同步触发， 破坏"ExecutionRun
     * 落库后才派发"的不变量，因此收尾逻辑必须放在跨 Bean 调用的独立事务内。
     */
    @Transactional
    public AigcProject finalizeCoverStatus(Long projectId, AigcProjectMaterializeCommand command) {
        var project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "项目不存在"));
        var coverStatus =
                project.getCoverMediaVersionId() == null
                        ? AigcProjectCoverStatus.NONE
                        : AigcProjectCoverStatus.READY;
        project.setCoverStatus(coverStatus);
        projectRepository.save(project);
        var cover = coverPlan(command);
        if (cover.mode() == AigcProjectCoverMode.AI_GENERATE) {
            project.setCoverStatus(AigcProjectCoverStatus.PENDING);
            projectRepository.save(project);
            eventPublisher.publishEvent(
                    new AigcProjectCoverGenerationRequestedEvent(
                            project.getId(),
                            coverPrompt(project, cover.prompt()),
                            project.getGraphRevision().longValue(),
                            cover.idempotencyKey()));
        }
        return project;
    }

    private CoverPlan coverPlan(AigcProjectMaterializeCommand command) {
        return switch (command) {
            case AigcProjectMaterializeCommand.Catalog catalog ->
                    new CoverPlan(
                            catalog.coverMode(),
                            catalog.coverPrompt(),
                            catalog.coverIdempotencyKey());
            case AigcProjectMaterializeCommand.AssistantProposal reference -> {
                var resolved =
                        assistantBlueprintProposals.resolveForMaterialization(
                                new AssistantBlueprintProposalCommands.Reference(
                                        reference.proposalId(),
                                        reference.proposalVersion(),
                                        reference.proposalHash()));
                var creationContext = resolved.creationContext();
                yield new CoverPlan(
                        creationContext.coverMode(),
                        creationContext.coverPrompt(),
                        creationContext.coverIdempotencyKey());
            }
        };
    }

    private record CoverPlan(AigcProjectCoverMode mode, String prompt, String idempotencyKey) {}

    private String coverPrompt(AigcProject project, String requestedPrompt) {
        if (requestedPrompt != null && !requestedPrompt.isBlank()) {
            return requestedPrompt.trim();
        }
        return "%s\n%s\n%s"
                .formatted(
                        project.getName(),
                        project.getDescription() == null ? "" : project.getDescription(),
                        project.getProjectTypeCode());
    }

    private String requireProjectName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目名称不能为空");
        }
        if (name.length() > 200) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目名称长度不能超过 200");
        }
        return name.trim();
    }

    private void validateProjectInitialization(
            AigcProjectSpecification specification, AigcProjectInitializationSpec initialization) {
        var configured =
                initialization.fields().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectInitializationSpec.FieldSpec::key,
                                        java.util.function.Function.identity()));
        for (var field : initialization.fields()) {
            if (field.required()
                    && !hasInitializationValue(initializationValue(specification, field.key()))) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "项目初始化字段不能为空: " + field.label());
            }
        }
        for (var key : AigcProjectInitializationSpec.supportedFieldKeys()) {
            if (!configured.containsKey(key)
                    && hasInitializationValue(initializationValue(specification, key))) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "Blueprint 未启用项目初始化字段: " + key);
            }
        }
    }

    private void validateProjectDocuments(
            List<AigcProjectDocumentInput> documents,
            AigcProjectInitializationSpec initialization) {
        var specifications =
                initialization.documents().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectInitializationSpec.DocumentSpec::key,
                                        java.util.function.Function.identity()));
        var inputsByKey =
                documents.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        AigcProjectDocumentInput::fieldKey));
        for (var input : documents) {
            if (input == null
                    || input.fieldKey() == null
                    || input.fieldKey().isBlank()
                    || !specifications.containsKey(input.fieldKey())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "项目文档 fieldKey 未被 Blueprint 声明");
            }
        }
        for (var specification : initialization.documents()) {
            var inputs = inputsByKey.getOrDefault(specification.key(), List.of());
            if (inputs.size() < specification.minCount()
                    || inputs.size() > specification.maxCount()) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST,
                        "项目文档数量不符合 Blueprint 约束: " + specification.label());
            }
            for (var input : inputs) {
                var projectDocument = "PROJECT_DOCUMENT".equals(specification.referenceType());
                if ((projectDocument && input.documentId() == null)
                        || (!projectDocument
                                && (input.documentVersionId() == null
                                        || input.documentId() != null))
                        || (projectDocument && input.documentVersionId() != null)) {
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "项目文档引用类型与输入不匹配: " + specification.key());
                }
            }
        }
    }

    private void materializeDocuments(
            AigcProject project,
            List<AigcProjectDocumentInput> documents,
            AigcProjectInitializationSpec initialization) {
        var specifications =
                initialization.documents().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectInitializationSpec.DocumentSpec::key,
                                        java.util.function.Function.identity()));
        var instanceByKey = new LinkedHashMap<String, Integer>();
        for (var input : documents) {
            var specification = specifications.get(input.fieldKey());
            final DocumentVersionApi.DocumentVersionView selectedVersion;
            if ("PROJECT_DOCUMENT".equals(specification.referenceType())) {
                selectedVersion =
                        documentVersionApi
                                .findCurrent(List.of(input.documentId()))
                                .get(input.documentId());
                if (selectedVersion == null) {
                    throw new BusinessException(GlobalErrorCode.NOT_FOUND, "项目文档当前版本不存在");
                }
                documentVersionApi.requireAccessible(
                        selectedVersion.id(),
                        project.getOwnerId(),
                        project.getOrgId(),
                        project.getWorkspaceId());
            } else {
                selectedVersion =
                        documentVersionApi.requireAccessible(
                                input.documentVersionId(),
                                project.getOwnerId(),
                                project.getOrgId(),
                                project.getWorkspaceId());
            }
            var reference = new AigcProjectDocumentRef();
            copyScope(project, reference);
            reference.setProjectId(project.getId());
            reference.setReferenceType(
                    AigcProjectDocumentReferenceType.valueOf(specification.referenceType()));
            reference.setDocumentId(selectedVersion.documentId());
            reference.setSelectedDocumentVersionId(selectedVersion.id());
            reference.setFieldKey(specification.key());
            reference.setInstanceNo(instanceByKey.merge(specification.key(), 1, Integer::sum) - 1);
            reference.setSource("blueprint");
            reference.setContextNote(input.contextNote());
            reference.setContextMode(
                    AigcProjectDocumentContextMode.valueOf(specification.contextPolicy().mode()));
            reference.setRole(specification.role());
            reference.setSortOrder(specification.orderNo() + reference.getInstanceNo());
            documentRefRepository.save(reference);
        }
    }

    private Object initializationValue(AigcProjectSpecification specification, String key) {
        return switch (key) {
            case "description" -> specification.description();
            case "background" -> specification.background();
            case "targetAudience" -> specification.targetAudience();
            case "requirements" -> specification.requirements();
            case "constraints" -> specification.constraints();
            case "keyFacts" -> specification.keyFacts();
            case "keyMessages" -> specification.keyMessages();
            case "styleGuidance" -> specification.styleGuidance();
            case "successCriteria" -> specification.successCriteria();
            case "deliveryRequirements" -> specification.deliveryRequirements();
            case "locale" -> specification.locale();
            case "additionalContext" -> specification.additionalContext();
            default -> null;
        };
    }

    private boolean hasInitializationValue(Object value) {
        return switch (value) {
            case null -> false;
            case String text -> !text.isBlank();
            case java.util.Collection<?> collection -> !collection.isEmpty();
            case Map<?, ?> map -> !map.isEmpty();
            default -> true;
        };
    }

    private void validateCover(MaterializationPlan command) {
        if (command.coverMode() == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "封面模式不能为空");
        }
        var hasPrompt = command.coverPrompt() != null && !command.coverPrompt().isBlank();
        var hasKey =
                command.coverIdempotencyKey() != null && !command.coverIdempotencyKey().isBlank();
        switch (command.coverMode()) {
            case NONE -> {
                if (command.coverFileId() != null || hasPrompt || hasKey) {
                    throw invalidCover();
                }
            }
            case UPLOAD -> {
                if (command.coverFileId() == null || hasPrompt || hasKey) {
                    throw invalidCover();
                }
            }
            case AI_GENERATE -> {
                if (command.coverFileId() != null || !hasKey) {
                    throw invalidCover();
                }
                if (command.coverIdempotencyKey().length() > 100) {
                    throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "封面幂等键长度不能超过 100");
                }
            }
        }
    }

    private BusinessException invalidCover() {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, "封面模式与参数不匹配");
    }

    private AigcProjectBlueprintSnapshotPayload blueprintSnapshot(
            AigcProjectMaterializeCommand.Catalog command,
            AigcResolvedConfiguration resolved,
            String sourceRef) {
        var specification =
                new AigcProjectSpecification(
                        command.projectTypeCode(),
                        resolved.projectTypeVersion(),
                        resolved.productionMode(),
                        resolved.channelSpecVersionIds(),
                        resolved.executionBindings(),
                        command.description(),
                        command.background(),
                        command.targetAudience(),
                        command.requirements(),
                        command.constraints(),
                        command.keyFacts(),
                        command.keyMessages(),
                        command.styleGuidance(),
                        command.successCriteria(),
                        command.deliveryRequirements(),
                        command.locale(),
                        command.additionalContext(),
                        command.slotOverrides());
        return new AigcProjectBlueprintSnapshotPayload(
                AigcBlueprintSourceType.CATALOG,
                sourceRef,
                specification,
                resolved.projectInitialization(),
                resolved.slotTemplates(),
                resolved.resolvedObjects(),
                resolved.relations(),
                resolved.resolvedObjects().stream()
                        .map(
                                object ->
                                        new AigcProjectBlueprintSnapshotPayload.OutputContractSpec(
                                                object.stableKey(), object.outputContract()))
                        .toList(),
                resolved.actions(),
                resolved.deliverableSets(),
                resolved.processPolicy(),
                2);
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
    }

    private void copyScope(AigcProject project, BaseEntity target) {
        target.setOrgId(project.getOrgId());
        target.setWorkspaceId(project.getWorkspaceId());
        target.setOwnerId(project.getOwnerId());
    }
}
