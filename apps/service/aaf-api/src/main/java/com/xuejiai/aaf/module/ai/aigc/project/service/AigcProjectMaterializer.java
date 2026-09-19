package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
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

/** 将已发布配置解析结果物化为唯一项目聚合及 revision 1。 */
@Service
@RequiredArgsConstructor
public class AigcProjectMaterializer {

    // plannedPhase 已确认为无实际业务消费的纯装饰性字段（详见 design.md），业务语义上已移除；
    // 数据库列 aigc_project_object.planned_phase 仍为 NOT NULL（v7__aigc_schema.sql 已发布迁移，
    // 本轮授权不允许新增迁移删除该列），写入固定占位值以满足既有约束，不再对外暴露或校验。
    private static final String LEGACY_PLANNED_PHASE_PLACEHOLDER = "BUILD";

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
    private final OperatorContext operatorContext;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AigcProject materialize(AigcProjectMaterializeCommand command) {
        var projectName = requireProjectName(command.name());
        if (command.sourceType() != AigcBlueprintSourceType.CATALOG) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "阶段一只支持 CATALOG BlueprintSnapshot");
        }
        if (command.sourceRef() == null || command.sourceRef().isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "CATALOG sourceRef 不能为空");
        }
        validateCover(command);
        var resolved =
                configurationApi.resolve(
                        new AigcConfigurationResolveCommand(
                                command.projectTypeCode(),
                                command.blueprintVersionId(),
                                command.channelSpecVersionIds(),
                                command.productionMode(),
                                command.slotOverrides()));
        validateProjectInitialization(command, resolved.projectInitialization());
        validateProjectDocuments(command, resolved.projectInitialization());
        var sourceRef =
                "blueprint:%d@%s"
                        .formatted(resolved.blueprintVersionId(), resolved.blueprintVersion());
        if (!sourceRef.equals(command.sourceRef().trim())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "CATALOG sourceRef 与已发布 Blueprint 不一致");
        }
        var ownerId = operatorContext.currentOwnerId().orElseThrow();
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId =
                command.workspaceId() == null
                        ? OrgContext.getCurrentWorkspaceId()
                        : command.workspaceId();
        var profiles = brandApi.requireVersions(command.brandProfileVersionIds(), workspaceId);

        var project = new AigcProject();
        project.setOrgId(orgId);
        project.setWorkspaceId(workspaceId);
        project.setOwnerId(ownerId);
        project.setUserId(ownerId);
        project.setName(projectName);
        project.setDescription(command.description());
        project.setBackground(command.background());
        project.setTargetAudience(command.targetAudience());
        project.setRequirements(command.requirements());
        project.setConstraints(command.constraints());
        project.setKeyFacts(command.keyFacts());
        project.setKeyMessages(command.keyMessages());
        project.setStyleGuidance(command.styleGuidance());
        project.setSuccessCriteria(command.successCriteria());
        project.setDeliveryRequirements(command.deliveryRequirements());
        project.setLocale(command.locale());
        project.setAdditionalContext(command.additionalContext());
        project.setProjectTypeCode(command.projectTypeCode());
        project.setBlueprintCode(resolved.blueprintCode());
        project.setBlueprintVersion(resolved.blueprintVersion());
        project.setProductionMode(resolved.productionMode());
        project.setGenerationMode("manual");
        project.setStatus(AigcProjectLifecycle.CREATING);
        project.setGraphRevision(1);
        project.setPrimaryBrandProfileId(
                profiles.isEmpty() ? null : profiles.getFirst().profileId());
        project.setCostUsed(BigDecimal.ZERO);
        project.setLastActiveTime(LocalDateTime.now());
        projectRepository.save(project);
        if (command.coverMode() == AigcProjectCoverMode.UPLOAD) {
            var cover =
                    mediaApi.createFromUploadedFile(
                            new AigcUploadedMediaCommand(
                                    ownerId,
                                    projectName + "封面",
                                    AigcMediaType.IMAGE,
                                    command.coverFileId(),
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

        var snapshotPayload = blueprintSnapshot(command, resolved, sourceRef);
        var snapshot =
                snapshotService.createInitialSnapshot(
                        project, snapshotPayload, sourceRef, resolved);
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
        for (var index = 0; index < resolved.channelSpecVersionIds().size(); index++) {
            var reference = new AigcProjectChannelRef();
            copyScope(project, reference);
            reference.setProjectId(project.getId());
            reference.setChannelSpecId(resolved.channelSpecVersionIds().get(index));
            reference.setPrimaryChannel(index == 0);
            channelRefRepository.save(reference);
        }
        materializeDocuments(project, command, resolved.projectInitialization());

        var objects = new LinkedHashMap<String, AigcProjectObject>();
        for (var spec : resolved.resolvedObjects()) {
            var object = new AigcProjectObject();
            copyScope(project, object);
            object.setProjectId(project.getId());
            object.setStableKey(spec.stableKey());
            object.setBlueprintTemplateKey(spec.blueprintTemplateKey());
            object.setInstanceNo(spec.instanceNo());
            object.setContractRole(spec.contractRole());
            object.setSemanticRole(spec.semanticRole());
            object.setPlannedPhase(LEGACY_PLANNED_PHASE_PLACEHOLDER);
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
        for (var spec : resolved.resolvedObjects()) {
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
        for (var spec : resolved.relations()) {
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
        linkCreationProposal(command, project.getId());
        return project;
    }

    /**
     * 物化前草稿 Proposal 的确认凭据校验与回填。
     *
     * <p>在同一物化事务内执行：Proposal 必须 APPROVED、作用域为 CREATION_DRAFT、Blueprint 版本与本次一致、归属当前用户，
     * 且已确认的初始化字段在本次提交中带值；随后回填 linkedProjectId 供追溯。未携带凭据表示本次创建没有使用 AI 辅助初始化。
     */
    private void linkCreationProposal(AigcProjectMaterializeCommand command, Long projectId) {
        var reference = command.approvedCreationProposal();
        if (reference == null) {
            return;
        }
        var providedFieldKeys =
                AigcProjectInitializationSpec.supportedFieldKeys().stream()
                        .filter(key -> hasInitializationValue(initializationValue(command, key)))
                        .collect(java.util.stream.Collectors.toSet());
        generationProposals.markMaterialized(
                reference.draftId(),
                reference.proposalId(),
                reference.proposalVersion(),
                reference.proposalHash(),
                command.blueprintVersionId(),
                projectId,
                providedFieldKeys);
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
        var generateCover = command.coverMode() == AigcProjectCoverMode.AI_GENERATE;
        if (generateCover) {
            project.setCoverStatus(AigcProjectCoverStatus.PENDING);
            projectRepository.save(project);
            eventPublisher.publishEvent(
                    new AigcProjectCoverGenerationRequestedEvent(
                            project.getId(),
                            coverPrompt(project, command.coverPrompt()),
                            project.getGraphRevision().longValue(),
                            command.coverIdempotencyKey()));
        }
        return project;
    }

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
            AigcProjectMaterializeCommand command, AigcProjectInitializationSpec initialization) {
        var configured =
                initialization.fields().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectInitializationSpec.FieldSpec::key,
                                        java.util.function.Function.identity()));
        for (var field : initialization.fields()) {
            if (field.required()
                    && !hasInitializationValue(initializationValue(command, field.key()))) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "项目初始化字段不能为空: " + field.label());
            }
        }
        for (var key : AigcProjectInitializationSpec.supportedFieldKeys()) {
            if (!configured.containsKey(key)
                    && hasInitializationValue(initializationValue(command, key))) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "Blueprint 未启用项目初始化字段: " + key);
            }
        }
    }

    private void validateProjectDocuments(
            AigcProjectMaterializeCommand command, AigcProjectInitializationSpec initialization) {
        var specifications =
                initialization.documents().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectInitializationSpec.DocumentSpec::key,
                                        java.util.function.Function.identity()));
        var inputsByKey =
                command.documents().stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        AigcProjectDocumentInput::fieldKey));
        for (var input : command.documents()) {
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
            AigcProjectMaterializeCommand command,
            AigcProjectInitializationSpec initialization) {
        var specifications =
                initialization.documents().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectInitializationSpec.DocumentSpec::key,
                                        java.util.function.Function.identity()));
        var instanceByKey = new LinkedHashMap<String, Integer>();
        for (var input : command.documents()) {
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

    private Object initializationValue(AigcProjectMaterializeCommand command, String key) {
        return switch (key) {
            case "description" -> command.description();
            case "background" -> command.background();
            case "targetAudience" -> command.targetAudience();
            case "requirements" -> command.requirements();
            case "constraints" -> command.constraints();
            case "keyFacts" -> command.keyFacts();
            case "keyMessages" -> command.keyMessages();
            case "styleGuidance" -> command.styleGuidance();
            case "successCriteria" -> command.successCriteria();
            case "deliveryRequirements" -> command.deliveryRequirements();
            case "locale" -> command.locale();
            case "additionalContext" -> command.additionalContext();
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

    private void validateCover(AigcProjectMaterializeCommand command) {
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
            AigcProjectMaterializeCommand command,
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
                command.sourceType(),
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
