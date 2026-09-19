package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.OperatorType;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantIdentityPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.AigcAuthorities;
import com.xuejiai.aaf.module.ai.aigc.AigcCanonicalRequest;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintActionSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintRelationSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintSlotTemplateSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedObjectSpec;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcUploadedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcApprovedManifestView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcCompletionEvaluationView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetCompletionView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetEvaluateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetManifestFreezeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDocumentCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcFrozenInputVersionRef;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationScope;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectReadiness;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectReadinessState;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionAdoptCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionComparisonView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionRejectCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectConversationBindCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectConversationContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverStatus;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentContextMode;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentReferenceType;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationBindCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationReleaseCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGraphView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycleCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMaterializeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMaterializeView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMediaRefCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMediaRefView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectContractCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectMoveCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectRemoveCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectProgressEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectProgressSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectRelationSemantics;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectRelationView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewDecisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewSubmitCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcTextCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.CompletionEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.project.api.ExecutionEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcObjectVersion;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectConfigSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDocumentDecision;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDocumentRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectExecutionReservation;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectExecutionReservationTarget;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectMediaRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectObject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectResourceRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRevision;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectArchivedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCoverGenerationRequestedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCoverSupersededEvent;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcObjectVersionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectChannelRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectDocumentDecisionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectDocumentRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectExecutionReservationRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectExecutionReservationTargetRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectMediaRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectObjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectProfileRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRelationRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectResourceRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRevisionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcResolvedDomainContextRepository;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcObjectVersionVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectChannelRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectConfigSnapshotVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectCoverPatchDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentCandidateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentDecisionDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentDecisionVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectGraphVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectMediaRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectObjectVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectProfileRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectRelationVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectResourceRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectRevisionVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectSummaryVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectVO;
import com.xuejiai.aaf.module.chat.conversation.api.ProjectConversationPort;
import com.xuejiai.aaf.module.document.api.DocumentReferenceApi;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi;
import com.xuejiai.aaf.module.document.api.DocumentVersionSemanticMetadataApi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** 唯一 AIGC Project 聚合应用服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcProjectService
        extends BaseCrudService<
                AigcProject, AigcProjectVO, Void, AigcProjectUpdateDTO, AigcProjectPageDTO>
        implements AigcProjectApi {

    private static final Set<AigcProjectLifecycle> MUTABLE_STATUSES =
            Set.of(
                    AigcProjectLifecycle.CREATING,
                    AigcProjectLifecycle.EXECUTING,
                    AigcProjectLifecycle.ADOPTING);

    // plannedPhase 已确认为无实际业务消费的纯装饰性字段（详见 design.md），业务语义上已移除；
    // 数据库列 aigc_project_object.planned_phase 仍为 NOT NULL（v7__aigc_schema.sql 已发布迁移，
    // 本轮授权不允许新增迁移删除该列），写入固定占位值以满足既有约束，不再对外暴露或校验。
    private static final String LEGACY_PLANNED_PHASE_PLACEHOLDER = "BUILD";

    private final AigcProjectRepository repository;
    private final EntityManager entityManager;
    private final AigcProjectObjectRepository objectRepository;
    private final AigcProjectRelationRepository relationRepository;
    private final AigcProjectExecutionReservationRepository reservationRepository;
    private final AigcProjectExecutionReservationTargetRepository reservationTargetRepository;
    private final AigcObjectVersionRepository versionRepository;
    private final AigcProjectRevisionRepository revisionRepository;
    private final AigcProjectConfigSnapshotRepository snapshotRepository;
    private final AigcResolvedDomainContextRepository resolvedDomainContextRepository;
    private final AigcProjectProfileRefRepository profileRefRepository;
    private final AigcProjectChannelRefRepository channelRefRepository;
    private final AigcProjectDocumentRefRepository documentRefRepository;
    private final AigcProjectDocumentDecisionRepository documentDecisionRepository;
    private final AigcProjectResourceRefRepository resourceRefRepository;
    private final AigcProjectMediaRefRepository mediaRefRepository;
    private final AigcProjectMaterializer materializer;
    private final AigcProjectDeliveryService deliveryService;
    private final CompletionEvidencePort completionEvidencePort;
    private final ExecutionEvidencePort executionEvidencePort;
    private final AigcProjectProgressEvidencePort progressEvidencePort;
    private final AigcMediaApi mediaApi;
    private final DocumentReferenceApi documentReferenceApi;
    private final DocumentVersionApi documentVersionApi;
    private final DocumentVersionSemanticMetadataApi semanticMetadataApi;
    private final AssistantIdentityPort assistantIdentities;
    private final ProjectConversationPort projectConversations;
    private final OperatorContext operatorContext;
    private final PermissionEvaluator permissionEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final AigcActivityEventService activityEventService;

    @Override
    protected AigcProjectRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcProjectVO toVO(AigcProject project) {
        var coverState = coverState(project);
        return new AigcProjectVO(
                project.getId(),
                project.getVersion(),
                project.getName(),
                project.getDescription(),
                project.getBackground(),
                project.getTargetAudience(),
                project.getRequirements(),
                project.getConstraints(),
                project.getKeyFacts(),
                project.getKeyMessages(),
                project.getStyleGuidance(),
                project.getSuccessCriteria(),
                project.getDeliveryRequirements(),
                project.getLocale(),
                project.getAdditionalContext(),
                project.getProjectTypeCode(),
                project.getBlueprintCode(),
                project.getBlueprintVersion(),
                project.getProductionMode(),
                project.getGenerationMode(),
                project.getStatus(),
                project.getPrompt(),
                project.getCoverMediaVersionId(),
                coverState.status(),
                coverState.runId(),
                project.getConfigSnapshotId(),
                project.getGraphRevision(),
                project.getPrimaryBrandProfileId(),
                project.getAssistantId(),
                project.getBudgetLimit(),
                project.getCostUsed(),
                project.getLastActiveTime(),
                project.getCreateTime(),
                project.getUpdateTime());
    }

    @Override
    protected AigcProject toEntity(Void ignored) {
        throw new UnsupportedOperationException("项目只能通过 materialize 创建");
    }

    @Override
    protected void updateEntity(AigcProject project, AigcProjectUpdateDTO request) {
        requireExpectedVersion(project, request.expectedVersion());
        if (isEmptyPatch(request)) {
            return;
        }
        requireDisplayInfoWritable(project);
        if (!request.name().isAbsent()) {
            var name = request.name().valueOrNull();
            if (name == null || name.isBlank()) {
                throw badRequest("项目名称不能为空");
            }
            if (name.length() > 200) {
                throw badRequest("项目名称长度不能超过 200");
            }
            project.setName(name.trim());
        }
        if (!request.description().isAbsent()) {
            var description = request.description().valueOrNull();
            if (description != null && description.length() > 500) {
                throw badRequest("项目描述长度不能超过 500");
            }
            project.setDescription(description);
        }
        if (!request.background().isAbsent()) {
            project.setBackground(request.background().valueOrNull());
        }
        if (!request.targetAudience().isAbsent()) {
            project.setTargetAudience(request.targetAudience().valueOrNull());
        }
        if (!request.requirements().isAbsent()) {
            project.setRequirements(
                    request.requirements().valueOrNull() == null
                            ? List.of()
                            : List.copyOf(request.requirements().valueOrNull()));
        }
        if (!request.constraints().isAbsent()) {
            project.setConstraints(
                    request.constraints().valueOrNull() == null
                            ? List.of()
                            : List.copyOf(request.constraints().valueOrNull()));
        }
        if (!request.keyFacts().isAbsent()) {
            project.setKeyFacts(
                    request.keyFacts().valueOrNull() == null
                            ? List.of()
                            : List.copyOf(request.keyFacts().valueOrNull()));
        }
        if (!request.keyMessages().isAbsent()) {
            project.setKeyMessages(
                    request.keyMessages().valueOrNull() == null
                            ? List.of()
                            : List.copyOf(request.keyMessages().valueOrNull()));
        }
        if (!request.styleGuidance().isAbsent()) {
            project.setStyleGuidance(request.styleGuidance().valueOrNull());
        }
        if (!request.successCriteria().isAbsent()) {
            project.setSuccessCriteria(
                    request.successCriteria().valueOrNull() == null
                            ? List.of()
                            : List.copyOf(request.successCriteria().valueOrNull()));
        }
        if (!request.deliveryRequirements().isAbsent()) {
            project.setDeliveryRequirements(
                    request.deliveryRequirements().valueOrNull() == null
                            ? Map.of()
                            : Map.copyOf(request.deliveryRequirements().valueOrNull()));
        }
        if (!request.locale().isAbsent()) {
            var locale = request.locale().valueOrNull();
            if (locale != null && locale.length() > 32) {
                throw badRequest("语言地区长度不能超过 32");
            }
            project.setLocale(locale);
        }
        if (!request.additionalContext().isAbsent()) {
            project.setAdditionalContext(request.additionalContext().valueOrNull());
        }
        if (!request.cover().isAbsent()) {
            if (request.cover().isNullValue()) {
                throw badRequest("封面必须使用明确操作更新");
            }
            updateCover(project, request.cover().valueOrNull());
        }
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
    }

    private boolean isEmptyPatch(AigcProjectUpdateDTO request) {
        return request.name().isAbsent()
                && request.description().isAbsent()
                && request.background().isAbsent()
                && request.targetAudience().isAbsent()
                && request.requirements().isAbsent()
                && request.constraints().isAbsent()
                && request.keyFacts().isAbsent()
                && request.keyMessages().isAbsent()
                && request.styleGuidance().isAbsent()
                && request.successCriteria().isAbsent()
                && request.deliveryRequirements().isAbsent()
                && request.locale().isAbsent()
                && request.additionalContext().isAbsent()
                && request.cover().isAbsent();
    }

    private void updateCover(AigcProject project, AigcProjectCoverPatchDTO cover) {
        if (cover == null || cover.operation() == null) {
            throw badRequest("封面更新操作不能为空");
        }
        switch (cover.operation()) {
            case UPLOAD -> replaceUploadedCover(project, cover);
            case AI_GENERATE -> requestGeneratedCover(project, cover);
            case REMOVE -> removeCover(project, cover);
        }
    }

    private void replaceUploadedCover(AigcProject project, AigcProjectCoverPatchDTO cover) {
        if (cover.fileId() == null || hasText(cover.prompt()) || hasText(cover.idempotencyKey())) {
            throw badRequest("上传封面参数不匹配");
        }
        var media =
                mediaApi.createFromUploadedFile(
                        new AigcUploadedMediaCommand(
                                project.getUserId(),
                                project.getName() + "封面",
                                AigcMediaType.IMAGE,
                                cover.fileId(),
                                project.getId()));
        var mediaVersionId = media.currentVersion().id();
        linkCoverMedia(project, mediaVersionId, "adopted");
        eventPublisher.publishEvent(
                new AigcProjectCoverSupersededEvent(project.getId(), "封面已手动替换"));
        project.setCoverMediaVersionId(mediaVersionId);
        project.setCoverExecutionRunId(null);
        project.setCoverStatus(AigcProjectCoverStatus.READY);
    }

    private void requestGeneratedCover(AigcProject project, AigcProjectCoverPatchDTO cover) {
        if (cover.fileId() != null || !hasText(cover.idempotencyKey())) {
            throw badRequest("AI 封面参数不匹配");
        }
        if (cover.idempotencyKey().length() > 100) {
            throw badRequest("封面幂等键长度不能超过 100");
        }
        project.setCoverStatus(AigcProjectCoverStatus.PENDING);
        project.setCoverExecutionRunId(null);
        eventPublisher.publishEvent(
                new AigcProjectCoverGenerationRequestedEvent(
                        project.getId(),
                        coverPrompt(project, cover.prompt()),
                        project.getGraphRevision().longValue(),
                        cover.idempotencyKey()));
    }

    private void removeCover(AigcProject project, AigcProjectCoverPatchDTO cover) {
        if (cover.fileId() != null || hasText(cover.prompt()) || hasText(cover.idempotencyKey())) {
            throw badRequest("移除封面参数不匹配");
        }
        supersedeAdoptedCoverRefs(project.getId(), null);
        eventPublisher.publishEvent(new AigcProjectCoverSupersededEvent(project.getId(), "封面已移除"));
        project.setCoverMediaVersionId(null);
        project.setCoverExecutionRunId(null);
        project.setCoverStatus(AigcProjectCoverStatus.NONE);
    }

    private AigcProjectMediaRef linkCoverMedia(
            AigcProject project, Long mediaVersionId, String adoptionStatus) {
        if ("adopted".equals(adoptionStatus)) {
            supersedeAdoptedCoverRefs(project.getId(), mediaVersionId);
        }
        var existing =
                mediaRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(project.getId()).stream()
                        .filter(reference -> mediaVersionId.equals(reference.getMediaVersionId()))
                        .filter(reference -> "cover".equals(reference.getRole()))
                        .filter(reference -> reference.getObjectId() == null)
                        .findFirst();
        if (existing.isPresent()) {
            var reference = existing.orElseThrow();
            if ("adopted".equals(adoptionStatus)) {
                reference.setAdoptionStatus("adopted");
                mediaRefRepository.save(reference);
            }
            return reference;
        }
        var reference = new AigcProjectMediaRef();
        copyScope(project, reference);
        reference.setProjectId(project.getId());
        reference.setMediaVersionId(mediaVersionId);
        reference.setRole("cover");
        reference.setAdoptionStatus(adoptionStatus);
        return mediaRefRepository.save(reference);
    }

    private void supersedeAdoptedCoverRefs(Long projectId, Long retainedMediaVersionId) {
        mediaRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                .filter(reference -> "cover".equals(reference.getRole()))
                .filter(reference -> reference.getObjectId() == null)
                .filter(reference -> "adopted".equals(reference.getAdoptionStatus()))
                .filter(
                        reference ->
                                !Objects.equals(
                                        retainedMediaVersionId, reference.getMediaVersionId()))
                .forEach(
                        reference -> {
                            reference.setAdoptionStatus("superseded");
                            mediaRefRepository.save(reference);
                        });
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String coverPrompt(AigcProject project, String requestedPrompt) {
        if (hasText(requestedPrompt)) {
            return requestedPrompt.trim();
        }
        return "%s\n%s\n%s"
                .formatted(
                        project.getName(),
                        project.getDescription() == null ? "" : project.getDescription(),
                        project.getProjectTypeCode());
    }

    @Override
    protected Specification<AigcProject> buildSpec(AigcProjectPageDTO request) {
        return SpecificationBuilder.<AigcProject>builder()
                .eqIfPresent("status", request.getStatus())
                .eqIfPresent("projectTypeCode", request.getProjectTypeCode())
                .eqIfPresent("productionMode", request.getProductionMode())
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AigcProjectMaterializeView materialize(AigcProjectMaterializeCommand command) {
        var project = materializer.materialize(command);
        project = materializer.finalizeCoverStatus(project.getId(), command);
        activityEventService.publish(
                project.getUserId(),
                "project.materialized",
                project.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(
                        "status", project.getStatus().name(),
                        "coverStatus", project.getCoverStatus().name()));
        var refreshed = repository.findById(project.getId()).orElseThrow();
        return new AigcProjectMaterializeView(
                toApiView(refreshed),
                refreshed.getCoverStatus(),
                refreshed.getCoverExecutionRunId());
    }

    @Override
    public AigcProjectView requireProject(Long projectId) {
        return toApiView(requireEntity(projectId));
    }

    @Override
    public AigcProjectConversationContext conversationContext(Long projectId) {
        var project = requireEntity(projectId);
        var userId = requireCurrentProjectOwner(project);
        return conversationContext(project, userId);
    }

    @Transactional
    public AigcProjectConversationContext bindConversation(
            AigcProjectConversationBindCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var project =
                requireLockedProject(command.projectId(), command.expectedProjectVersion(), true);
        var userId = requireCurrentProjectOwner(project);
        var tenantId = projectTenant(project);
        if (project.getConversationId() != null || project.getAssistantId() != null) {
            var current = conversationContext(project, userId);
            if (current.state() != AigcProjectConversationContext.State.READY
                    || !command.assistantId().equals(current.assistantId())) {
                throw new BusinessException(409, "项目已绑定其他 Assistant 或会话身份不一致");
            }
            return current;
        }
        var assistant =
                assistantIdentities
                        .resolveExecutable(tenantId, userId, new AssistantId(command.assistantId()))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.FORBIDDEN,
                                                "Assistant 不存在或当前用户不可执行"));
        var conversation =
                projectConversations.create(
                        new ProjectConversationPort.CreateCommand(
                                project.getId(),
                                project.getUserId(),
                                project.getOrgId(),
                                project.getWorkspaceId(),
                                assistant.assistantRecordId(),
                                projectConversationTitle(project)));
        project.setAssistantId(assistant.assistantRecordId());
        project.setConversationId(conversation.conversationRecordId());
        project.setVersion(project.getVersion() + 1);
        repository.saveAndFlush(project);
        return new AigcProjectConversationContext(
                project.getId(),
                project.getVersion(),
                AigcProjectConversationContext.State.READY,
                assistant.assistantRecordId(),
                assistant.assistantId().value(),
                conversation.conversationRecordId(),
                conversation.threadId());
    }

    private AigcProjectConversationContext conversationContext(AigcProject project, UserId userId) {
        if (project.getConversationId() == null && project.getAssistantId() == null) {
            return AigcProjectConversationContext.bindingRequired(
                    project.getId(), project.getVersion());
        }
        if (project.getConversationId() == null || project.getAssistantId() == null) {
            throw new BusinessException(409, "项目 Assistant 与会话绑定不完整");
        }
        var assistant =
                assistantIdentities
                        .resolveExecutable(projectTenant(project), userId, project.getAssistantId())
                        .orElseThrow(() -> new BusinessException(409, "项目绑定的 Assistant 已不可执行"));
        var conversation =
                projectConversations
                        .find(project.getConversationId())
                        .orElseThrow(() -> new BusinessException(409, "项目绑定会话不存在或不可管理"));
        if (!Objects.equals(conversation.ownerId(), project.getUserId())
                || !Objects.equals(conversation.orgId(), project.getOrgId())
                || !Objects.equals(conversation.workspaceId(), project.getWorkspaceId())
                || !Objects.equals(conversation.assistantRecordId(), assistant.assistantRecordId())
                || conversation.threadId() == null
                || conversation.threadId().isBlank()) {
            throw new BusinessException(409, "项目绑定会话身份或范围不一致");
        }
        return new AigcProjectConversationContext(
                project.getId(),
                project.getVersion(),
                AigcProjectConversationContext.State.READY,
                assistant.assistantRecordId(),
                assistant.assistantId().value(),
                conversation.conversationRecordId(),
                conversation.threadId());
    }

    private UserId requireCurrentProjectOwner(AigcProject project) {
        var ownerId = operatorContext.currentOwnerId().orElseThrow();
        if (!Objects.equals(project.getUserId(), ownerId)) {
            throw notFound("项目不存在");
        }
        return new UserId(String.valueOf(ownerId));
    }

    private TenantId projectTenant(AigcProject project) {
        if (project.getOrgId() == null) {
            throw new BusinessException(409, "项目缺少组织范围");
        }
        return new TenantId(String.valueOf(project.getOrgId()));
    }

    private String projectConversationTitle(AigcProject project) {
        var title = "项目协作：" + project.getName();
        return title.length() <= 200 ? title : title.substring(0, 200);
    }

    @Override
    @Transactional
    public AigcProjectView lockForCreativeMutation(Long projectId, Long userId) {
        var project = requireLockedProject(projectId, null, false);
        if (!Objects.equals(project.getUserId(), userId)) {
            throw notFound("项目不存在");
        }
        requireWritable(project);
        return toApiView(project);
    }

    @Override
    @Transactional
    public AigcProjectView lockForCreativeMutation(
            Long projectId, Long userId, Integer expectedProjectVersion) {
        var project = requireLockedProject(projectId, expectedProjectVersion, true);
        if (!Objects.equals(project.getUserId(), userId)) {
            throw notFound("项目不存在");
        }
        requireWritable(project);
        return toApiView(project);
    }

    @Override
    @Transactional
    public AigcProjectView lockForWorkMutation(
            Long projectId, Long userId, Integer expectedProjectVersion) {
        var project = requireLockedProject(projectId, expectedProjectVersion, true);
        if (!Objects.equals(project.getUserId(), userId)) {
            throw notFound("项目不存在");
        }
        if (project.getStatus() != AigcProjectLifecycle.DELIVERING) {
            throw badRequest("只有 DELIVERING 阶段允许修改 Work 与 Publication");
        }
        return toApiView(project);
    }

    @Override
    @Transactional
    public AigcProjectExecutionReservationView requireBoundExecution(
            Long reservationId, Long executionSubmissionId, Long rootExecutionRunId) {
        var snapshot =
                reservationRepository
                        .findById(reservationId)
                        .orElseThrow(() -> notFound("执行 reservation 不存在"));
        var project = requireLockedProject(snapshot.getProjectId(), null, false);
        var reservation =
                reservationRepository
                        .findLockedById(reservationId)
                        .orElseThrow(() -> notFound("执行 reservation 不存在"));
        requireProjectScope(
                project,
                operatorContext.currentOwnerId().orElse(null),
                OrgContext.getCurrentOrgId(),
                OrgContext.getCurrentWorkspaceId());
        requireSameSubmission(reservation, executionSubmissionId);
        if (!"BOUND".equals(reservation.getStatus())
                || !Objects.equals(reservation.getRootExecutionRunId(), rootExecutionRunId)) {
            throw badRequest("执行 reservation 未绑定到指定 root");
        }
        return toReservationView(reservation);
    }

    @Override
    public Set<Long> findLinkedDocumentIds(
            Long ownerId, Long orgId, Long workspaceId, Long projectId) {
        requireCurrentScope(ownerId, orgId, workspaceId);
        if (projectId != null) {
            var project = requireEntity(projectId);
            requireProjectScope(project, ownerId, orgId, workspaceId);
        }
        var documentIds =
                new LinkedHashSet<>(
                        documentRefRepository.findLinkedDocumentIds(
                                AigcProjectDocumentReferenceType.PROJECT_DOCUMENT,
                                ownerId,
                                orgId,
                                workspaceId,
                                projectId));
        if (!documentIds.isEmpty()) {
            documentReferenceApi.requireAccessible(documentIds, ownerId, orgId, workspaceId);
        }
        var externalVersionIds =
                documentRefRepository.findLinkedDocumentVersionIds(
                        AigcProjectDocumentReferenceType.EXTERNAL_REFERENCE,
                        ownerId,
                        orgId,
                        workspaceId,
                        projectId);
        if (!externalVersionIds.isEmpty()) {
            documentVersionApi
                    .requireAccessible(externalVersionIds, ownerId, orgId, workspaceId)
                    .stream()
                    .map(DocumentVersionApi.DocumentVersionView::documentId)
                    .forEach(documentIds::add);
        }
        return Set.copyOf(documentIds);
    }

    @Override
    public List<DocumentProjectReference> findDocumentProjects(
            Long ownerId, Long orgId, Long workspaceId, Collection<Long> documentIds) {
        requireCurrentScope(ownerId, orgId, workspaceId);
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        var ids = new LinkedHashSet<>(documentIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            return List.of();
        }
        var rows = documentRefRepository.findDocumentProjects(ownerId, orgId, workspaceId);
        if (rows.isEmpty()) {
            return List.of();
        }
        var projectDocumentIds =
                rows.stream()
                        .map(AigcProjectDocumentRefRepository.DocumentProjectRow::getDocumentId)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!projectDocumentIds.isEmpty()) {
            documentReferenceApi.requireAccessible(projectDocumentIds, ownerId, orgId, workspaceId);
        }
        var externalVersionIds =
                rows.stream()
                        .map(
                                AigcProjectDocumentRefRepository.DocumentProjectRow
                                        ::getDocumentVersionId)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, DocumentVersionApi.DocumentVersionView> externalVersionsById =
                externalVersionIds.isEmpty()
                        ? Map.of()
                        : documentVersionApi
                                .requireAccessible(externalVersionIds, ownerId, orgId, workspaceId)
                                .stream()
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                DocumentVersionApi.DocumentVersionView::id,
                                                version -> version));
        return rows.stream()
                .map(
                        row -> {
                            var documentId = row.getDocumentId();
                            if (documentId == null && row.getDocumentVersionId() != null) {
                                var version = externalVersionsById.get(row.getDocumentVersionId());
                                documentId = version == null ? null : version.documentId();
                            }
                            if (documentId == null) {
                                throw badRequest("项目文档引用身份无效");
                            }
                            return new DocumentProjectReference(
                                    documentId, row.getProjectId(), row.getProjectName());
                        })
                .filter(reference -> ids.contains(reference.documentId()))
                .distinct()
                .toList();
    }

    @Override
    public void lockForGeneratedResource(Long projectId, Long userId) {
        var project =
                repository
                        .findActiveSharedLockedByIdAndUserId(projectId, userId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "项目不存在或已删除"));
        entityManager.refresh(project, LockModeType.PESSIMISTIC_READ);
        requireWritable(project);
    }

    @Override
    @Transactional
    public void lockForCoverMutation(Long projectId, Long userId) {
        var project =
                repository
                        .findLockedById(projectId)
                        .filter(candidate -> !Boolean.TRUE.equals(candidate.getDeleted()))
                        .filter(candidate -> Objects.equals(candidate.getUserId(), userId))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "项目不存在或已删除"));
        entityManager.refresh(project, LockModeType.PESSIMISTIC_WRITE);
        requireWritable(project);
    }

    @Override
    @Transactional
    public void markCoverExecutionStarted(Long projectId, Long executionRunId) {
        var project = requireLockedProject(projectId, null, false);
        if (project.getCoverStatus() != AigcProjectCoverStatus.PENDING) {
            return;
        }
        if (project.getCoverExecutionRunId() != null
                && !Objects.equals(project.getCoverExecutionRunId(), executionRunId)) {
            throw badRequest("封面执行证据冲突");
        }
        project.setCoverExecutionRunId(executionRunId);
        repository.save(project);
    }

    @Override
    @Transactional
    public void markCoverGenerationFailed(Long projectId) {
        var project = requireLockedProject(projectId, null, false);
        if (project.getCoverStatus() == AigcProjectCoverStatus.PENDING
                && project.getCoverExecutionRunId() == null) {
            project.setCoverStatus(AigcProjectCoverStatus.FAILED);
            repository.save(project);
        }
    }

    @Override
    @Transactional
    public void markCoverExecutionTerminal(
            Long projectId, Long executionRunId, AigcProjectCoverStatus status) {
        var project = requireLockedProject(projectId, null, false);
        if (!Objects.equals(project.getCoverExecutionRunId(), executionRunId)) {
            return;
        }
        project.setCoverStatus(status);
        repository.save(project);
    }

    @Override
    @Transactional
    public boolean applyGeneratedCover(
            Long projectId,
            Long mediaVersionId,
            Long expectedCoverMediaVersionId,
            Long executionRunId) {
        var project = requireLockedProject(projectId, null, false);
        mediaApi.getByVersionId(mediaVersionId, project.getUserId());
        if (!MUTABLE_STATUSES.contains(project.getStatus())) {
            return false;
        }
        var currentRequest =
                Objects.equals(project.getCoverMediaVersionId(), expectedCoverMediaVersionId)
                        && Objects.equals(project.getCoverExecutionRunId(), executionRunId)
                        && project.getCoverStatus() == AigcProjectCoverStatus.PENDING;
        linkCoverMedia(project, mediaVersionId, currentRequest ? "adopted" : "candidate");
        if (!currentRequest) {
            return false;
        }
        project.setCoverMediaVersionId(mediaVersionId);
        project.setCoverStatus(AigcProjectCoverStatus.READY);
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
        repository.save(project);
        return true;
    }

    public AigcProjectVO requireProjectVO(Long projectId) {
        return toVO(requireEntity(projectId));
    }

    @Override
    public AigcProjectGraphView getGraph(Long projectId) {
        var project = requireEntity(projectId);
        var objects = objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        var objectsById =
                objects.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectObject::getId,
                                        java.util.function.Function.identity()));
        return new AigcProjectGraphView(
                toApiView(project),
                objects.stream().map(this::toApiObjectView).toList(),
                graphRelations(projectId).stream()
                        .map(
                                relation -> {
                                    var source = objectsById.get(relation.sourceObjectId());
                                    var target = objectsById.get(relation.targetObjectId());
                                    return new AigcProjectRelationView(
                                            relation.id(),
                                            relation.sourceObjectId(),
                                            relation.targetObjectId(),
                                            relation.relationType(),
                                            relation.layer(),
                                            JsonUtils.toJsonString(relation.metadata()),
                                            source == null ? null : source.getSemanticRole(),
                                            target == null ? null : target.getSemanticRole(),
                                            source == null ? null : source.getOutputContract(),
                                            target == null ? null : target.getOutputContract());
                                })
                        .toList(),
                project.getGraphRevision().longValue());
    }

    public AigcProjectGraphVO graph(Long projectId) {
        var project = requireEntity(projectId);
        var objects = objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        var objectsById =
                objects.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectObject::getId,
                                        java.util.function.Function.identity()));
        return new AigcProjectGraphVO(
                toVO(project),
                project.getGraphRevision(),
                objects.stream().map(this::toObjectVO).toList(),
                graphRelations(projectId).stream()
                        .map(relation -> toRelationVO(relation, objectsById))
                        .toList());
    }

    @Override
    public List<AigcObjectReadiness> readiness(Long projectId, AigcGenerationScope scope) {
        if (scope == null) {
            throw badRequest("readiness scope 不能为空");
        }
        var project = requireEntity(projectId);
        var objects = objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        var optionalIds = scope.includedOptionalObjectIds();
        if (optionalIds.size() != optionalIds.stream().distinct().count()) {
            throw badRequest("readiness scope 的 OPTIONAL 对象不能重复");
        }
        var byId =
                objects.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectObject::getId,
                                        java.util.function.Function.identity()));
        for (var optionalId : optionalIds) {
            var optional = byId.get(optionalId);
            if (optional == null || !"OPTIONAL".equals(optional.getContractRole())) {
                throw badRequest("readiness scope 只能显式纳入当前项目 OPTIONAL 对象");
            }
        }
        var selectedOptional = Set.copyOf(optionalIds);
        var snapshot =
                snapshotRepository
                        .findById(project.getConfigSnapshotId())
                        .filter(value -> projectId.equals(value.getProjectId()))
                        .orElseThrow(() -> notFound("项目配置快照不存在"));
        var actions = snapshot.getSnapshot().actions();
        var availableContextKeys =
                documentRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                        .filter(
                                reference ->
                                        reference.getFieldKey() != null
                                                && !reference.getFieldKey().isBlank())
                        .filter(
                                reference ->
                                        reference.getContextMode()
                                                != AigcProjectDocumentContextMode.NONE)
                        .filter(
                                reference ->
                                        reference.getReferenceType()
                                                        == AigcProjectDocumentReferenceType
                                                                .PROJECT_DOCUMENT
                                                ? reference.getSelectedDocumentVersionId() != null
                                                : reference.getSelectedDocumentVersionId() != null)
                        .map(AigcProjectDocumentRef::getFieldKey)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var dependencies = readinessDependencies(objects, actions);
        var candidatesByObject =
                versionRepository.findByProjectIdAndStatus(projectId, "candidate").stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        AigcObjectVersion::getObjectId));
        var activeReservedObjectIds =
                objects.isEmpty()
                        ? Set.<Long>of()
                        : reservationTargetRepository.findActiveReservedObjectIds(
                                objects.stream().map(AigcProjectObject::getId).toList());
        return objects.stream()
                .filter(
                        object ->
                                "REQUIRED".equals(object.getContractRole())
                                        || ("OPTIONAL".equals(object.getContractRole())
                                                && selectedOptional.contains(object.getId())))
                .map(
                        object ->
                                evaluateReadiness(
                                        project,
                                        object,
                                        dependencies.getOrDefault(object.getId(), List.of()),
                                        candidatesByObject,
                                        activeReservedObjectIds.contains(object.getId()),
                                        actions,
                                        availableContextKeys))
                .toList();
    }

    @Override
    public AigcProjectProgressSnapshot progress(Long projectId) {
        var project = requireEntity(projectId);
        var readiness = readiness(projectId, AigcGenerationScope.REQUIRED_ONLY);
        var readinessById =
                readiness.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcObjectReadiness::projectObjectId,
                                        java.util.function.Function.identity()));
        var objects = objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        var versions = versionRepository.findByProjectIdOrderByIdAsc(projectId);
        var candidateObjectIds =
                versions.stream()
                        .filter(version -> "candidate".equals(version.getStatus()))
                        .map(AigcObjectVersion::getObjectId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var mediaVersionIdsByObjectVersion =
                mediaRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                        .filter(reference -> reference.getObjectVersionId() != null)
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        AigcProjectMediaRef::getObjectVersionId,
                                        LinkedHashMap::new,
                                        java.util.stream.Collectors.mapping(
                                                AigcProjectMediaRef::getMediaVersionId,
                                                java.util.stream.Collectors.toList())));
        var execution = progressEvidencePort.load(projectId, project.getUserId());
        var completionEvidence = completionEvidencePort.load(projectId);
        var completion = deliveryService.completion(projectId);
        var reviews = deliveryService.reviews(projectId);
        return new AigcProjectProgressSnapshot(
                1,
                Instant.now(),
                projectId,
                project.getVersion(),
                project.getGraphRevision().longValue(),
                null,
                objects.stream()
                        .map(
                                object ->
                                        new AigcProjectProgressSnapshot.ObjectProgressRef(
                                                object.getId(),
                                                AigcProjectObjectMaturityProjector.project(
                                                        object,
                                                        candidateObjectIds.contains(
                                                                object.getId())),
                                                object.getContractRole(),
                                                readinessById.containsKey(object.getId())
                                                        ? readinessById.get(object.getId()).state()
                                                        : null,
                                                execution.creditsByObject().get(object.getId())))
                        .toList(),
                readiness,
                execution.executions(),
                versions.stream()
                        .filter(version -> !"MANIFEST_FREEZE".equals(version.getDecisionMode()))
                        .map(
                                version ->
                                        new AigcProjectProgressSnapshot.CandidateDecisionRef(
                                                version.getId(),
                                                version.getObjectId(),
                                                version.getStatus(),
                                                version.getExecutionRunId(),
                                                version.getDocumentVersionId(),
                                                mediaVersionIdsByObjectVersion.getOrDefault(
                                                        version.getId(), List.of())))
                        .toList(),
                new AigcProjectProgressSnapshot.DeliveryProgress(
                        completion.satisfied(),
                        reviews.size(),
                        reviews.stream()
                                .filter(review -> "PENDING".equals(review.status()))
                                .count(),
                        completionEvidence.activeWorkCount(),
                        completionEvidence.publicationCount(),
                        completion.blockers()),
                execution.credits());
    }

    private AigcObjectReadiness evaluateReadiness(
            AigcProject project,
            AigcProjectObject object,
            List<AigcProjectObject> dependencies,
            Map<Long, List<AigcObjectVersion>> candidatesByObject,
            boolean activeReservation,
            List<AigcBlueprintActionSpec> actions,
            Set<String> availableContextKeys) {
        var targetCandidates = candidatesByObject.getOrDefault(object.getId(), List.of());
        var inputVersions = new java.util.ArrayList<AigcFrozenInputVersionRef>();
        String dependencyBlocker = null;
        for (var dependency : dependencies) {
            if (dependency.getAdoptedVersionId() != null) {
                inputVersions.add(
                        new AigcFrozenInputVersionRef(
                                dependency.getId(), dependency.getAdoptedVersionId(), "ADOPTED"));
                continue;
            }
            // 阶段一没有 canonical TaskPlan 授权范围；"同一已授权 TaskPlan 唯一 candidate
            // 可作为中间输入"的例外只在阶段二 TaskPlan 落地后生效，此前必须先 adopted 才能
            // 作为下游输入，避免用户未决策的 candidate 被静默冻结进专业执行。
            var candidates = candidatesByObject.getOrDefault(dependency.getId(), List.of());
            dependencyBlocker =
                    candidates.isEmpty() ? "必需上游对象尚无 adopted 版本" : "必需上游对象存在待决 candidate，必须先完成采用";
            break;
        }
        var action = actionFor(object, actions);
        var missingContextKeys =
                action == null
                        ? List.<String>of()
                        : action.requiredContextKeys().stream()
                                .filter(key -> !availableContextKeys.contains(key))
                                .toList();
        var allowed = allowedActions(project, object, targetCandidates, action);
        var hasContentAction = allowed.stream().anyMatch(value -> !value.startsWith("object."));
        AigcObjectReadinessState state;
        String reason = null;
        if ("EXCLUDED".equals(object.getContractRole())) {
            state = AigcObjectReadinessState.BLOCKED_BY_POLICY;
            reason = "EXCLUDED 合同对象不进入阶段一产出范围";
        } else if (activeReservation) {
            state = AigcObjectReadinessState.ACTIVE_RUN;
            reason = "对象存在活动 ExecutionRun/reservation";
        } else if (!targetCandidates.isEmpty()) {
            state = AigcObjectReadinessState.PENDING_CANDIDATE;
            reason = "对象存在待决 candidate";
        } else if (object.getAdoptedVersionId() != null) {
            state = AigcObjectReadinessState.SATISFIED;
        } else if (!MUTABLE_STATUSES.contains(project.getStatus())) {
            state = AigcObjectReadinessState.BLOCKED_BY_POLICY;
            reason = "当前项目生命周期只读";
        } else if (!hasContentAction) {
            state = AigcObjectReadinessState.BLOCKED_BY_POLICY;
            reason = "outputContract、权限或动作策略没有阶段一可用内容动作";
        } else if (!missingContextKeys.isEmpty()) {
            state = AigcObjectReadinessState.BLOCKED_BY_CONTEXT;
            reason = "缺少必需项目上下文: " + String.join(", ", missingContextKeys);
        } else if (dependencyBlocker != null) {
            state = AigcObjectReadinessState.BLOCKED_BY_DEPENDENCY;
            reason = dependencyBlocker;
        } else if (action != null
                && allowed.contains(action.actionKey())
                && "REQUIRED".equalsIgnoreCase(action.confirmationPolicy())) {
            state = AigcObjectReadinessState.AWAITING_CONFIRMATION;
            reason = "专业媒体动作需要显式确认";
        } else {
            state = AigcObjectReadinessState.READY;
        }
        return new AigcObjectReadiness(
                object.getId(),
                action == null ? null : action.actionKey(),
                state,
                dependencies.stream().map(AigcProjectObject::getId).toList(),
                inputVersions,
                reason,
                allowed);
    }

    private Map<Long, List<AigcProjectObject>> readinessDependencies(
            List<AigcProjectObject> objects, List<AigcBlueprintActionSpec> actions) {
        if (objects.isEmpty()) {
            return Map.of();
        }
        var byId =
                objects.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectObject::getId,
                                        java.util.function.Function.identity()));
        var byTemplate =
                objects.stream()
                        .filter(object -> object.getBlueprintTemplateKey() != null)
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        AigcProjectObject::getBlueprintTemplateKey));
        var result = new LinkedHashMap<Long, LinkedHashSet<AigcProjectObject>>();
        for (var relation :
                relationRepository.findByProjectIdOrderByIdAsc(objects.getFirst().getProjectId())) {
            final AigcProjectRelationSemantics.Definition semantics;
            try {
                semantics = AigcProjectRelationSemantics.require(relation.getRelationType());
            } catch (IllegalArgumentException error) {
                throw badRequest(error.getMessage());
            }
            if (!semantics.affectsReadiness()) {
                continue;
            }
            var source = byId.get(relation.getSourceObjectId());
            if (source != null && byId.containsKey(relation.getTargetObjectId())) {
                result.computeIfAbsent(
                                relation.getTargetObjectId(), ignored -> new LinkedHashSet<>())
                        .add(source);
            }
        }
        for (var object : objects) {
            var action = actionFor(object, actions);
            if (action == null) {
                continue;
            }
            var target = result.computeIfAbsent(object.getId(), ignored -> new LinkedHashSet<>());
            action.dependencyTemplateKeys().stream()
                    .flatMap(key -> byTemplate.getOrDefault(key, List.of()).stream())
                    .forEach(target::add);
        }
        var immutable = new LinkedHashMap<Long, List<AigcProjectObject>>();
        result.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return Map.copyOf(immutable);
    }

    private AigcBlueprintActionSpec actionFor(
            AigcProjectObject object, List<AigcBlueprintActionSpec> actions) {
        return actions.stream()
                .filter(
                        action ->
                                Objects.equals(
                                        object.getBlueprintTemplateKey(),
                                        action.targetTemplateKey()))
                .findFirst()
                .orElse(null);
    }

    private List<String> allowedActions(
            AigcProject project,
            AigcProjectObject object,
            List<AigcObjectVersion> candidates,
            AigcBlueprintActionSpec action) {
        if (!MUTABLE_STATUSES.contains(project.getStatus())
                || "review".equals(object.getObjectType())) {
            return List.of();
        }
        var allowed = new LinkedHashSet<String>();
        var canUpdate = hasPermission(AigcAuthorities.PROJECT_UPDATE);
        if (canUpdate) {
            allowed.add("object.contract");
            allowed.add("object.move");
            if (object.getBlueprintTemplateKey() == null
                    || requireSlotTemplate(project, object.getBlueprintTemplateKey())
                            .userRemovable()) {
                allowed.add("object.remove");
            }
        }
        if ("EXCLUDED".equals(object.getContractRole())) {
            return List.copyOf(allowed);
        }
        if (canUpdate && "DOCUMENT_VERSION".equals(object.getOutputContract())) {
            allowed.add("candidate.record.document");
        } else if (hasPermission(AigcAuthorities.PROJECT_ACTION)
                && "MEDIA_VERSION".equals(object.getOutputContract())
                && action != null
                && Set.of("image.generate", "image.edit", "video.generate")
                        .contains(action.actionKey())) {
            allowed.add(action.actionKey());
        }
        if (!candidates.isEmpty() && hasPermission(AigcAuthorities.OBJECT_VERSION_ADOPT)) {
            allowed.add("candidate.adopt");
            allowed.add("candidate.reject");
        }
        return List.copyOf(allowed);
    }

    private boolean hasPermission(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && permissionEvaluator.hasPermission(authentication, null, permission);
    }

    public AigcProjectSummaryVO summary(Long projectId) {
        var project = requireEntity(projectId);
        var objects = objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        var versions =
                objects.stream()
                        .flatMap(
                                object ->
                                        versionRepository
                                                .findByObjectIdOrderByVersionNoDesc(object.getId())
                                                .stream())
                        .toList();
        var candidateObjectIds =
                versions.stream()
                        .filter(version -> "candidate".equals(version.getStatus()))
                        .map(AigcObjectVersion::getObjectId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var executionEvidence = executionEvidencePort.load(projectId);
        var completionEvidence = completionEvidencePort.load(projectId);
        var completionEvaluation = deliveryService.completion(projectId);
        var readiness = readiness(projectId, AigcGenerationScope.REQUIRED_ONLY);
        var blockedCount =
                readiness.stream()
                        .filter(
                                value ->
                                        value.state()
                                                        == AigcObjectReadinessState
                                                                .BLOCKED_BY_DEPENDENCY
                                                || value.state()
                                                        == AigcObjectReadinessState
                                                                .BLOCKED_BY_POLICY)
                        .count();
        return new AigcProjectSummaryVO(
                projectId,
                objects.size(),
                objects.stream()
                        .filter(object -> object.getObjectType().endsWith("_deliverable"))
                        .count(),
                objects.stream()
                        .filter(
                                object ->
                                        "CANDIDATE_AVAILABLE"
                                                .equals(
                                                        AigcProjectObjectMaturityProjector.project(
                                                                object,
                                                                candidateObjectIds.contains(
                                                                        object.getId()))))
                        .count(),
                blockedCount,
                executionEvidence.runningCount(),
                versions.stream()
                        .filter(version -> "candidate".equals(version.getStatus()))
                        .count(),
                versions.stream().filter(version -> "adopted".equals(version.getStatus())).count(),
                completionEvidence.activeWorkCount(),
                completionEvidence.publicationCount(),
                completionEvidence.activePublicationCount(),
                completionEvaluation.satisfied(),
                project.getCostUsed());
    }

    @Override
    @Transactional
    public AigcProjectObjectView appendObject(AigcProjectObjectCommand command) {
        var project = requireLockedProject(command.projectId(), command.expectedProjectVersion());
        requireWritable(project);
        requireGraphRevision(project, command.expectedGraphRevision());
        if (command.objectType() == null || command.objectType().isBlank()) {
            throw badRequest("对象类型不能为空");
        }
        if (command.displayName() == null || command.displayName().isBlank()) {
            throw badRequest("对象名称不能为空");
        }
        var parentObject =
                command.parentObjectId() == null
                        ? null
                        : requireObject(project.getId(), command.parentObjectId(), false);
        var template =
                command.blueprintTemplateKey() == null
                        ? null
                        : requireSlotTemplate(project, command.blueprintTemplateKey());
        if (template != null && !template.userAddable()) {
            throw badRequest("该槽位模板不允许用户追加实例");
        }
        if (template != null && !command.objectType().equals(template.objectType())) {
            throw badRequest("对象类型与槽位模板不匹配");
        }
        validateObjectParent(
                project, command.blueprintTemplateKey(), command.objectType(), parentObject);
        if (template != null) {
            if (template.maxCount() == null) {
                throw badRequest("槽位模板 maxCount 非法");
            }
            var currentCount =
                    objectRepository
                            .findByProjectIdOrderBySortOrderAscIdAsc(project.getId())
                            .stream()
                            .filter(
                                    object ->
                                            command.blueprintTemplateKey()
                                                    .equals(object.getBlueprintTemplateKey()))
                            .count();
            if (currentCount >= template.maxCount()) {
                throw badRequest("模板实例数已达上限");
            }
        } else {
            if (parentObject == null) {
                throw badRequest("自定义对象必须指定所属 DeliverableSet");
            }
            var snapshot =
                    snapshotRepository
                            .findFirstByProjectIdOrderByRevisionNoDesc(project.getId())
                            .orElseThrow(() -> badRequest("对象类型不在交付包允许的自定义对象白名单内"));
            var setTemplateKey = parentObject.getBlueprintTemplateKey();
            var allowedCustomObjectTypes =
                    snapshot.getSnapshot().deliverableSets().stream()
                            .filter(set -> Objects.equals(setTemplateKey, set.setTemplateKey()))
                            .map(
                                    com.xuejiai.aaf.module.ai.aigc.configuration.api
                                                    .AigcDeliverableSetSpec
                                            ::allowedCustomObjectTypes)
                            .findFirst()
                            .orElseThrow(() -> badRequest("对象类型不在交付包允许的自定义对象白名单内"));
            if (!allowedCustomObjectTypes.contains(command.objectType())) {
                throw badRequest("对象类型不在交付包允许的自定义对象白名单内");
            }
        }
        var instanceNo =
                template == null
                        ? objectRepository.findHistoricalMaxCustomInstanceNo(
                                        project.getId(), command.objectType())
                                + 1
                        : objectRepository.findHistoricalMaxInstanceNo(
                                        project.getId(), command.blueprintTemplateKey())
                                + 1;
        var stableKey =
                template == null
                        ? "custom.%s.%02d".formatted(command.objectType(), instanceNo)
                        : formatStableKey(template.stableKeyPattern(), instanceNo);
        if (objectRepository.findByProjectIdAndStableKey(project.getId(), stableKey).isPresent()) {
            throw badRequest("项目对象 stableKey 已存在");
        }
        var object = new AigcProjectObject();
        copyScope(project, object);
        object.setProjectId(project.getId());
        object.setParentId(command.parentObjectId());
        object.setStableKey(stableKey);
        object.setBlueprintTemplateKey(command.blueprintTemplateKey());
        object.setInstanceNo(instanceNo);
        object.setContractRole(requireContractRole(command.contractRole()));
        object.setSemanticRole(
                template == null
                        ? requireSemanticRole(command.semanticRole())
                        : template.semanticRole());
        object.setPlannedPhase(LEGACY_PLANNED_PHASE_PLACEHOLDER);
        object.setOutputContract(
                template == null
                        ? requireOutputContract(command.outputContract())
                        : template.outputContract());
        object.setObjectType(command.objectType());
        object.setSortOrder(command.orderNo() == null ? 0 : command.orderNo());
        object.setTitle(command.displayName().trim());
        object.setStatus("EMPTY");
        object.setSource("user");
        object.setSchemaVersion(command.schemaVersion());
        object.setPayload(parseMap(command.payloadJson()));
        objectRepository.save(object);
        bumpStructuralRevision(project, List.of(object.getId()), List.of(), "追加项目对象");
        deliveryService.staleReviews(
                project.getId(),
                command.parentObjectId() == null
                        ? List.of(object.getId())
                        : List.of(command.parentObjectId(), object.getId()),
                "DeliverableSet 结构已变化");
        return toApiObjectView(object);
    }

    @Override
    @Transactional
    public AigcProjectObjectView moveObject(AigcProjectObjectMoveCommand command) {
        var project = requireLockedProject(command.projectId(), command.expectedProjectVersion());
        requireWritable(project);
        requireGraphRevision(project, command.expectedGraphRevision());
        var object = requireObject(project.getId(), command.objectId(), true);
        var newParent =
                command.newParentObjectId() == null
                        ? null
                        : requireObject(project.getId(), command.newParentObjectId(), false);
        if (newParent != null && Objects.equals(newParent.getId(), object.getId())) {
            throw badRequest("对象不能移动到自身下级");
        }
        var cursor = newParent;
        while (cursor != null) {
            if (Objects.equals(cursor.getId(), object.getId())) {
                throw badRequest("对象移动会形成层级环");
            }
            cursor =
                    cursor.getParentId() == null
                            ? null
                            : requireObject(project.getId(), cursor.getParentId(), false);
        }
        if (moveAffectedObjectIds(project.getId(), object, newParent).stream()
                .anyMatch(reservationTargetRepository::existsActiveReservationByProjectObjectId)) {
            throw badRequest("移动对象子树或父级正被执行占用");
        }
        validateObjectParent(
                project, object.getBlueprintTemplateKey(), object.getObjectType(), newParent);
        var oldParentId = object.getParentId();
        object.setParentId(newParent == null ? null : newParent.getId());
        object.setSortOrder(command.orderNo() == null ? object.getSortOrder() : command.orderNo());
        objectRepository.save(object);
        var changed = new LinkedHashSet<Long>();
        changed.add(object.getId());
        if (oldParentId != null) {
            changed.add(oldParentId);
        }
        if (newParent != null) {
            changed.add(newParent.getId());
        }
        bumpStructuralRevision(project, List.copyOf(changed), List.of(), "移动项目对象");
        deliveryService.staleReviews(project.getId(), changed, "DeliverableSet 层级已变化");
        return toApiObjectView(object);
    }

    @Override
    @Transactional
    public AigcProjectObjectView updateObjectContract(AigcProjectObjectContractCommand command) {
        var project = requireLockedProject(command.projectId(), command.expectedProjectVersion());
        requireWritable(project);
        requireGraphRevision(project, command.expectedGraphRevision());
        var object = requireObject(project.getId(), command.objectId(), true);
        var contractRole = requireContractRole(command.contractRole());
        if ("EXCLUDED".equals(contractRole)
                && reservationTargetRepository.existsActiveReservationByProjectObjectId(
                        object.getId())) {
            throw badRequest("对象正被执行占用，不能排除");
        }
        object.setContractRole(contractRole);
        object.setStatus(
                AigcProjectObjectMaturityProjector.project(
                        object,
                        !versionRepository
                                .findByObjectIdAndStatus(object.getId(), "candidate")
                                .isEmpty()));
        objectRepository.save(object);
        bumpStructuralRevision(project, List.of(object.getId()), List.of(), "更新对象合同");
        deliveryService.staleReviews(
                project.getId(),
                object.getParentId() == null
                        ? List.of(object.getId())
                        : List.of(object.getId(), object.getParentId()),
                "交付对象合同角色已变化");
        return toApiObjectView(object);
    }

    @Override
    @Transactional
    public AigcProjectObjectView removeObject(AigcProjectObjectRemoveCommand command) {
        var project = requireLockedProject(command.projectId(), command.expectedProjectVersion());
        requireWritable(project);
        requireGraphRevision(project, command.expectedGraphRevision());
        var object = requireObject(project.getId(), command.objectId(), true);
        var parentId = object.getParentId();
        if (objectRepository.existsByProjectIdAndParentIdAndDeletedFalse(
                project.getId(), object.getId())) {
            throw badRequest("存在子对象，不能删除");
        }
        if (reservationTargetRepository.existsActiveReservationByProjectObjectId(object.getId())) {
            throw badRequest("对象正被执行占用，不能删除");
        }
        var hasHistory =
                versionRepository.existsByObjectId(object.getId())
                        || relationRepository.existsBySourceObjectIdOrTargetObjectId(
                                object.getId(), object.getId())
                        || reservationTargetRepository.existsByProjectObjectId(object.getId());
        if (hasHistory) {
            object.setContractRole("EXCLUDED");
            object.setStatus(
                    AigcProjectObjectMaturityProjector.project(
                            object,
                            !versionRepository
                                    .findByObjectIdAndStatus(object.getId(), "candidate")
                                    .isEmpty()));
            objectRepository.save(object);
        } else {
            objectRepository.delete(object);
        }
        bumpStructuralRevision(
                project,
                List.of(object.getId()),
                List.of(),
                command.reason() == null ? "移除项目对象" : command.reason());
        deliveryService.staleReviews(
                project.getId(),
                parentId == null ? List.of(object.getId()) : List.of(object.getId(), parentId),
                "交付对象已移除或排除");
        return toApiObjectView(object);
    }

    @Override
    @Transactional
    public AigcProjectExecutionReservationView reserveExecution(
            AigcProjectExecutionReservationCommand command) {
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        var project =
                repository.findLockedById(command.projectId()).orElseThrow(() -> notFound("项目不存在"));
        entityManager.refresh(project, LockModeType.PESSIMISTIC_WRITE);
        var requestHash = reservationRequestHash(command);
        var existingBySubmission =
                reservationRepository.findByProjectIdAndExecutionSubmissionId(
                        project.getId(), command.executionSubmissionId());
        var existingByKey =
                reservationRepository.findByProjectIdAndIdempotencyKey(
                        project.getId(), command.idempotencyKey());
        if (existingBySubmission.isPresent() || existingByKey.isPresent()) {
            var existing = existingBySubmission.orElseGet(existingByKey::orElseThrow);
            if (existingBySubmission.isPresent()
                    && existingByKey.isPresent()
                    && !existingBySubmission.get().getId().equals(existingByKey.get().getId())) {
                throw new BusinessException(409, "执行 reservation 幂等键冲突");
            }
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new BusinessException(409, "执行 reservation 同幂等键请求参数冲突");
            }
            return toReservationView(existing);
        }
        requireWritable(project);
        if (command.expectedGraphRevision() == null
                || command.expectedGraphRevision() != project.getGraphRevision().longValue()) {
            throw badRequest("项目图谱版本已变化");
        }
        if (command.commandObjectId() != null) {
            requireObject(project.getId(), command.commandObjectId(), true);
        }
        var targetIds = resolveReservationTargets(project.getId(), command);
        var reservation = new AigcProjectExecutionReservation();
        copyScope(project, reservation);
        reservation.setExecutionSubmissionId(command.executionSubmissionId());
        reservation.setProjectId(project.getId());
        reservation.setCommandObjectId(command.commandObjectId());
        reservation.setActionKey(command.actionKey());
        reservation.setTargetGraphRevision(command.expectedGraphRevision());
        reservation.setStatus("PREPARED");
        reservation.setIdempotencyKey(command.idempotencyKey());
        reservation.setRequestHash(requestHash);
        reservationRepository.saveAndFlush(reservation);
        for (var targetId : targetIds) {
            var target = new AigcProjectExecutionReservationTarget();
            target.setReservationId(reservation.getId());
            target.setProjectObjectId(targetId);
            reservationTargetRepository.save(target);
        }
        deliveryService.staleReviews(project.getId(), targetIds, "交付对象进入执行 reservation");
        return toReservationView(reservation);
    }

    @Override
    @Transactional
    public AigcProjectExecutionReservationView bindExecution(
            AigcProjectExecutionReservationBindCommand command) {
        var snapshot =
                reservationRepository
                        .findById(command.reservationId())
                        .orElseThrow(() -> notFound("执行 reservation 不存在"));
        requireEntity(snapshot.getProjectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        var project =
                repository
                        .findLockedById(snapshot.getProjectId())
                        .orElseThrow(() -> notFound("项目不存在"));
        entityManager.refresh(project, LockModeType.PESSIMISTIC_WRITE);
        var reservation =
                reservationRepository
                        .findLockedById(command.reservationId())
                        .filter(
                                candidate ->
                                        Objects.equals(candidate.getProjectId(), project.getId()))
                        .orElseThrow(() -> notFound("执行 reservation 不存在"));
        requireSameSubmission(reservation, command.executionSubmissionId());
        if ("BOUND".equals(reservation.getStatus())
                && Objects.equals(
                        reservation.getRootExecutionRunId(), command.rootExecutionRunId())) {
            return toReservationView(reservation);
        }
        if ("RELEASED".equals(reservation.getStatus())
                && "ROOT_TERMINAL".equals(reservation.getReleaseReason())
                && Objects.equals(
                        reservation.getRootExecutionRunId(), command.rootExecutionRunId())) {
            reservation.setStatus("BOUND");
            reservation.setReleaseReason(null);
            reservationRepository.save(reservation);
        } else {
            if (!"PREPARED".equals(reservation.getStatus())
                    || reservation.getRootExecutionRunId() != null) {
                throw badRequest("执行 reservation 不能绑定");
            }
            reservation.setRootExecutionRunId(command.rootExecutionRunId());
            reservation.setStatus("BOUND");
            reservationRepository.save(reservation);
        }
        if (!AigcProjectLifecycle.EXECUTING.equals(project.getStatus())) {
            if (!MUTABLE_STATUSES.contains(project.getStatus())) {
                throw badRequest("当前项目阶段不能绑定执行");
            }
            project.setStatus(AigcProjectLifecycle.EXECUTING);
            project.setVersion(project.getVersion() + 1);
            project.setLastActiveTime(LocalDateTime.now());
            repository.save(project);
        }
        return toReservationView(reservation);
    }

    @Override
    @Transactional
    public AigcProjectExecutionReservationView releaseExecution(
            AigcProjectExecutionReservationReleaseCommand command) {
        var snapshot =
                reservationRepository
                        .findById(command.reservationId())
                        .orElseThrow(() -> notFound("执行 reservation 不存在"));
        requireEntity(snapshot.getProjectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        var project =
                repository
                        .findLockedById(snapshot.getProjectId())
                        .orElseThrow(() -> notFound("项目不存在"));
        entityManager.refresh(project, LockModeType.PESSIMISTIC_WRITE);
        var reservation =
                reservationRepository
                        .findLockedById(command.reservationId())
                        .filter(
                                candidate ->
                                        Objects.equals(candidate.getProjectId(), project.getId()))
                        .orElseThrow(() -> notFound("执行 reservation 不存在"));
        requireSameSubmission(reservation, command.executionSubmissionId());
        if ("RELEASED".equals(reservation.getStatus())) {
            if (Objects.equals(reservation.getReleaseReason(), command.releaseReason())
                    && Objects.equals(
                            reservation.getRootExecutionRunId(), command.rootExecutionRunId())) {
                return toReservationView(reservation);
            }
            throw badRequest("执行 reservation 释放参数冲突");
        }
        var valid =
                switch (command.releaseReason()) {
                    case "PRE_BIND_CANCEL" ->
                            "PREPARED".equals(reservation.getStatus())
                                    && reservation.getRootExecutionRunId() == null
                                    && command.rootExecutionRunId() == null;
                    case "ROOT_TERMINAL" ->
                            "BOUND".equals(reservation.getStatus())
                                    && Objects.equals(
                                            reservation.getRootExecutionRunId(),
                                            command.rootExecutionRunId());
                    default -> false;
                };
        if (!valid) {
            throw badRequest("执行 reservation 释放状态冲突");
        }
        reservation.setStatus("RELEASED");
        reservation.setReleaseReason(command.releaseReason());
        reservationRepository.saveAndFlush(reservation);
        if ("ROOT_TERMINAL".equals(command.releaseReason())
                && AigcProjectLifecycle.EXECUTING.equals(project.getStatus())
                && !reservationRepository.existsByProjectIdAndStatusIn(
                        project.getId(), Set.of("PREPARED", "BOUND"))) {
            project.setStatus(AigcProjectLifecycle.CREATING);
            project.setVersion(project.getVersion() + 1);
            project.setLastActiveTime(LocalDateTime.now());
            repository.save(project);
        }
        return toReservationView(reservation);
    }

    @Override
    @Transactional
    public AigcProjectMediaRefView attachMedia(AigcProjectMediaRefCommand command) {
        var project = requireLockedProject(command.projectId(), command.expectedProjectVersion());
        requireWritable(project);
        if (command.projectObjectId() != null) {
            requireObject(project.getId(), command.projectObjectId(), false);
        }
        mediaApi.getByVersionId(command.mediaVersionId(), project.getUserId());
        var existingReference =
                mediaRefRepository
                        .findByProjectIdAndMediaVersionIdAndRole(
                                project.getId(), command.mediaVersionId(), command.role())
                        .stream()
                        .filter(
                                existing ->
                                        Objects.equals(
                                                existing.getObjectId(), command.projectObjectId()))
                        .findFirst();
        if (existingReference.isPresent()) {
            return toApiMediaRefView(existingReference.get());
        }
        var reference = new AigcProjectMediaRef();
        copyScope(project, reference);
        reference.setProjectId(project.getId());
        reference.setObjectId(command.projectObjectId());
        reference.setMediaVersionId(command.mediaVersionId());
        reference.setRole(command.role());
        reference.setSortOrder(command.orderNo() == null ? 0 : command.orderNo());
        reference.setAdoptionStatus(
                command.adoptionStatus() == null ? "candidate" : command.adoptionStatus());
        mediaRefRepository.save(reference);
        bumpRevision(project, List.of(), List.of(), null, "关联项目媒体");
        if (command.projectObjectId() != null) {
            deliveryService.staleReviews(
                    project.getId(), List.of(command.projectObjectId()), "交付对象媒体引用已变化");
        }
        return toApiMediaRefView(reference);
    }

    @Override
    @Transactional
    public void detachMedia(
            Long projectId, Long projectMediaRefId, Integer expectedProjectVersion) {
        var project = requireLockedProject(projectId, expectedProjectVersion);
        requireWritable(project);
        var reference =
                mediaRefRepository
                        .findById(projectMediaRefId)
                        .filter(candidate -> projectId.equals(candidate.getProjectId()))
                        .orElseThrow(() -> notFound("项目媒体引用不存在"));
        if (reference.getObjectId() != null && reference.getObjectVersionId() != null) {
            var object =
                    objectRepository
                            .findById(reference.getObjectId())
                            .filter(candidate -> projectId.equals(candidate.getProjectId()));
            if (object.isPresent()
                    && reference.getObjectVersionId().equals(object.get().getAdoptedVersionId())) {
                throw badRequest("已采用版本的制品引用不能原地解除，请先切换采用版本");
            }
        }
        mediaRefRepository.delete(reference);
        bumpRevision(project, List.of(), List.of(), null, "解除项目媒体引用");
        if (reference.getObjectId() != null) {
            deliveryService.staleReviews(
                    project.getId(), List.of(reference.getObjectId()), "交付对象媒体引用已变化");
        }
    }

    @Override
    @Transactional
    public AigcObjectVersionView appendCandidate(AigcObjectVersionCandidateCommand command) {
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        return deliveryService.appendCandidate(command);
    }

    @Override
    @Transactional
    public AigcObjectVersionView recordTextCandidate(AigcTextCandidateCommand command) {
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        return deliveryService.recordTextCandidate(command);
    }

    @Override
    @Transactional
    public AigcObjectVersionView recordDocumentCandidate(AigcDocumentCandidateCommand command) {
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        return deliveryService.recordDocumentCandidate(command);
    }

    @Override
    public AigcObjectVersionView requireObjectVersion(
            Long projectId, Long objectId, Long objectVersionId) {
        requireEntity(projectId);
        requireObject(projectId, objectId, false);
        return toApiVersionView(requireVersion(projectId, objectId, objectVersionId));
    }

    @Override
    public AigcObjectVersionComparisonView compareObjectVersions(
            Long projectId, Long objectId, Long leftObjectVersionId, Long rightObjectVersionId) {
        requireEntity(projectId);
        return deliveryService.compare(
                projectId, objectId, leftObjectVersionId, rightObjectVersionId);
    }

    @Override
    public AigcProjectMediaRefView requireAdoptedMediaVersion(
            Long projectId, Long objectId, Long mediaVersionId) {
        requireEntity(projectId);
        return mediaRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                .filter(reference -> mediaVersionId.equals(reference.getMediaVersionId()))
                .filter(reference -> objectId == null || objectId.equals(reference.getObjectId()))
                .filter(reference -> "adopted".equals(reference.getAdoptionStatus()))
                .findFirst()
                .map(this::toApiMediaRefView)
                .orElseThrow(() -> notFound("项目中不存在已采用的媒体版本"));
    }

    @Override
    @Transactional
    @PreAuthorize(AigcAuthorities.HAS_OBJECT_VERSION_ADOPT)
    public AigcObjectVersionView adoptVersion(AigcObjectVersionAdoptCommand command) {
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        return deliveryService.adopt(command);
    }

    @Override
    @Transactional
    @PreAuthorize(AigcAuthorities.HAS_OBJECT_VERSION_ADOPT)
    public AigcObjectVersionView rejectVersion(AigcObjectVersionRejectCommand command) {
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        return deliveryService.reject(command);
    }

    @Override
    public AigcDeliverableSetCompletionView evaluateDeliverableSet(
            AigcDeliverableSetEvaluateCommand command) {
        requireEntity(command.projectId());
        return deliveryService.evaluate(command);
    }

    @Override
    @Transactional
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    public AigcObjectVersionView freezeDeliverableSetManifest(
            AigcDeliverableSetManifestFreezeCommand command) {
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        return deliveryService.freeze(command);
    }

    @Override
    @Transactional
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_REVIEW)
    public AigcReviewView submitReview(AigcReviewSubmitCommand command) {
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        return deliveryService.submitReview(command);
    }

    @Override
    @Transactional
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_REVIEW)
    public AigcReviewView approveReview(AigcReviewDecisionCommand command) {
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        return deliveryService.approveReview(command);
    }

    @Override
    @Transactional
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_REVIEW)
    public AigcReviewView returnReview(AigcReviewDecisionCommand command) {
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        return deliveryService.returnReview(command);
    }

    @Override
    public List<AigcReviewView> reviews(Long projectId) {
        requireEntity(projectId);
        return deliveryService.reviews(projectId);
    }

    @Override
    public AigcApprovedManifestView requireApprovedManifest(
            Long projectId, Long deliverableSetObjectId, Long manifestObjectVersionId) {
        requireEntity(projectId);
        return deliveryService.requireApprovedManifest(
                projectId, deliverableSetObjectId, manifestObjectVersionId);
    }

    @Override
    public AigcCompletionEvaluationView completionEvidence(Long projectId) {
        requireEntity(projectId);
        return deliveryService.completion(projectId);
    }

    @Override
    @Transactional
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_LIFECYCLE)
    public AigcProjectView complete(AigcProjectLifecycleCommand command) {
        requireLifecycleCommand(command, false);
        var requestHash = lifecycleRequestHash("COMPLETE", command);
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        var project =
                repository.findLockedById(command.projectId()).orElseThrow(() -> notFound("项目不存在"));
        entityManager.refresh(project, LockModeType.PESSIMISTIC_WRITE);
        if (AigcProjectLifecycle.COMPLETED.equals(project.getStatus())) {
            requireLifecycleReplay(project, command.idempotencyKey(), requestHash);
            return toApiView(project);
        }
        requireExpectedVersion(project, command.expectedProjectVersion());
        if (!AigcProjectLifecycle.DELIVERING.equals(project.getStatus())) {
            throw badRequest("项目必须先进入 DELIVERING 阶段");
        }
        var evaluation = deliveryService.completion(project.getId());
        if (!evaluation.satisfied()) {
            throw badRequest("项目完成条件未满足: " + String.join("；", evaluation.blockers()));
        }
        project.setStatus(AigcProjectLifecycle.COMPLETED);
        project.setLifecycleIdempotencyKey(command.idempotencyKey());
        project.setLifecycleRequestHash(requestHash);
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
        repository.save(project);
        publishProjectLifecycle(project);
        return toApiView(project);
    }

    @Override
    @Transactional
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_LIFECYCLE)
    public AigcProjectView archive(AigcProjectLifecycleCommand command) {
        requireLifecycleCommand(command, true);
        var requestHash = lifecycleRequestHash("ARCHIVE", command);
        requireEntity(command.projectId(), CrudOperation.UPDATE, AccessMode.DEFAULT);
        var project =
                repository.findLockedById(command.projectId()).orElseThrow(() -> notFound("项目不存在"));
        entityManager.refresh(project, LockModeType.PESSIMISTIC_WRITE);
        if (AigcProjectLifecycle.ARCHIVED.equals(project.getStatus())) {
            requireLifecycleReplay(project, command.idempotencyKey(), requestHash);
            return toApiView(project);
        }
        requireExpectedVersion(project, command.expectedProjectVersion());
        project.setStatus(AigcProjectLifecycle.ARCHIVED);
        project.setLifecycleIdempotencyKey(command.idempotencyKey());
        project.setLifecycleRequestHash(requestHash);
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
        repository.save(project);
        publishProjectLifecycle(project);
        eventPublisher.publishEvent(
                new AigcProjectArchivedEvent(UUID.randomUUID(), project.getId(), Instant.now()));
        return toApiView(project);
    }

    public List<AigcProjectObjectVO> objects(Long projectId) {
        requireEntity(projectId);
        return objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                .map(this::toObjectVO)
                .toList();
    }

    public List<AigcObjectVersionVO> versions(Long projectId, Long objectId) {
        requireObject(projectId, objectId, false);
        return versionRepository.findByObjectIdOrderByVersionNoDesc(objectId).stream()
                .map(this::toVersionVO)
                .toList();
    }

    public List<AigcProjectRevisionVO> revisions(Long projectId) {
        requireEntity(projectId);
        return revisionRepository.findByProjectIdOrderByRevisionNoDesc(projectId).stream()
                .map(
                        revision ->
                                new AigcProjectRevisionVO(
                                        revision.getId(),
                                        revision.getProjectId(),
                                        revision.getConfigSnapshotId(),
                                        revision.getRevisionNo(),
                                        revision.getProjectVersion(),
                                        revision.getGraphRevision(),
                                        revision.getChangedObjectIds(),
                                        revision.getChangedRelationIds(),
                                        revision.getActorType(),
                                        revision.getActorId(),
                                        revision.getSourceExecutionRunId(),
                                        revision.getSummary(),
                                        revision.getCreateTime()))
                .toList();
    }

    public List<AigcProjectConfigSnapshotVO> configuration(Long projectId) {
        requireEntity(projectId);
        return snapshotRepository.findByProjectIdOrderByRevisionNoDesc(projectId).stream()
                .map(
                        snapshot ->
                                new AigcProjectConfigSnapshotVO(
                                        snapshot.getId(),
                                        snapshot.getRevisionNo(),
                                        snapshot.getSourceType(),
                                        snapshot.getSourceRef(),
                                        snapshot.getSnapshotHash(),
                                        snapshot.getProjectTypeVersion(),
                                        snapshot.getBlueprintVersion(),
                                        snapshot.getChannelVersions(),
                                        snapshot.getExecutionBindingVersions(),
                                        snapshot.getCompatibilityResult(),
                                        snapshot.getSnapshot()))
                .toList();
    }

    public List<AigcProjectProfileRefVO> profileRefs(Long projectId) {
        requireEntity(projectId);
        return profileRefRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                .map(
                        reference ->
                                new AigcProjectProfileRefVO(
                                        reference.getId(),
                                        reference.getBrandProfileVersionId(),
                                        reference.getRefScope(),
                                        reference.getScopeNote()))
                .toList();
    }

    public List<AigcProjectChannelRefVO> channelRefs(Long projectId) {
        requireEntity(projectId);
        return channelRefRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                .map(
                        reference ->
                                new AigcProjectChannelRefVO(
                                        reference.getId(),
                                        reference.getChannelSpecId(),
                                        reference.getPrimaryChannel(),
                                        reference.getOverrideConfig()))
                .toList();
    }

    @Transactional
    public AigcProjectDocumentRefVO attachDocument(
            Long projectId, AigcProjectDocumentRefDTO request) {
        var project = requireLockedProject(projectId, request.expectedProjectVersion());
        requireWritable(project);
        requireDocumentReferenceIdentity(request);
        if (request.objectId() != null) {
            requireObject(projectId, request.objectId(), false);
        }
        var role =
                request.role() == null || request.role().isBlank()
                        ? "project"
                        : request.role().trim();
        DocumentVersionApi.DocumentVersionView fixedVersion = null;
        DocumentVersionApi.DocumentVersionView currentVersion;
        java.util.Optional<AigcProjectDocumentRef> existing;
        switch (request.referenceType()) {
            case PROJECT_DOCUMENT -> {
                currentVersion =
                        requireCurrentDocumentVersions(project, List.of(request.documentId()))
                                .get(request.documentId());
                existing =
                        documentRefRepository.findByProjectIdAndReferenceTypeAndDocumentIdAndRole(
                                projectId, request.referenceType(), request.documentId(), role);
            }
            case EXTERNAL_REFERENCE -> {
                fixedVersion =
                        documentVersionApi.requireAccessible(
                                request.documentVersionId(),
                                project.getOwnerId(),
                                project.getOrgId(),
                                project.getWorkspaceId());
                currentVersion =
                        requireCurrentDocumentVersions(project, List.of(fixedVersion.documentId()))
                                .get(fixedVersion.documentId());
                existing =
                        documentRefRepository
                                .findByProjectIdAndReferenceTypeAndSelectedDocumentVersionIdAndRole(
                                        projectId,
                                        request.referenceType(),
                                        request.documentVersionId(),
                                        role);
            }
            default -> throw badRequest("不支持的项目文档引用类型");
        }
        if (existing.isPresent()) {
            return toDocumentRefVO(existing.get(), fixedVersion, currentVersion);
        }

        var reference = new AigcProjectDocumentRef();
        copyScope(project, reference);
        reference.setProjectId(projectId);
        reference.setObjectId(request.objectId());
        reference.setReferenceType(request.referenceType());
        reference.setDocumentId(
                request.referenceType() == AigcProjectDocumentReferenceType.EXTERNAL_REFERENCE
                        ? fixedVersion.documentId()
                        : request.documentId());
        reference.setSelectedDocumentVersionId(
                request.referenceType() == AigcProjectDocumentReferenceType.EXTERNAL_REFERENCE
                        ? fixedVersion.id()
                        : currentVersion.id());
        reference.setFieldKey(request.fieldKey());
        reference.setContextNote(request.contextNote());
        reference.setContextMode(
                request.contextMode() == null
                        ? AigcProjectDocumentContextMode.AUTO
                        : request.contextMode());
        if (request.referenceType() == AigcProjectDocumentReferenceType.PROJECT_DOCUMENT) {
            reference.setSelectedDocumentVersionId(currentVersion.id());
        }
        reference.setRole(role);
        reference.setSortOrder(
                request.sortOrder() == null
                        ? documentRefRepository
                                .findByProjectIdOrderBySortOrderAscIdAsc(projectId)
                                .size()
                        : request.sortOrder());
        documentRefRepository.save(reference);
        bumpRevision(project, List.of(), List.of(), null, "关联项目文档");
        return toDocumentRefVO(reference, fixedVersion, currentVersion);
    }

    @Transactional
    public AigcProjectDocumentRefVO updateExternalDocumentReference(
            Long projectId, Long refId, AigcProjectDocumentRefUpdateDTO request) {
        var project = requireLockedProject(projectId, request.expectedProjectVersion());
        requireWritable(project);
        var reference =
                documentRefRepository
                        .findById(refId)
                        .filter(candidate -> projectId.equals(candidate.getProjectId()))
                        .orElseThrow(() -> notFound("项目文档引用不存在"));
        requireCanonicalDocumentReference(reference);
        if (reference.getReferenceType() != AigcProjectDocumentReferenceType.EXTERNAL_REFERENCE) {
            throw badRequest("仅外部文档引用可更新到当前版本");
        }
        var fixedVersion =
                documentVersionApi.requireAccessible(
                        reference.getSelectedDocumentVersionId(),
                        project.getOwnerId(),
                        project.getOrgId(),
                        project.getWorkspaceId());
        var currentVersion =
                requireCurrentDocumentVersions(project, List.of(fixedVersion.documentId()))
                        .get(fixedVersion.documentId());
        if (fixedVersion.id().equals(currentVersion.id())) {
            return toDocumentRefVO(reference, fixedVersion, currentVersion);
        }
        documentRefRepository
                .findByProjectIdAndReferenceTypeAndSelectedDocumentVersionIdAndRole(
                        projectId,
                        AigcProjectDocumentReferenceType.EXTERNAL_REFERENCE,
                        currentVersion.id(),
                        reference.getRole())
                .filter(duplicate -> !duplicate.getId().equals(reference.getId()))
                .ifPresent(
                        duplicate -> {
                            throw badRequest("项目已引用该文档的当前版本");
                        });
        reference.setSelectedDocumentVersionId(currentVersion.id());
        documentRefRepository.save(reference);
        bumpRevision(project, List.of(), List.of(), null, "更新外部文档引用版本");
        return toDocumentRefVO(reference, currentVersion, currentVersion);
    }

    @Transactional
    public AigcProjectDocumentDecisionVO recordProjectDocumentCandidate(
            Long projectId, Long refId, AigcProjectDocumentCandidateDTO request) {
        var idempotencyKey = requireCommandText(request.idempotencyKey(), "candidate 幂等键不能为空");
        var requestHash = projectDocumentCandidateRequestHash(projectId, refId, request);
        var project = requireLockedProject(projectId, null, false);
        var reference = requireProjectDocumentReference(projectId, refId);
        var replay =
                documentDecisionRepository.findByProjectDocumentRefIdAndCandidateIdempotencyKey(
                        refId, idempotencyKey);
        if (replay.isPresent()) {
            if (!Objects.equals(replay.get().getCandidateRequestHash(), requestHash)) {
                throw new BusinessException(409, "同一项目文档 candidate 幂等键对应不同正文");
            }
            return toProjectDocumentDecisionVO(project, replay.get());
        }
        requireExpectedVersion(project, request.expectedProjectVersion());
        requireWritable(project);
        var currentVersion =
                requireCurrentDocumentVersions(project, List.of(reference.getDocumentId()))
                        .get(reference.getDocumentId());
        if (!Objects.equals(currentVersion.id(), request.expectedDocumentCurrentVersionId())) {
            throw new BusinessException(409, "文档当前版本已变化，请刷新后重试");
        }
        var sourceType =
                operatorContext.currentOperatorType() == OperatorType.AI ? "AIGC" : "MANUAL";
        var sourceRef = "aigc-project:%d:document-ref:%d".formatted(projectId, refId);
        var candidateVersion =
                documentVersionApi.append(
                        new DocumentVersionApi.AppendDocumentVersionCommand(
                                reference.getDocumentId(),
                                request.expectedDocumentCurrentVersionId(),
                                request.title(),
                                request.content(),
                                request.format(),
                                sourceType,
                                sourceRef,
                                request.changeSummary(),
                                project.getOwnerId(),
                                project.getOrgId(),
                                project.getWorkspaceId()));
        if (Objects.equals(candidateVersion.id(), reference.getSelectedDocumentVersionId())) {
            throw badRequest("项目文档 candidate 必须形成新的正文版本");
        }
        documentDecisionRepository
                .findByProjectDocumentRefIdAndDocumentVersionId(refId, candidateVersion.id())
                .ifPresent(
                        existing -> {
                            throw new BusinessException(409, "该文档版本已登记为项目 candidate");
                        });
        var decision = new AigcProjectDocumentDecision();
        copyScope(project, decision);
        decision.setProjectId(projectId);
        decision.setProjectDocumentRefId(refId);
        decision.setDocumentVersionId(candidateVersion.id());
        decision.setSourceType(sourceType);
        decision.setSourceRef(sourceRef);
        decision.setCandidateIdempotencyKey(idempotencyKey);
        decision.setCandidateRequestHash(requestHash);
        documentDecisionRepository.save(decision);
        project.setStatus(AigcProjectLifecycle.ADOPTING);
        bumpRevision(project, List.of(), List.of(), null, "登记项目文档 candidate");
        return toProjectDocumentDecisionVO(project, decision, candidateVersion);
    }

    public List<AigcProjectDocumentDecisionVO> projectDocumentCandidates(
            Long projectId, Long refId) {
        var project = requireEntity(projectId);
        requireProjectDocumentReference(projectId, refId);
        return documentDecisionRepository.findByProjectDocumentRefIdOrderByIdDesc(refId).stream()
                .map(decision -> toProjectDocumentDecisionVO(project, decision))
                .toList();
    }

    @Transactional
    public AigcProjectDocumentDecisionVO adoptProjectDocumentCandidate(
            Long projectId, Long refId, Long decisionId, AigcProjectDocumentDecisionDTO request) {
        var idempotencyKey = requireCommandText(request.idempotencyKey(), "采用幂等键不能为空");
        var requestHash =
                projectDocumentDecisionRequestHash(
                        "project.document-candidate.adopt", projectId, refId, decisionId, request);
        var project = requireLockedProject(projectId, null, false);
        var reference = requireProjectDocumentReference(projectId, refId);
        var decision = requireProjectDocumentDecision(refId, decisionId);
        requireDecisionIdempotency(refId, decision, idempotencyKey);
        if ("ADOPTED".equals(decision.getDecision())) {
            requireProjectDocumentDecisionReplay(decision, idempotencyKey, requestHash);
            return toProjectDocumentDecisionVO(project, decision);
        }
        requireExpectedVersion(project, request.expectedProjectVersion());
        requireWritable(project);
        if (!Objects.equals(
                reference.getSelectedDocumentVersionId(),
                request.expectedSelectedDocumentVersionId())) {
            throw new BusinessException(409, "项目文档选定版本已变化，请刷新后重试");
        }
        if (!"PENDING".equals(decision.getDecision())) {
            throw badRequest("只有待决项目文档 candidate 可以采用");
        }
        var candidateVersion =
                documentVersionApi.requireAccessible(
                        decision.getDocumentVersionId(),
                        project.getOwnerId(),
                        project.getOrgId(),
                        project.getWorkspaceId());
        if (!Objects.equals(candidateVersion.documentId(), reference.getDocumentId())) {
            throw badRequest("项目文档 candidate 与引用的文档身份不一致");
        }
        if (!request.confirmedReplacement()) {
            throw badRequest("替换项目文档选定版本必须显式确认");
        }
        var reason = requireCommandText(request.reason(), "替换项目文档选定版本必须填写原因");
        var replacedVersionId = reference.getSelectedDocumentVersionId();
        reference.setSelectedDocumentVersionId(candidateVersion.id());
        documentRefRepository.save(reference);
        resolveProjectDocumentDecision(
                decision,
                "ADOPTED",
                projectDocumentDecisionMode(),
                reason,
                replacedVersionId,
                idempotencyKey,
                requestHash);
        documentDecisionRepository.save(decision);
        bumpRevision(project, List.of(), List.of(), null, "采用项目文档 candidate");
        return toProjectDocumentDecisionVO(project, decision, candidateVersion);
    }

    @Transactional
    public AigcProjectDocumentDecisionVO rejectProjectDocumentCandidate(
            Long projectId, Long refId, Long decisionId, AigcProjectDocumentDecisionDTO request) {
        var idempotencyKey = requireCommandText(request.idempotencyKey(), "否决幂等键不能为空");
        var reason = requireCommandText(request.reason(), "否决原因不能为空");
        var requestHash =
                projectDocumentDecisionRequestHash(
                        "project.document-candidate.reject", projectId, refId, decisionId, request);
        var project = requireLockedProject(projectId, null, false);
        var reference = requireProjectDocumentReference(projectId, refId);
        var decision = requireProjectDocumentDecision(refId, decisionId);
        requireDecisionIdempotency(refId, decision, idempotencyKey);
        if ("REJECTED".equals(decision.getDecision())) {
            requireProjectDocumentDecisionReplay(decision, idempotencyKey, requestHash);
            return toProjectDocumentDecisionVO(project, decision);
        }
        requireExpectedVersion(project, request.expectedProjectVersion());
        requireWritable(project);
        if (!Objects.equals(
                reference.getSelectedDocumentVersionId(),
                request.expectedSelectedDocumentVersionId())) {
            throw new BusinessException(409, "项目文档选定版本已变化，请刷新后重试");
        }
        if (!"PENDING".equals(decision.getDecision())) {
            throw badRequest("只有待决项目文档 candidate 可以否决");
        }
        resolveProjectDocumentDecision(
                decision,
                "REJECTED",
                projectDocumentDecisionMode(),
                reason,
                null,
                idempotencyKey,
                requestHash);
        documentDecisionRepository.save(decision);
        bumpRevision(project, List.of(), List.of(), null, "否决项目文档 candidate");
        return toProjectDocumentDecisionVO(project, decision);
    }

    @Transactional
    public void detachDocument(Long projectId, Long refId, Integer expectedProjectVersion) {
        var project = requireLockedProject(projectId, expectedProjectVersion);
        requireWritable(project);
        var reference =
                documentRefRepository
                        .findById(refId)
                        .filter(candidate -> projectId.equals(candidate.getProjectId()))
                        .orElseThrow(() -> notFound("项目文档引用不存在"));
        documentRefRepository.delete(reference);
        bumpRevision(project, List.of(), List.of(), null, "解除项目文档引用");
    }

    public List<AigcProjectDocumentRefVO> documentRefs(Long projectId) {
        var project = requireEntity(projectId);
        var references = documentRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        if (references.isEmpty()) {
            return List.of();
        }
        references.forEach(this::requireCanonicalDocumentReference);
        var externalVersionIds =
                references.stream()
                        .filter(
                                reference ->
                                        reference.getReferenceType()
                                                == AigcProjectDocumentReferenceType
                                                        .EXTERNAL_REFERENCE)
                        .map(AigcProjectDocumentRef::getSelectedDocumentVersionId)
                        .toList();
        var fixedVersions =
                documentVersionApi.requireAccessible(
                        externalVersionIds,
                        project.getOwnerId(),
                        project.getOrgId(),
                        project.getWorkspaceId());
        var fixedVersionsById =
                fixedVersions.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        DocumentVersionApi.DocumentVersionView::id,
                                        version -> version));
        var documentIds = new LinkedHashSet<Long>();
        references.stream()
                .filter(
                        reference ->
                                reference.getReferenceType()
                                        == AigcProjectDocumentReferenceType.PROJECT_DOCUMENT)
                .map(AigcProjectDocumentRef::getDocumentId)
                .forEach(documentIds::add);
        fixedVersions.stream()
                .map(DocumentVersionApi.DocumentVersionView::documentId)
                .forEach(documentIds::add);
        var currentVersionsByDocumentId = requireCurrentDocumentVersions(project, documentIds);
        return references.stream()
                .map(
                        reference -> {
                            var fixedVersion =
                                    fixedVersionsById.get(reference.getSelectedDocumentVersionId());
                            var documentId = reference.getDocumentId();
                            return toDocumentRefVO(
                                    reference,
                                    fixedVersion,
                                    currentVersionsByDocumentId.get(documentId));
                        })
                .toList();
    }

    private AigcProjectDocumentRef requireProjectDocumentReference(Long projectId, Long refId) {
        var reference =
                documentRefRepository
                        .findById(refId)
                        .filter(candidate -> projectId.equals(candidate.getProjectId()))
                        .orElseThrow(() -> notFound("项目文档引用不存在"));
        requireCanonicalDocumentReference(reference);
        if (reference.getReferenceType() != AigcProjectDocumentReferenceType.PROJECT_DOCUMENT) {
            throw badRequest("只有项目自产文档可以登记 candidate");
        }
        return reference;
    }

    private AigcProjectDocumentDecision requireProjectDocumentDecision(
            Long refId, Long decisionId) {
        return documentDecisionRepository
                .findById(decisionId)
                .filter(decision -> refId.equals(decision.getProjectDocumentRefId()))
                .orElseThrow(() -> notFound("项目文档 candidate 不存在"));
    }

    private void requireDecisionIdempotency(
            Long refId, AigcProjectDocumentDecision decision, String idempotencyKey) {
        documentDecisionRepository
                .findByProjectDocumentRefIdAndDecisionIdempotencyKey(refId, idempotencyKey)
                .filter(existing -> !existing.getId().equals(decision.getId()))
                .ifPresent(
                        existing -> {
                            throw new BusinessException(409, "项目文档决策幂等键已被使用");
                        });
    }

    private void requireProjectDocumentDecisionReplay(
            AigcProjectDocumentDecision decision, String idempotencyKey, String requestHash) {
        if (!Objects.equals(decision.getDecisionIdempotencyKey(), idempotencyKey)
                || !Objects.equals(decision.getDecisionRequestHash(), requestHash)) {
            throw new BusinessException(409, "项目文档决策重放与原请求不一致");
        }
    }

    private void resolveProjectDocumentDecision(
            AigcProjectDocumentDecision decision,
            String result,
            String mode,
            String reason,
            Long replacedDocumentVersionId,
            String idempotencyKey,
            String requestHash) {
        decision.setDecision(result);
        decision.setDecisionMode(mode);
        decision.setDecisionReason(reason);
        decision.setReplacedDocumentVersionId(replacedDocumentVersionId);
        decision.setDecisionIdempotencyKey(idempotencyKey);
        decision.setDecisionRequestHash(requestHash);
        decision.setDecidedBy(operatorContext.currentOperatorId().orElse(null));
        decision.setDecidedByType(operatorContext.currentOperatorType().name());
        decision.setDecidedTime(LocalDateTime.now());
        decision.setVersion(decision.getVersion() == null ? 1 : decision.getVersion() + 1);
    }

    private String projectDocumentCandidateRequestHash(
            Long projectId, Long refId, AigcProjectDocumentCandidateDTO request) {
        var business = new LinkedHashMap<String, Object>();
        business.put("projectId", projectId);
        business.put("projectDocumentRefId", refId);
        business.put("title", request.title());
        business.put("content", request.content());
        business.put("format", request.format());
        business.put("changeSummary", request.changeSummary());
        var cas = new LinkedHashMap<String, Object>();
        cas.put("expectedDocumentCurrentVersionId", request.expectedDocumentCurrentVersionId());
        cas.put("expectedProjectVersion", request.expectedProjectVersion());
        return AigcCanonicalRequest.of("project.document-candidate.record", business, cas).sha256();
    }

    private String projectDocumentDecisionRequestHash(
            String operation,
            Long projectId,
            Long refId,
            Long decisionId,
            AigcProjectDocumentDecisionDTO request) {
        var business = new LinkedHashMap<String, Object>();
        business.put("projectId", projectId);
        business.put("projectDocumentRefId", refId);
        business.put("decisionId", decisionId);
        business.put("confirmedReplacement", request.confirmedReplacement());
        business.put("reason", request.reason());
        var cas = new LinkedHashMap<String, Object>();
        cas.put("expectedProjectVersion", request.expectedProjectVersion());
        cas.put("expectedSelectedDocumentVersionId", request.expectedSelectedDocumentVersionId());
        return AigcCanonicalRequest.of(operation, business, cas).sha256();
    }

    private AigcProjectDocumentDecisionVO toProjectDocumentDecisionVO(
            AigcProject project, AigcProjectDocumentDecision decision) {
        var documentVersion =
                documentVersionApi.requireAccessible(
                        decision.getDocumentVersionId(),
                        project.getOwnerId(),
                        project.getOrgId(),
                        project.getWorkspaceId());
        return toProjectDocumentDecisionVO(project, decision, documentVersion);
    }

    private AigcProjectDocumentDecisionVO toProjectDocumentDecisionVO(
            AigcProject project,
            AigcProjectDocumentDecision decision,
            DocumentVersionApi.DocumentVersionView documentVersion) {
        if (!Objects.equals(decision.getProjectId(), project.getId())) {
            throw badRequest("项目文档 candidate 与项目身份不一致");
        }
        return new AigcProjectDocumentDecisionVO(
                decision.getId(),
                decision.getVersion(),
                decision.getProjectDocumentRefId(),
                decision.getDocumentVersionId(),
                documentVersion.versionNo(),
                documentVersion.title(),
                decision.getDecision(),
                decision.getSourceType(),
                decision.getSourceRef(),
                decision.getDecisionMode(),
                decision.getDecisionReason(),
                decision.getDecidedBy(),
                decision.getDecidedByType(),
                decision.getDecidedTime(),
                decision.getReplacedDocumentVersionId(),
                decision.getCreateTime());
    }

    private String projectDocumentDecisionMode() {
        return operatorContext.currentOperatorType() == OperatorType.AI
                ? "AI_AUTHORIZED"
                : "MANUAL";
    }

    private String requireCommandText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private void requireDocumentReferenceIdentity(AigcProjectDocumentRefDTO request) {
        if (request.referenceType() == null) {
            throw badRequest("项目文档引用类型不能为空");
        }
        var valid =
                switch (request.referenceType()) {
                    case PROJECT_DOCUMENT ->
                            request.documentId() != null && request.documentVersionId() == null;
                    case EXTERNAL_REFERENCE ->
                            request.documentId() == null && request.documentVersionId() != null;
                };
        if (!valid) {
            throw badRequest("项目文档引用类型与身份字段不匹配");
        }
    }

    private void requireCanonicalDocumentReference(AigcProjectDocumentRef reference) {
        if (reference.getReferenceType() == null) {
            throw badRequest("项目文档引用类型不能为空");
        }
        var valid =
                switch (reference.getReferenceType()) {
                    case PROJECT_DOCUMENT ->
                            reference.getDocumentId() != null
                                    && reference.getSelectedDocumentVersionId() != null;
                    case EXTERNAL_REFERENCE ->
                            reference.getDocumentId() != null
                                    && reference.getSelectedDocumentVersionId() != null;
                };
        if (!valid) {
            throw badRequest("项目文档引用身份无效");
        }
    }

    private Map<Long, DocumentVersionApi.DocumentVersionView> requireCurrentDocumentVersions(
            AigcProject project, Collection<Long> documentIds) {
        var ids = new LinkedHashSet<>(documentIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            return Map.of();
        }
        documentReferenceApi.requireAccessible(
                ids, project.getOwnerId(), project.getOrgId(), project.getWorkspaceId());
        var currentVersions = documentVersionApi.findCurrent(ids);
        if (currentVersions.size() != ids.size()) {
            throw notFound("项目引用的文档当前版本不存在");
        }
        return currentVersions;
    }

    public List<AigcProjectResourceRefVO> resourceRefs(Long projectId) {
        requireEntity(projectId);
        return resourceRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                .map(
                        reference ->
                                new AigcProjectResourceRefVO(
                                        reference.getId(),
                                        reference.getResourceType(),
                                        reference.getResourceId(),
                                        reference.getResourceVersion(),
                                        reference.getRole(),
                                        reference.getSortOrder()))
                .toList();
    }

    @Transactional
    public void registerExternalResource(
            Long projectId, String resourceType, String resourceId, String role) {
        var project = requireEntity(projectId);
        if (AigcProjectLifecycle.ARCHIVED.equals(project.getStatus())) {
            return;
        }
        var existing =
                resourceRefRepository.findByProjectIdAndResourceTypeAndResourceId(
                        projectId, resourceType, resourceId);
        if (existing.isPresent()) {
            var reference = existing.get();
            if (!Objects.equals(reference.getRole(), role)) {
                reference.setRole(role);
                resourceRefRepository.save(reference);
            }
            return;
        }
        var reference = new AigcProjectResourceRef();
        copyScope(project, reference);
        reference.setProjectId(projectId);
        reference.setResourceType(resourceType);
        reference.setResourceId(resourceId);
        reference.setRole(role);
        resourceRefRepository.save(reference);
    }

    public List<AigcProjectMediaRefVO> mediaRefs(Long projectId) {
        requireEntity(projectId);
        return mediaRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                .map(this::toMediaRefVO)
                .toList();
    }

    private AigcProject requireLockedProject(Long projectId, Integer expectedVersion) {
        return requireLockedProject(projectId, expectedVersion, true);
    }

    private AigcProject requireLockedProject(
            Long projectId, Integer expectedVersion, boolean requireVersion) {
        requireEntity(projectId, CrudOperation.UPDATE, AccessMode.DEFAULT);
        var project = repository.findLockedById(projectId).orElseThrow(() -> notFound("项目不存在"));
        entityManager.refresh(project, LockModeType.PESSIMISTIC_WRITE);
        if (requireVersion) {
            requireExpectedVersion(project, expectedVersion);
        }
        return project;
    }

    private AigcProjectObject requireObject(Long projectId, Long objectId, boolean locked) {
        var object =
                locked
                        ? objectRepository.findLockedById(objectId)
                        : objectRepository.findById(objectId);
        return object.filter(candidate -> projectId.equals(candidate.getProjectId()))
                .orElseThrow(() -> notFound("项目对象不存在"));
    }

    private AigcObjectVersion requireVersion(Long projectId, Long objectId, Long versionId) {
        return versionRepository
                .findById(versionId)
                .filter(
                        version ->
                                projectId.equals(version.getProjectId())
                                        && objectId.equals(version.getObjectId()))
                .orElseThrow(() -> notFound("对象版本不存在"));
    }

    private void requireGraphRevision(AigcProject project, Long expectedGraphRevision) {
        if (expectedGraphRevision == null
                || !expectedGraphRevision.equals(project.getGraphRevision().longValue())) {
            throw new BusinessException(409, "项目图谱已被其他操作更新，请刷新后重试");
        }
    }

    private String requireSemanticValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw badRequest(field + " 不能为空");
        }
        return value.trim().toUpperCase();
    }

    private String requireSemanticRole(String value) {
        var role = requireSemanticValue(value, "semanticRole");
        if (!Set.of("STRUCTURE", "WORKING_ARTIFACT", "DELIVERABLE", "EVIDENCE", "REFERENCE")
                .contains(role)) {
            throw badRequest("不支持的 semanticRole: " + value);
        }
        return role;
    }

    private String requireOutputContract(String value) {
        var contract = requireSemanticValue(value, "outputContract");
        if (!Set.of(
                        "INLINE_OBJECT_VERSION",
                        "DOCUMENT_VERSION",
                        "MEDIA_VERSION",
                        "COMPOSITE_MANIFEST",
                        "REFERENCE_ONLY")
                .contains(contract)) {
            throw badRequest("不支持的 outputContract: " + value);
        }
        return contract;
    }

    private Set<Long> moveAffectedObjectIds(
            Long projectId, AigcProjectObject object, AigcProjectObject newParent) {
        var objects = objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        var subtree = new LinkedHashSet<Long>();
        subtree.add(object.getId());
        var expanded = true;
        while (expanded) {
            expanded = false;
            for (var candidate : objects) {
                if (candidate.getParentId() != null
                        && subtree.contains(candidate.getParentId())
                        && subtree.add(candidate.getId())) {
                    expanded = true;
                }
            }
        }
        if (object.getParentId() != null) {
            subtree.add(object.getParentId());
        }
        if (newParent != null) {
            subtree.add(newParent.getId());
        }
        return Set.copyOf(subtree);
    }

    private void validateObjectParent(
            AigcProject project,
            String blueprintTemplateKey,
            String objectType,
            AigcProjectObject parent) {
        if (blueprintTemplateKey != null) {
            var template = requireSlotTemplate(project, blueprintTemplateKey);
            var actualParent = parent == null ? null : parent.getBlueprintTemplateKey();
            if (!Objects.equals(template.parentTemplateKey(), actualParent)) {
                throw badRequest("对象父级与 Blueprint 槽位合同不匹配");
            }
            return;
        }
        if (parent == null) {
            throw badRequest("自定义对象必须指定所属 DeliverableSet");
        }
        var snapshot =
                snapshotRepository
                        .findFirstByProjectIdOrderByRevisionNoDesc(project.getId())
                        .orElseThrow(() -> badRequest("项目配置快照不存在"));
        var allowed =
                snapshot.getSnapshot().deliverableSets().stream()
                        .filter(
                                set ->
                                        Objects.equals(
                                                parent.getBlueprintTemplateKey(),
                                                set.setTemplateKey()))
                        .anyMatch(set -> set.allowedCustomObjectTypes().contains(objectType));
        if (!allowed) {
            throw badRequest("对象类型不在父级 DeliverableSet 白名单内");
        }
    }

    private void requireExpectedVersion(AigcProject project, Integer expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(project.getVersion())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目已被其他操作更新，请刷新后重试");
        }
    }

    private void requireWritable(AigcProject project) {
        if (!MUTABLE_STATUSES.contains(project.getStatus())) {
            throw badRequest("当前项目阶段只读: " + project.getStatus());
        }
    }

    private record CoverState(AigcProjectCoverStatus status, Long runId) {}

    private CoverState coverState(AigcProject project) {
        var status =
                project.getCoverStatus() == null
                        ? (project.getCoverMediaVersionId() == null
                                ? AigcProjectCoverStatus.NONE
                                : AigcProjectCoverStatus.READY)
                        : project.getCoverStatus();
        return new CoverState(status, project.getCoverExecutionRunId());
    }

    private void publishProjectLifecycle(AigcProject project) {
        activityEventService.publish(
                project.getUserId(),
                "project.lifecycle.changed",
                project.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("lifecycle", project.getStatus().name()));
    }

    private void requireDisplayInfoWritable(AigcProject project) {
        if (!MUTABLE_STATUSES.contains(project.getStatus())) {
            throw badRequest("当前项目阶段基础信息只读: " + project.getStatus());
        }
    }

    private AigcProjectRevision bumpStructuralRevision(
            AigcProject project,
            List<Long> changedObjectIds,
            List<Long> changedRelationIds,
            String summary) {
        return bumpRevision(project, changedObjectIds, changedRelationIds, null, summary, true);
    }

    private AigcProjectRevision bumpRevision(
            AigcProject project,
            List<Long> changedObjectIds,
            List<Long> changedRelationIds,
            Long executionRunId,
            String summary) {
        return bumpRevision(
                project, changedObjectIds, changedRelationIds, executionRunId, summary, false);
    }

    private AigcProjectRevision bumpRevision(
            AigcProject project,
            List<Long> changedObjectIds,
            List<Long> changedRelationIds,
            Long executionRunId,
            String summary,
            boolean structuralChange) {
        project.setGraphRevision(project.getGraphRevision() + 1);
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
        repository.save(project);
        if (structuralChange) {
            persistStructuralSnapshot(project);
        }
        activityEventService.publish(
                project.getUserId(),
                "project.changed",
                project.getId(),
                executionRunId,
                null,
                null,
                null,
                null,
                null,
                Map.of(
                        "lifecycle", project.getStatus().name(),
                        "graphRevision", project.getGraphRevision(),
                        "summary", summary));
        var revision = new AigcProjectRevision();
        revision.setProjectId(project.getId());
        revision.setConfigSnapshotId(project.getConfigSnapshotId());
        revision.setRevisionNo(project.getGraphRevision());
        revision.setProjectVersion(project.getVersion());
        revision.setGraphRevision(project.getGraphRevision());
        revision.setChangedObjectIds(List.copyOf(changedObjectIds));
        revision.setChangedRelationIds(List.copyOf(changedRelationIds));
        revision.setActorType("HUMAN");
        revision.setActorId(operatorContext.currentOwnerId().orElse(null));
        revision.setSourceExecutionRunId(executionRunId);
        revision.setSummary(summary);
        return revisionRepository.save(revision);
    }

    private void persistStructuralSnapshot(AigcProject project) {
        var previous =
                snapshotRepository
                        .findById(project.getConfigSnapshotId())
                        .orElseThrow(() -> notFound("项目配置快照不存在"));
        var objects = objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(project.getId());
        var objectsById =
                objects.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectObject::getId,
                                        java.util.function.Function.identity()));
        var previousByStableKey =
                previous.getSnapshot().objectSpecs().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcResolvedObjectSpec::stableKey,
                                        java.util.function.Function.identity()));
        var objectSpecs =
                objects.stream()
                        .map(
                                object -> {
                                    var previousSpec =
                                            previousByStableKey.get(object.getStableKey());
                                    return new AigcResolvedObjectSpec(
                                            object.getStableKey(),
                                            object.getBlueprintTemplateKey(),
                                            object.getInstanceNo(),
                                            object.getObjectType(),
                                            object.getTitle(),
                                            object.getParentId() == null
                                                    ? null
                                                    : objectsById
                                                            .get(object.getParentId())
                                                            .getStableKey(),
                                            object.getSortOrder(),
                                            object.getContractRole(),
                                            object.getSemanticRole(),
                                            object.getOutputContract(),
                                            previousSpec == null
                                                    ? "SINGLE_ADOPTED"
                                                    : previousSpec.adoptionPolicy(),
                                            previousSpec == null
                                                    ? object.getDefaultActionKey()
                                                    : previousSpec.defaultActionKey(),
                                            previousSpec == null
                                                    ? null
                                                    : previousSpec.userInstructionJson(),
                                            previousSpec == null
                                                    ? JsonUtils.toJsonString(object.getPayload())
                                                    : previousSpec.initialPayloadJson(),
                                            previousSpec == null
                                                    ? null
                                                    : previousSpec.payloadSchemaJson());
                                })
                        .toList();
        var relationSpecs =
                relationRepository.findByProjectIdOrderByIdAsc(project.getId()).stream()
                        .filter(relation -> !"contains".equals(relation.getRelationType()))
                        .map(
                                relation ->
                                        new AigcBlueprintRelationSpec(
                                                objectsById
                                                        .get(relation.getSourceObjectId())
                                                        .getStableKey(),
                                                objectsById
                                                        .get(relation.getTargetObjectId())
                                                        .getStableKey(),
                                                relation.getRelationType(),
                                                JsonUtils.toJsonString(
                                                        relation.getRelationMeta() == null
                                                                ? Map.of()
                                                                : relation.getRelationMeta())))
                        .toList();
        var payload = previous.getSnapshot().withGraph(objectSpecs, relationSpecs);

        var snapshot = new AigcProjectConfigSnapshot();
        copyScope(project, snapshot);
        snapshot.setProjectId(project.getId());
        snapshot.setRevisionNo(project.getGraphRevision());
        snapshot.setSourceType(previous.getSourceType());
        snapshot.setSourceRef(previous.getSourceRef());
        snapshot.setSnapshotHash(
                AigcCanonicalRequest.canonicalSha256(parseMap(JsonUtils.toJsonString(payload))));
        snapshot.setProjectTypeVersion(previous.getProjectTypeVersion());
        snapshot.setBlueprintVersion(previous.getBlueprintVersion());
        snapshot.setChannelVersions(previous.getChannelVersions());
        snapshot.setExecutionBindingVersions(previous.getExecutionBindingVersions());
        snapshot.setCompatibilityResult(previous.getCompatibilityResult());
        snapshot.setSnapshot(payload);
        snapshotRepository.saveAndFlush(snapshot);
        project.setConfigSnapshotId(snapshot.getId());
        repository.save(project);
    }

    private AigcProjectView toApiView(AigcProject project) {
        var channelIds =
                channelRefRepository.findByProjectIdOrderByIdAsc(project.getId()).stream()
                        .map(reference -> reference.getChannelSpecId())
                        .toList();
        var executionBindings =
                snapshotRepository
                        .findFirstByProjectIdOrderByRevisionNoDesc(project.getId())
                        .map(AigcProjectConfigSnapshot::getExecutionBindingVersions)
                        .orElse(List.of());
        return new AigcProjectView(
                project.getId(),
                project.getOrgId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getStatus(),
                project.getVersion(),
                project.getConfigSnapshotId(),
                snapshotRepository
                        .findById(project.getConfigSnapshotId())
                        .map(AigcProjectConfigSnapshot::getSnapshotHash)
                        .orElseThrow(() -> notFound("项目配置快照不存在")),
                project.getProjectTypeCode(),
                project.getProductionMode(),
                project.getGenerationMode(),
                channelIds,
                executionBindings,
                project.getBudgetLimit(),
                project.getCostUsed(),
                project.getDescription(),
                project.getBackground(),
                project.getTargetAudience(),
                project.getRequirements(),
                project.getConstraints(),
                project.getKeyFacts(),
                project.getKeyMessages(),
                project.getStyleGuidance(),
                project.getSuccessCriteria(),
                project.getDeliveryRequirements(),
                project.getLocale(),
                project.getAdditionalContext(),
                project.getCoverMediaVersionId(),
                project.getUserId());
    }

    private AigcProjectObjectView toApiObjectView(AigcProjectObject object) {
        return new AigcProjectObjectView(
                object.getId(),
                object.getProjectId(),
                object.getStableKey(),
                object.getBlueprintTemplateKey(),
                object.getInstanceNo(),
                object.getObjectType(),
                object.getTitle(),
                object.getContractRole(),
                object.getSemanticRole(),
                object.getOutputContract(),
                object.getDefaultActionKey(),
                object.getParentId(),
                object.getStatus(),
                object.getAdoptedVersionId());
    }

    private AigcProjectDocumentRefVO toDocumentRefVO(
            AigcProjectDocumentRef reference,
            DocumentVersionApi.DocumentVersionView fixedVersion,
            DocumentVersionApi.DocumentVersionView currentVersion) {
        requireCanonicalDocumentReference(reference);
        if (currentVersion == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "项目引用的文档当前版本不存在");
        }
        DocumentVersionApi.DocumentVersionView effectiveVersion;
        Long documentId;
        Long fixedDocumentVersionId;
        boolean outdated;
        switch (reference.getReferenceType()) {
            case PROJECT_DOCUMENT -> {
                if (!reference.getDocumentId().equals(currentVersion.documentId())) {
                    throw badRequest("项目文档引用与当前版本身份不匹配");
                }
                documentId = reference.getDocumentId();
                fixedDocumentVersionId = null;
                if (reference.getSelectedDocumentVersionId() == null) {
                    throw badRequest("项目文档引用缺少选定版本");
                }
                effectiveVersion =
                        documentVersionApi
                                .findExact(
                                        reference.getDocumentId(),
                                        reference.getSelectedDocumentVersionId())
                                .orElseThrow(
                                        () ->
                                                new BusinessException(
                                                        GlobalErrorCode.NOT_FOUND, "项目文档选定版本不存在"));
                outdated = !effectiveVersion.id().equals(currentVersion.id());
            }
            case EXTERNAL_REFERENCE -> {
                if (fixedVersion == null
                        || !reference.getSelectedDocumentVersionId().equals(fixedVersion.id())
                        || !reference.getDocumentId().equals(fixedVersion.documentId())
                        || !fixedVersion.documentId().equals(currentVersion.documentId())) {
                    throw badRequest("外部文档引用版本身份不匹配");
                }
                documentId = reference.getDocumentId();
                fixedDocumentVersionId = reference.getSelectedDocumentVersionId();
                effectiveVersion = fixedVersion;
                outdated = !fixedVersion.id().equals(currentVersion.id());
            }
            default -> throw badRequest("不支持的项目文档引用类型");
        }
        var semanticMetadata = semanticMetadataApi.find(effectiveVersion.id()).orElse(null);
        return new AigcProjectDocumentRefVO(
                reference.getId(),
                reference.getObjectId(),
                reference.getReferenceType(),
                documentId,
                fixedDocumentVersionId,
                effectiveVersion.id(),
                currentVersion.id(),
                effectiveVersion.versionNo(),
                currentVersion.versionNo(),
                effectiveVersion.title(),
                reference.getFieldKey(),
                reference.getInstanceNo(),
                reference.getSelectedDocumentVersionId(),
                reference.getSource(),
                reference.getContextNote(),
                reference.getContextMode(),
                reference.getRole(),
                reference.getSortOrder(),
                outdated,
                semanticMetadata == null ? null : semanticMetadata.summary(),
                semanticMetadata == null ? List.of() : semanticMetadata.topics(),
                semanticMetadata == null ? null : semanticMetadata.language(),
                semanticMetadata == null ? null : semanticMetadata.generationStatus(),
                semanticMetadata != null && semanticMetadata.manualOverride(),
                semanticMetadata == null ? null : semanticMetadata.metadataVersion());
    }

    private AigcProjectMediaRefView toApiMediaRefView(AigcProjectMediaRef reference) {
        return new AigcProjectMediaRefView(
                reference.getId(),
                reference.getProjectId(),
                reference.getObjectId(),
                reference.getMediaVersionId(),
                reference.getRole(),
                reference.getSortOrder(),
                reference.getAdoptionStatus());
    }

    private AigcObjectVersionView toApiVersionView(AigcObjectVersion version) {
        return new AigcObjectVersionView(
                version.getId(),
                version.getObjectId(),
                version.getVersionNo(),
                version.getStatus(),
                version.getExecutionRunId());
    }

    private AigcProjectObjectVO toObjectVO(AigcProjectObject object) {
        return new AigcProjectObjectVO(
                object.getId(),
                object.getVersion(),
                object.getProjectId(),
                object.getObjectType(),
                object.getStableKey(),
                object.getBlueprintTemplateKey(),
                object.getInstanceNo(),
                object.getContractRole(),
                object.getSemanticRole(),
                object.getOutputContract(),
                object.getDefaultActionKey(),
                object.getParentId(),
                object.getSortOrder(),
                object.getTitle(),
                object.getStatus(),
                object.getSource(),
                object.getSchemaVersion(),
                object.getEntityResource(),
                object.getEntityId(),
                object.getAdoptedVersionId(),
                object.getSummary(),
                object.getPayload());
    }

    private AigcProjectRelationVO toRelationVO(
            GraphRelation relation, Map<Long, AigcProjectObject> objectsById) {
        var source = objectsById.get(relation.sourceObjectId());
        var target = objectsById.get(relation.targetObjectId());
        return new AigcProjectRelationVO(
                relation.id(),
                relation.projectId(),
                relation.relationType(),
                relation.layer(),
                relation.sourceObjectId(),
                relation.targetObjectId(),
                relation.metadata(),
                source == null ? null : source.getSemanticRole(),
                target == null ? null : target.getSemanticRole(),
                source == null ? null : source.getOutputContract(),
                target == null ? null : target.getOutputContract());
    }

    private List<GraphRelation> graphRelations(Long projectId) {
        var persisted =
                relationRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                        .filter(relation -> !"contains".equals(relation.getRelationType()))
                        .map(
                                relation ->
                                        new GraphRelation(
                                                relation.getId(),
                                                projectId,
                                                relation.getRelationType(),
                                                AigcProjectRelationSemantics.canonicalLayer(
                                                        relation.getRelationType()),
                                                relation.getSourceObjectId(),
                                                relation.getTargetObjectId(),
                                                relation.getRelationMeta() == null
                                                        ? Map.of()
                                                        : relation.getRelationMeta()))
                        .toList();
        var contains =
                objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                        .filter(object -> object.getParentId() != null)
                        .map(
                                object ->
                                        new GraphRelation(
                                                null,
                                                projectId,
                                                "contains",
                                                AigcProjectRelationSemantics.canonicalLayer(
                                                        "contains"),
                                                object.getParentId(),
                                                object.getId(),
                                                Map.of()))
                        .toList();
        return java.util.stream.Stream.concat(persisted.stream(), contains.stream()).toList();
    }

    private record GraphRelation(
            Long id,
            Long projectId,
            String relationType,
            String layer,
            Long sourceObjectId,
            Long targetObjectId,
            Map<String, Object> metadata) {}

    private AigcObjectVersionVO toVersionVO(AigcObjectVersion version) {
        var mediaIds =
                mediaRefRepository
                        .findByProjectIdOrderBySortOrderAscIdAsc(version.getProjectId())
                        .stream()
                        .filter(reference -> version.getId().equals(reference.getObjectVersionId()))
                        .map(AigcProjectMediaRef::getMediaVersionId)
                        .toList();
        return new AigcObjectVersionVO(
                version.getId(),
                version.getProjectId(),
                version.getObjectId(),
                version.getVersionNo(),
                version.getStatus(),
                version.getContentPayload(),
                version.getDocumentVersionId(),
                mediaIds,
                version.getExecutionRunId(),
                version.getSummary(),
                version.getAdoptedTime(),
                version.getSupersededByVersionId(),
                version.getCreateTime());
    }

    private AigcProjectMediaRefVO toMediaRefVO(AigcProjectMediaRef reference) {
        return new AigcProjectMediaRefVO(
                reference.getId(),
                reference.getObjectId(),
                reference.getObjectVersionId(),
                reference.getMediaVersionId(),
                reference.getRole(),
                reference.getSortOrder(),
                reference.getAdoptionStatus());
    }

    private AigcBlueprintSlotTemplateSpec requireSlotTemplate(
            AigcProject project, String templateKey) {
        var snapshot =
                snapshotRepository
                        .findFirstByProjectIdOrderByRevisionNoDesc(project.getId())
                        .orElseThrow(() -> notFound("项目配置快照不存在"));
        return snapshot.getSnapshot().slotTemplates().stream()
                .filter(template -> templateKey.equals(template.templateKey()))
                .findFirst()
                .orElseThrow(() -> badRequest("项目配置中不存在槽位模板: " + templateKey));
    }

    private String formatStableKey(String pattern, int instanceNo) {
        if (pattern == null || pattern.isBlank()) {
            throw badRequest("槽位模板 stableKeyPattern 不能为空");
        }
        if (pattern.contains("{instanceNo:02d}")) {
            return pattern.replace("{instanceNo:02d}", "%02d".formatted(instanceNo));
        }
        if (pattern.contains("{instanceNo}")) {
            return pattern.replace("{instanceNo}", String.valueOf(instanceNo));
        }
        if (pattern.contains("%")) {
            try {
                return pattern.formatted(instanceNo);
            } catch (IllegalFormatException error) {
                throw badRequest("槽位模板 stableKeyPattern 非法");
            }
        }
        if (instanceNo > 1) {
            throw badRequest("可重复槽位模板 stableKeyPattern 必须包含 instanceNo");
        }
        return pattern;
    }

    private String requireContractRole(String role) {
        if (!Set.of("REQUIRED", "OPTIONAL", "EXCLUDED").contains(role)) {
            throw badRequest("对象合同角色必须为 REQUIRED/OPTIONAL/EXCLUDED");
        }
        return role;
    }

    private List<Long> resolveReservationTargets(
            Long projectId, AigcProjectExecutionReservationCommand command) {
        var requested = command.requestedProjectObjectIds().stream().distinct().sorted().toList();
        if (command.commandObjectId() == null && requested.isEmpty()) {
            return List.of();
        }
        var targetIds =
                requested.isEmpty()
                        ? objectRepository
                                .findByProjectIdAndParentIdOrderBySortOrderAscIdAsc(
                                        projectId, command.commandObjectId())
                                .stream()
                                .filter(object -> !"EXCLUDED".equals(object.getContractRole()))
                                .map(AigcProjectObject::getId)
                                .toList()
                        : requested;
        if (targetIds.isEmpty()) {
            targetIds = List.of(command.commandObjectId());
        }
        for (var targetId : targetIds) {
            var object = requireObject(projectId, targetId, true);
            if ("EXCLUDED".equals(object.getContractRole())) {
                throw badRequest("不能冻结已排除的项目对象");
            }
        }
        return targetIds.stream().sorted().toList();
    }

    private String reservationRequestHash(AigcProjectExecutionReservationCommand command) {
        var business = new TreeMap<String, Object>();
        business.put("projectId", command.projectId());
        business.put("commandObjectId", command.commandObjectId());
        business.put("actionKey", command.actionKey());
        business.put(
                "requestedProjectObjectIds",
                command.requestedProjectObjectIds().stream().distinct().sorted().toList());
        var cas = new TreeMap<String, Object>();
        cas.put("expectedGraphRevision", command.expectedGraphRevision());
        return AigcCanonicalRequest.of("project.reserve-execution", business, cas).sha256();
    }

    private String lifecycleRequestHash(String action, AigcProjectLifecycleCommand command) {
        var business = new TreeMap<String, Object>();
        business.put("projectId", command.projectId());
        business.put("reason", command.reason());
        return AigcCanonicalRequest.of(
                        "project.lifecycle." + action,
                        business,
                        Map.of("expectedProjectVersion", command.expectedProjectVersion()))
                .sha256();
    }

    private void requireLifecycleCommand(
            AigcProjectLifecycleCommand command, boolean reasonRequired) {
        if (command == null
                || command.projectId() == null
                || command.expectedProjectVersion() == null
                || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()) {
            throw badRequest("生命周期命令缺少 projectId、expectedProjectVersion 或 idempotencyKey");
        }
        if (reasonRequired && (command.reason() == null || command.reason().isBlank())) {
            throw badRequest("归档原因不能为空");
        }
    }

    private void requireLifecycleReplay(
            AigcProject project, String idempotencyKey, String requestHash) {
        if (!Objects.equals(project.getLifecycleIdempotencyKey(), idempotencyKey)
                || !Objects.equals(project.getLifecycleRequestHash(), requestHash)) {
            throw new BusinessException(409, "生命周期幂等键或请求参数与原请求不一致");
        }
    }

    private void requireSameSubmission(
            AigcProjectExecutionReservation reservation, Long executionSubmissionId) {
        if (!Objects.equals(reservation.getExecutionSubmissionId(), executionSubmissionId)) {
            throw badRequest("执行 reservation 与 submission 不匹配");
        }
    }

    private AigcProjectExecutionReservationView toReservationView(
            AigcProjectExecutionReservation reservation) {
        var targetIds =
                reservationTargetRepository
                        .findByReservationIdOrderByIdAsc(reservation.getId())
                        .stream()
                        .map(AigcProjectExecutionReservationTarget::getProjectObjectId)
                        .toList();
        return new AigcProjectExecutionReservationView(
                reservation.getId(),
                reservation.getExecutionSubmissionId(),
                reservation.getProjectId(),
                reservation.getCommandObjectId(),
                reservation.getTargetGraphRevision(),
                targetIds,
                reservation.getStatus(),
                reservation.getRootExecutionRunId());
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

    private void requireCurrentScope(Long ownerId, Long orgId, Long workspaceId) {
        if (ownerId == null || orgId == null) {
            throw badRequest("文档查询范围不能为空");
        }
        var currentOwnerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "账号未登录"));
        var currentOrgId = OrgContext.getCurrentOrgId();
        if (currentOrgId == null) {
            throw badRequest("当前组织不能为空");
        }
        if (!Objects.equals(ownerId, currentOwnerId)
                || !Objects.equals(orgId, currentOrgId)
                || !Objects.equals(workspaceId, OrgContext.getCurrentWorkspaceId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "项目查询范围与当前上下文不一致");
        }
    }

    private void requireProjectScope(
            AigcProject project, Long ownerId, Long orgId, Long workspaceId) {
        var workspaceAccessible =
                workspaceId == null
                        ? project.getWorkspaceId() == null
                        : project.getWorkspaceId() == null
                                || Objects.equals(project.getWorkspaceId(), workspaceId);
        if (!Objects.equals(project.getOwnerId(), ownerId)
                || !Objects.equals(project.getOrgId(), orgId)
                || !workspaceAccessible) {
            throw notFound("项目不存在");
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(GlobalErrorCode.NOT_FOUND, message);
    }
}
