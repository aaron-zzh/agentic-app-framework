package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcUploadedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverMode;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverStatus;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMaterializeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectChannelRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectConfigSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDocumentRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectMediaRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectObject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectProfileRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRelation;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRevision;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCoverGenerationRequestedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectChannelRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectDocumentRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectMediaRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectObjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectProfileRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRelationRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRevisionRepository;
import com.xuejiai.aaf.module.document.api.DocumentReferenceApi;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** 将已发布配置解析结果物化为唯一项目聚合及 revision 1。 */
@Service
@RequiredArgsConstructor
public class AigcProjectMaterializer {

    private final AigcConfigurationApi configurationApi;
    private final AigcBrandApi brandApi;
    private final AigcProjectRepository projectRepository;
    private final AigcProjectConfigSnapshotRepository snapshotRepository;
    private final AigcProjectProfileRefRepository profileRefRepository;
    private final AigcProjectChannelRefRepository channelRefRepository;
    private final AigcProjectDocumentRefRepository documentRefRepository;
    private final AigcProjectObjectRepository objectRepository;
    private final AigcProjectRelationRepository relationRepository;
    private final AigcProjectRevisionRepository revisionRepository;
    private final DocumentReferenceApi documentReferenceApi;
    private final AigcMediaApi mediaApi;
    private final AigcProjectMediaRefRepository mediaRefRepository;
    private final OperatorContext operatorContext;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional
    public AigcProject materialize(AigcProjectMaterializeCommand command) {
        var projectName = requireProjectName(command.name());
        validateCover(command);
        var resolved =
                configurationApi.resolve(
                        new AigcConfigurationResolveCommand(
                                command.projectTypeCode(),
                                command.blueprintVersionId(),
                                command.domainExtensionVersionId(),
                                command.channelSpecVersionIds(),
                                command.productionMode(),
                                command.budgetTier(),
                                command.qualityTier(),
                                command.slotOverrides()));
        var ownerId = operatorContext.currentOwnerId().orElseThrow();
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId =
                command.workspaceId() == null
                        ? OrgContext.getCurrentWorkspaceId()
                        : command.workspaceId();
        var documents =
                documentReferenceApi.requireAccessible(
                        command.documentVersionIds(), ownerId, orgId, workspaceId);
        var profiles = brandApi.requireVersions(command.brandProfileVersionIds(), workspaceId);

        var project = new AigcProject();
        project.setOrgId(orgId);
        project.setWorkspaceId(workspaceId);
        project.setOwnerId(ownerId);
        project.setUserId(ownerId);
        project.setName(projectName);
        project.setDescription(command.description());
        project.setProjectTypeCode(command.projectTypeCode());
        project.setBlueprintCode(resolved.blueprintCode());
        project.setBlueprintVersion(resolved.blueprintVersion());
        project.setDomainExtensionCode(resolved.domainExtensionCode());
        project.setDomainExtensionVersion(resolved.domainExtensionVersion());
        project.setProductionMode(resolved.productionMode());
        project.setGenerationMode("manual");
        project.setStatus(AigcProjectLifecycle.CREATING);
        project.setBrief(command.briefJson());
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

        var snapshot = new AigcProjectConfigSnapshot();
        copyScope(project, snapshot);
        snapshot.setProjectId(project.getId());
        snapshot.setRevisionNo(1);
        snapshot.setProjectTypeVersion(resolved.projectTypeVersion());
        snapshot.setBlueprintVersion(resolved.blueprintVersion());
        snapshot.setDomainExtensionVersion(resolved.domainExtensionVersion());
        snapshot.setChannelVersions(resolved.channelSpecVersionIds());
        snapshot.setExecutionBindingVersions(resolved.executionBindings());
        snapshot.setCompatibilityResult(
                Map.of(
                        "compatible", true,
                        "packageVersionId", resolved.packageVersionId(),
                        "packageVersion", resolved.packageVersion()));
        snapshot.setSnapshot(parseMap(resolved.snapshotJson()));
        snapshotRepository.save(snapshot);
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
        for (var index = 0; index < documents.size(); index++) {
            var reference = new AigcProjectDocumentRef();
            copyScope(project, reference);
            reference.setProjectId(project.getId());
            reference.setDocumentVersionId(documents.get(index).id());
            reference.setRole("project");
            reference.setSortOrder(index);
            documentRefRepository.save(reference);
        }

        var objects = new LinkedHashMap<String, AigcProjectObject>();
        for (var spec : resolved.resolvedObjects()) {
            var object = new AigcProjectObject();
            copyScope(project, object);
            object.setProjectId(project.getId());
            object.setStableKey(spec.stableKey());
            object.setBlueprintTemplateKey(spec.blueprintTemplateKey());
            object.setInstanceNo(spec.instanceNo());
            object.setContractRole(spec.contractRole());
            object.setObjectType(spec.objectType());
            object.setSortOrder(spec.orderNo() == null ? 0 : spec.orderNo());
            object.setTitle(spec.displayName());
            object.setStatus("empty");
            object.setSource("blueprint");
            object.setSchemaVersion("1.0.0");
            var payload = new LinkedHashMap<>(parseMap(spec.schemaJson()));
            resolved.actions().stream()
                    .filter(
                            action ->
                                    spec.blueprintTemplateKey().equals(action.targetTemplateKey()))
                    .findFirst()
                    .ifPresent(action -> payload.put("defaultActionKey", action.actionKey()));
            object.setPayload(Map.copyOf(payload));
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
            relation.setLayer("domain");
            relation.setSourceObjectId(source.getId());
            relation.setTargetObjectId(target.getId());
            relation.setRelationMeta(parseMap(spec.metadataJson()));
            relationRepository.save(relation);
            relationIds.add(relation.getId());
        }

        var revision = new AigcProjectRevision();
        revision.setProjectId(project.getId());
        revision.setRevisionNo(1);
        revision.setChangedObjectIds(
                objects.values().stream().map(AigcProjectObject::getId).toList());
        revision.setChangedRelationIds(List.copyOf(relationIds));
        revision.setActorType("HUMAN");
        revision.setActorId(ownerId);
        revision.setSummary("项目蓝图物化");
        revisionRepository.save(revision);
        return project;
    }

    /**
     * 收尾封面状态并（如需）发起 AI 封面生成事件；独立事务保证 {@code AFTER_COMMIT} 监听器语义正确。
     *
     * <p>调用方 {@code AigcProjectService.materialize} 以 {@code Propagation.NOT_SUPPORTED} 运行，
     * 若在其中直接发布事件将导致事件在无事务上下文下由 {@code fallbackExecution} 立即同步触发，
     * 破坏"ExecutionRun 落库后才派发"的不变量，因此收尾逻辑必须放在跨 Bean 调用的独立事务内。
     */
    @Transactional
    public AigcProject finalizeCoverStatus(
            Long projectId, AigcProjectMaterializeCommand command) {
        var project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "项目不存在"));
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
                        project.getBrief() == null ? "" : project.getBrief(),
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
