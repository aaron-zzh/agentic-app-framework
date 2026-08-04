package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionAdoptCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGraphView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMaterializeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMediaRefCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMediaRefView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectRelationView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewApproveCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.CompletionEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.project.api.ExecutionEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcObjectVersion;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectMediaRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectObject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRevision;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcObjectVersionAdoptedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectArchivedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectReviewApprovedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcObjectVersionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectChannelRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectDocumentRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectMediaRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectObjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectProfileRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRelationRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectResourceRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRevisionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcObjectVersionVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectChannelRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectConfigSnapshotVO;
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

    private static final Set<String> MUTABLE_STATUSES = Set.of("draft", "in_progress");

    private final AigcProjectRepository repository;
    private final AigcProjectObjectRepository objectRepository;
    private final AigcProjectRelationRepository relationRepository;
    private final AigcObjectVersionRepository versionRepository;
    private final AigcProjectRevisionRepository revisionRepository;
    private final AigcProjectConfigSnapshotRepository snapshotRepository;
    private final AigcProjectProfileRefRepository profileRefRepository;
    private final AigcProjectChannelRefRepository channelRefRepository;
    private final AigcProjectDocumentRefRepository documentRefRepository;
    private final AigcProjectResourceRefRepository resourceRefRepository;
    private final AigcProjectMediaRefRepository mediaRefRepository;
    private final AigcProjectMaterializer materializer;
    private final CompletionEvidencePort completionEvidencePort;
    private final ExecutionEvidencePort executionEvidencePort;
    private final AigcMediaApi mediaApi;
    private final OperatorContext operatorContext;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    protected AigcProjectRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcProjectVO toVO(AigcProject project) {
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
        requireWritable(project);
        if (request.name() != null) project.setName(request.name());
        if (request.description() != null) project.setDescription(request.description());
        if (request.brief() != null) project.setBrief(request.brief());
        if (request.prompt() != null) project.setPrompt(request.prompt());
        if (request.coverMediaVersionId() != null) {
            mediaApi.getByVersionId(request.coverMediaVersionId(), project.getUserId());
            project.setCoverMediaVersionId(request.coverMediaVersionId());
        }
        if (request.assistantId() != null) project.setAssistantId(request.assistantId());
        if (request.budgetLimit() != null) project.setBudgetLimit(request.budgetLimit());
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
    }

    @Override
    protected void beforeDelete(AigcProject project) {
        throw new UnsupportedOperationException("项目不开放删除，请使用归档命令");
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
    @Transactional
    public AigcProjectView materialize(AigcProjectMaterializeCommand command) {
        return toApiView(materializer.materialize(command));
    }

    @Override
    public AigcProjectView requireProject(Long projectId) {
        return toApiView(requireEntity(projectId));
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
                completionEvidence.unpublishedPublicationCount(),
                completionEvidence.hasActiveWork() && completionEvidence.publicationsCompleted(),
                project.getCostUsed());
    }

    @Override
    @Transactional
    public AigcProjectObjectView appendObject(AigcProjectObjectCommand command) {
        var project = requireLockedProject(command.projectId(), command.expectedProjectVersion());
        requireWritable(project);
        if (objectRepository
                .findByProjectIdAndObjectKey(project.getId(), command.stableKey())
                .isPresent()) {
            throw badRequest("项目对象 stableKey 已存在");
        }
        if (command.parentObjectId() != null) {
            requireObject(project.getId(), command.parentObjectId(), false);
        }
        var object = new AigcProjectObject();
        copyScope(project, object);
        object.setProjectId(project.getId());
        object.setParentId(command.parentObjectId());
        object.setObjectKey(command.stableKey());
        object.setObjectType(command.objectType());
        object.setSortOrder(command.orderNo() == null ? 0 : command.orderNo());
        object.setStatus("draft");
        object.setSource("user");
        object.setSchemaVersion(command.schemaVersion());
        object.setPayload(parseMap(command.payloadJson()));
        objectRepository.save(object);
        bumpRevision(project, List.of(object.getId()), List.of(), null, "追加项目对象");
        return toApiObjectView(object);
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
    }

    @Override
    @Transactional
    public AigcObjectVersionView appendCandidate(AigcObjectVersionCandidateCommand command) {
        var existing = versionRepository.findByExecutionRunIdOrderByIdAsc(command.executionRunId());
        if (!existing.isEmpty()) {
            return toApiVersionView(existing.getFirst());
        }
        var project = requireEntity(command.projectId());
        requireWritable(project);
        var object = requireObject(project.getId(), command.objectId(), true);
        var version = new AigcObjectVersion();
        copyScope(project, version);
        version.setProjectId(project.getId());
        version.setObjectId(object.getId());
        version.setVersionNo(
                versionRepository
                                .findFirstByObjectIdOrderByVersionNoDesc(object.getId())
                                .map(AigcObjectVersion::getVersionNo)
                                .orElse(0)
                        + 1);
        version.setStatus("candidate");
        version.setContentPayload(parseMap(command.contentJson()));
        version.setDocumentVersionId(command.documentVersionId());
        version.setExecutionRunId(command.executionRunId());
        version.setSummary("执行候选");
        versionRepository.save(version);
        for (var mediaVersionId : command.mediaVersionIds()) {
            mediaApi.getByVersionId(mediaVersionId, project.getUserId());
            var reference = new AigcProjectMediaRef();
            copyScope(project, reference);
            reference.setProjectId(project.getId());
            reference.setObjectId(object.getId());
            reference.setObjectVersionId(version.getId());
            reference.setMediaVersionId(mediaVersionId);
            reference.setRole("candidate");
            reference.setAdoptionStatus("candidate");
            mediaRefRepository.save(reference);
        }
        return toApiVersionView(version);
    }

    @Override
    public AigcObjectVersionView requireObjectVersion(
            Long projectId, Long objectId, Long objectVersionId) {
        requireEntity(projectId);
        requireObject(projectId, objectId, false);
        return toApiVersionView(requireVersion(projectId, objectId, objectVersionId));
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
    public AigcObjectVersionView adoptVersion(AigcObjectVersionAdoptCommand command) {
        var project = requireLockedProject(command.projectId(), command.expectedProjectVersion());
        requireWritable(project);
        var object = requireObject(project.getId(), command.objectId(), true);
        var version = requireVersion(project.getId(), object.getId(), command.objectVersionId());
        if ("adopted".equals(version.getStatus())
                && version.getId().equals(object.getAdoptedVersionId())) {
            return toApiVersionView(version);
        }
        if (!"candidate".equals(version.getStatus())) {
            throw badRequest("只有候选版本可以采用");
        }
        versionRepository
                .findByObjectIdAndStatus(object.getId(), "adopted")
                .forEach(
                        previous -> {
                            previous.setStatus("superseded");
                            previous.setSupersededByVersionId(version.getId());
                            versionRepository.save(previous);
                        });
        version.setStatus("adopted");
        version.setAdoptedTime(LocalDateTime.now());
        version.setAdoptedBy(operatorContext.currentOwnerId().orElseThrow());
        versionRepository.save(version);
        object.setAdoptedVersionId(version.getId());
        object.setStatus("adopted");
        objectRepository.save(object);
        mediaRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(project.getId()).stream()
                .filter(reference -> version.getId().equals(reference.getObjectVersionId()))
                .forEach(
                        reference -> {
                            reference.setAdoptionStatus("adopted");
                            mediaRefRepository.save(reference);
                        });
        var revision =
                bumpRevision(
                        project,
                        List.of(object.getId()),
                        List.of(),
                        version.getExecutionRunId(),
                        command.reason() == null ? "采用对象版本" : command.reason());
        eventPublisher.publishEvent(
                new AigcObjectVersionAdoptedEvent(
                        UUID.randomUUID(),
                        project.getId(),
                        object.getId(),
                        version.getId(),
                        revision.getRevisionNo().longValue(),
                        Instant.now()));
        return toApiVersionView(version);
    }

    @Override
    @Transactional
    public AigcObjectVersionView rejectVersion(
            Long projectId, Long objectId, Long objectVersionId, Integer expectedProjectVersion) {
        var project = requireLockedProject(projectId, expectedProjectVersion);
        requireWritable(project);
        var version = requireVersion(projectId, objectId, objectVersionId);
        if (!"candidate".equals(version.getStatus()) && !"rejected".equals(version.getStatus())) {
            throw badRequest("只有候选版本可以否决");
        }
        version.setStatus("rejected");
        versionRepository.save(version);
        bumpRevision(project, List.of(objectId), List.of(), version.getExecutionRunId(), "否决对象版本");
        return toApiVersionView(version);
    }

    @Override
    @Transactional
    public AigcProjectView submitReview(Long projectId, Integer expectedVersion) {
        var project = requireLockedProject(projectId, expectedVersion);
        requireWritable(project);
        if (objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                .noneMatch(object -> object.getAdoptedVersionId() != null)) {
            throw badRequest("至少采用一个对象版本后才能提交审核");
        }
        project.setStatus("reviewing");
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
        repository.save(project);
        return toApiView(project);
    }

    @Override
    @Transactional
    public AigcProjectView approveReview(AigcReviewApproveCommand command) {
        var project = requireLockedProject(command.projectId(), command.expectedProjectVersion());
        if (!"reviewing".equals(project.getStatus())) {
            throw badRequest("只有审核中的项目可以通过审核");
        }
        var review = requireObject(project.getId(), command.reviewObjectId(), true);
        review.setStatus("done");
        review.setSummary(command.conclusion());
        objectRepository.save(review);
        var deliverables =
                objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(project.getId()).stream()
                        .filter(object -> object.getObjectType().endsWith("_deliverable"))
                        .filter(object -> object.getAdoptedVersionId() != null)
                        .map(AigcProjectObject::getId)
                        .toList();
        bumpRevision(project, List.of(review.getId()), List.of(), null, "项目审核通过");
        project.setStatus("delivering");
        project.setLastActiveTime(LocalDateTime.now());
        repository.save(project);
        eventPublisher.publishEvent(
                new AigcProjectReviewApprovedEvent(
                        UUID.randomUUID(),
                        project.getId(),
                        review.getId(),
                        deliverables,
                        Instant.now()));
        return toApiView(project);
    }

    @Override
    @Transactional
    public AigcProjectView complete(Long projectId, Integer expectedVersion) {
        var project = requireLockedProject(projectId, expectedVersion);
        if (!"delivering".equals(project.getStatus())) {
            throw badRequest("项目必须先完成审核并进入交付阶段");
        }
        var evidence = completionEvidencePort.load(projectId);
        if (!evidence.hasActiveWork()) {
            throw badRequest("项目必须先收录至少一个未归档 Work");
        }
        if (!evidence.publicationsCompleted()) {
            throw badRequest("项目存在未发布成功的 Publication");
        }
        project.setStatus("completed");
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
        repository.save(project);
        return toApiView(project);
    }

    @Override
    @Transactional
    public AigcProjectView archive(Long projectId, Integer expectedVersion) {
        var project = requireLockedProject(projectId, expectedVersion);
        if ("archived".equals(project.getStatus())) {
            return toApiView(project);
        }
        project.setStatus("archived");
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
        repository.save(project);
        eventPublisher.publishEvent(
                new AigcProjectArchivedEvent(UUID.randomUUID(), projectId, Instant.now()));
        return toApiView(project);
    }

    @Transactional
    public AigcProjectVO updateStatus(Long projectId, String status, Integer expectedVersion) {
        if (!MUTABLE_STATUSES.contains(status)) {
            throw badRequest("通用状态更新只支持 draft/in_progress，其他生命周期请使用专用命令");
        }
        var project = requireLockedProject(projectId, expectedVersion);
        requireWritable(project);
        project.setStatus(status);
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
        repository.save(project);
        return toVO(project);
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

    public List<AigcProjectDocumentRefVO> documentRefs(Long projectId) {
        requireEntity(projectId);
        return documentRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                .map(
                        reference ->
                                new AigcProjectDocumentRefVO(
                                        reference.getId(),
                                        reference.getObjectId(),
                                        reference.getDocumentVersionId(),
                                        reference.getRole(),
                                        reference.getSortOrder()))
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
        var existing =
                resourceRefRepository.findByProjectIdAndResourceTypeAndResourceId(
                        projectId, resourceType, resourceId);
        if (existing.isPresent()) {
            var reference = existing.get();
            if (!java.util.Objects.equals(reference.getRole(), role)) {
                reference.setRole(role);
                resourceRefRepository.save(reference);
            }
            return;
        }
        var reference = new com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectResourceRef();
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
        requireEntity(projectId, CrudOperation.UPDATE, AccessMode.DEFAULT);
        var project = repository.findLockedById(projectId).orElseThrow(() -> notFound("项目不存在"));
        requireExpectedVersion(project, expectedVersion);
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
                project.getBudgetLimit(),
                project.getCostUsed(),
                project.getBrief(),
                project.getUserId());
    }

    private AigcProjectObjectView toApiObjectView(AigcProjectObject object) {
        return new AigcProjectObjectView(
                object.getId(),
                object.getProjectId(),
                object.getObjectType(),
                object.getStatus(),
                object.getAdoptedVersionId());
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
                object.getObjectKey(),
                object.getBlueprintNodeKey(),
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
            com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRelation relation) {
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

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
    }

    private void copyScope(AigcProject project, com.xuejiai.aaf.common.model.BaseEntity target) {
        target.setOrgId(project.getOrgId());
        target.setWorkspaceId(project.getWorkspaceId());
        target.setOwnerId(project.getOwnerId());
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(GlobalErrorCode.NOT_FOUND, message);
    }
}
