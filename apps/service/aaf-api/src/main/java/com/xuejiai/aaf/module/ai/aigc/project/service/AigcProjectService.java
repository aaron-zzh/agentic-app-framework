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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.AigcAuthorities;
import com.xuejiai.aaf.module.ai.aigc.AigcCanonicalRequest;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcUploadedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcApprovedManifestView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcCompletionEvaluationView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetCompletionView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetEvaluateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetManifestFreezeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionAdoptCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionComparisonView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionRejectCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverMode;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverStatus;
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
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectRemoveCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectRelationView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewDecisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewSubmitCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewView;
import com.xuejiai.aaf.module.ai.aigc.project.api.CompletionEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.project.api.ExecutionEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcObjectVersion;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectConfigSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDocumentRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectExecutionReservation;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectExecutionReservationTarget;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectMediaRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectObject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRelation;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectResourceRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRevision;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectArchivedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCoverGenerationRequestedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCoverSupersededEvent;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcObjectVersionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectChannelRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;
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
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefDTO;
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
import com.xuejiai.aaf.module.document.api.DocumentReferenceApi;

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
    private final AigcProjectResourceRefRepository resourceRefRepository;
    private final AigcProjectMediaRefRepository mediaRefRepository;
    private final AigcProjectMaterializer materializer;
    private final AigcProjectDeliveryService deliveryService;
    private final CompletionEvidencePort completionEvidencePort;
    private final ExecutionEvidencePort executionEvidencePort;
    private final AigcMediaApi mediaApi;
    private final DocumentReferenceApi documentReferenceApi;
    private final OperatorContext operatorContext;
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
                project.getProjectTypeCode(),
                project.getBlueprintCode(),
                project.getBlueprintVersion(),
                project.getDomainExtensionCode(),
                project.getDomainExtensionVersion(),
                project.getProductionMode(),
                project.getGenerationMode(),
                project.getStatus(),
                project.getBrief(),
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
        if (!request.brief().isAbsent()) {
            project.setBrief(request.brief().valueOrNull());
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
                && request.brief().isAbsent()
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
                        project.getBrief() == null ? "" : project.getBrief(),
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
                toApiView(refreshed), refreshed.getCoverStatus(), refreshed.getCoverExecutionRunId());
    }

    @Override
    public AigcProjectView requireProject(Long projectId) {
        return toApiView(requireEntity(projectId));
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
        return new LinkedHashSet<>(
                documentRefRepository.findLinkedDocumentIds(
                        ownerId, orgId, workspaceId, projectId));
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
        return documentRefRepository.findDocumentProjects(ownerId, orgId, workspaceId, ids).stream()
                .map(
                        row ->
                                new DocumentProjectReference(
                                        row.getDocumentId(),
                                        row.getProjectId(),
                                        row.getProjectName()))
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
        return new AigcProjectGraphView(
                toApiView(project),
                objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                        .map(this::toApiObjectView)
                        .toList(),
                relationRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                        .map(
                                relation ->
                                        new AigcProjectRelationView(
                                                relation.getId(),
                                                relation.getSourceObjectId(),
                                                relation.getTargetObjectId(),
                                                relation.getRelationType(),
                                                JsonUtils.toJsonString(relation.getRelationMeta())))
                        .toList(),
                project.getGraphRevision().longValue());
    }

    public AigcProjectGraphVO graph(Long projectId) {
        var project = requireEntity(projectId);
        return new AigcProjectGraphVO(
                toVO(project),
                project.getGraphRevision(),
                objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                        .map(this::toObjectVO)
                        .toList(),
                relationRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                        .map(this::toRelationVO)
                        .toList());
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
        var executionEvidence = executionEvidencePort.load(projectId);
        var completionEvidence = completionEvidencePort.load(projectId);
        var completionEvaluation = deliveryService.completion(projectId);
        return new AigcProjectSummaryVO(
                projectId,
                objects.size(),
                objects.stream()
                        .filter(object -> object.getObjectType().endsWith("_deliverable"))
                        .count(),
                objects.stream()
                        .filter(object -> "pending_confirm".equals(object.getStatus()))
                        .count(),
                objects.stream().filter(object -> "blocked".equals(object.getStatus())).count(),
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
        if (template != null && !Boolean.TRUE.equals(template.get("userAddable"))) {
            throw badRequest("该槽位模板不允许用户追加实例");
        }
        if (template != null
                && !command.objectType().equals(String.valueOf(template.get("objectType")))) {
            throw badRequest("对象类型与槽位模板不匹配");
        }
        if (template != null) {
            if (!(template.get("maxCount") instanceof Number maxCountValue)) {
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
            if (currentCount >= maxCountValue.intValue()) {
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
            var deliverableSets = snapshot.getSnapshot().get("deliverableSets");
            if (!(deliverableSets instanceof List<?> sets)) {
                throw badRequest("对象类型不在交付包允许的自定义对象白名单内");
            }
            var setTemplateKey = parentObject.getBlueprintTemplateKey();
            var allowedCustomObjectTypes =
                    sets.stream()
                            .filter(Map.class::isInstance)
                            .map(Map.class::cast)
                            .filter(
                                    set ->
                                            Objects.equals(
                                                    setTemplateKey, set.get("setTemplateKey")))
                            .map(set -> set.get("allowedCustomObjectTypes"))
                            .filter(List.class::isInstance)
                            .map(List.class::cast)
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
                        : formatStableKey(
                                String.valueOf(template.get("stableKeyPattern")), instanceNo);
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
        object.setObjectType(command.objectType());
        object.setSortOrder(command.orderNo() == null ? 0 : command.orderNo());
        object.setTitle(command.displayName().trim());
        object.setStatus("draft");
        object.setSource("user");
        object.setSchemaVersion(command.schemaVersion());
        object.setPayload(parseMap(command.payloadJson()));
        objectRepository.save(object);
        bumpRevision(project, List.of(object.getId()), List.of(), null, "追加项目对象");
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
    public AigcProjectObjectView updateObjectContract(AigcProjectObjectContractCommand command) {
        var project = requireLockedProject(command.projectId(), command.expectedProjectVersion());
        requireWritable(project);
        var object = requireObject(project.getId(), command.objectId(), true);
        var contractRole = requireContractRole(command.contractRole());
        if ("EXCLUDED".equals(contractRole)
                && reservationTargetRepository.existsActiveReservationByProjectObjectId(
                        object.getId())) {
            throw badRequest("对象正被执行占用，不能排除");
        }
        object.setContractRole(contractRole);
        if ("EXCLUDED".equals(contractRole)) {
            object.setStatus("EXCLUDED");
        }
        objectRepository.save(object);
        bumpRevision(project, List.of(object.getId()), List.of(), null, "更新对象合同");
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
            object.setStatus("EXCLUDED");
            objectRepository.save(object);
        } else {
            objectRepository.delete(object);
        }
        bumpRevision(
                project,
                List.of(object.getId()),
                List.of(),
                null,
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
                                        revision.getRevisionNo(),
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
                                        snapshot.getProjectTypeVersion(),
                                        snapshot.getBlueprintVersion(),
                                        snapshot.getDomainExtensionVersion(),
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
        if (request.objectId() != null) {
            requireObject(projectId, request.objectId(), false);
        }
        documentReferenceApi.requireAccessible(
                List.of(request.documentVersionId()),
                project.getOwnerId(),
                project.getOrgId(),
                project.getWorkspaceId());
        var role =
                request.role() == null || request.role().isBlank()
                        ? "project"
                        : request.role().trim();
        var existing =
                documentRefRepository.findByProjectIdAndDocumentVersionIdAndRole(
                        projectId, request.documentVersionId(), role);
        if (existing.isPresent()) {
            return toDocumentRefVO(existing.get());
        }

        var reference = new AigcProjectDocumentRef();
        copyScope(project, reference);
        reference.setProjectId(projectId);
        reference.setObjectId(request.objectId());
        reference.setDocumentVersionId(request.documentVersionId());
        reference.setRole(role);
        reference.setSortOrder(
                request.sortOrder() == null
                        ? documentRefRepository
                                .findByProjectIdOrderBySortOrderAscIdAsc(projectId)
                                .size()
                        : request.sortOrder());
        documentRefRepository.save(reference);
        bumpRevision(project, List.of(), List.of(), null, "关联项目文档");
        return toDocumentRefVO(reference);
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
        requireEntity(projectId);
        return documentRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                .map(this::toDocumentRefVO)
                .toList();
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

    private AigcProjectRevision bumpRevision(
            AigcProject project,
            List<Long> changedObjectIds,
            List<Long> changedRelationIds,
            Long executionRunId,
            String summary) {
        project.setGraphRevision(project.getGraphRevision() + 1);
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
        repository.save(project);
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
        revision.setRevisionNo(project.getGraphRevision());
        revision.setChangedObjectIds(List.copyOf(changedObjectIds));
        revision.setChangedRelationIds(List.copyOf(changedRelationIds));
        revision.setActorType("HUMAN");
        revision.setActorId(operatorContext.currentOwnerId().orElse(null));
        revision.setSourceExecutionRunId(executionRunId);
        revision.setSummary(summary);
        return revisionRepository.save(revision);
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
                project.getProjectTypeCode(),
                project.getDomainExtensionCode(),
                project.getProductionMode(),
                project.getGenerationMode(),
                channelIds,
                executionBindings,
                project.getBudgetLimit(),
                project.getCostUsed(),
                project.getDescription(),
                project.getBrief(),
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
                object.getPayload() == null || object.getPayload().get("defaultActionKey") == null
                        ? null
                        : String.valueOf(object.getPayload().get("defaultActionKey")),
                object.getParentId(),
                object.getStatus(),
                object.getAdoptedVersionId());
    }

    private AigcProjectDocumentRefVO toDocumentRefVO(AigcProjectDocumentRef reference) {
        return new AigcProjectDocumentRefVO(
                reference.getId(),
                reference.getObjectId(),
                reference.getDocumentVersionId(),
                reference.getRole(),
                reference.getSortOrder());
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

    private AigcProjectRelationVO toRelationVO(AigcProjectRelation relation) {
        return new AigcProjectRelationVO(
                relation.getId(),
                relation.getProjectId(),
                relation.getRelationType(),
                relation.getLayer(),
                relation.getSourceObjectId(),
                relation.getTargetObjectId(),
                relation.getRelationMeta());
    }

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

    private Map<String, Object> requireSlotTemplate(AigcProject project, String templateKey) {
        var snapshot =
                snapshotRepository
                        .findFirstByProjectIdOrderByRevisionNoDesc(project.getId())
                        .orElseThrow(() -> notFound("项目配置快照不存在"));
        var value = snapshot.getSnapshot().get("slotTemplates");
        if (!(value instanceof List<?> templates)) {
            throw badRequest("项目配置快照缺少槽位模板");
        }
        for (var candidate : templates) {
            if (!(candidate instanceof Map<?, ?> raw)
                    || !templateKey.equals(String.valueOf(raw.get("templateKey")))) {
                continue;
            }
            var result = new LinkedHashMap<String, Object>();
            raw.forEach((key, item) -> result.put(String.valueOf(key), item));
            return Map.copyOf(result);
        }
        throw badRequest("项目配置中不存在槽位模板: " + templateKey);
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
