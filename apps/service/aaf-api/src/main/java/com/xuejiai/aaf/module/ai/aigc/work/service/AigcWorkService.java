package com.xuejiai.aaf.module.ai.aigc.work.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcChannelSpecApi;
import com.xuejiai.aaf.module.ai.aigc.AigcCanonicalRequest;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationCancelCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationResultCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationRetryCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationView;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkApi;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkArchiveCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkCollectCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkPublishCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkView;
import com.xuejiai.aaf.module.ai.aigc.work.api.event.AigcWorkArchivedEvent;
import com.xuejiai.aaf.module.ai.aigc.work.api.event.AigcWorkCollectedEvent;
import com.xuejiai.aaf.module.ai.aigc.work.api.event.AigcWorkPublicationChangedEvent;
import com.xuejiai.aaf.module.ai.aigc.work.domain.AigcWork;
import com.xuejiai.aaf.module.ai.aigc.work.domain.AigcWorkPublication;
import com.xuejiai.aaf.module.ai.aigc.work.repository.AigcWorkPublicationRepository;
import com.xuejiai.aaf.module.ai.aigc.work.repository.AigcWorkRepository;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkPageDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkPublicationVO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** Work 管理根及受控 Publication 生命周期服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcWorkService
        extends BaseCrudService<AigcWork, AigcWorkVO, Void, AigcWorkUpdateDTO, AigcWorkPageDTO>
        implements AigcWorkApi {

    private static final Set<String> ACTIVE_PUBLICATION_STATUSES =
            Set.of("PENDING", "SCHEDULED", "PUBLISHING");
    private static final Set<String> RESULT_STATUSES = Set.of("PUBLISHING", "SUCCEEDED", "FAILED");
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "WORKSPACE", "PUBLIC");

    private final AigcWorkRepository repository;
    private final AigcWorkPublicationRepository publicationRepository;
    private final AigcProjectApi projectApi;
    private final AigcChannelSpecApi channelSpecApi;
    private final OperatorContext operatorContext;
    private final ApplicationEventPublisher eventPublisher;
    private final AigcActivityEventService activityEventService;

    @Override
    protected AigcWorkRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcWorkVO toVO(AigcWork work) {
        return new AigcWorkVO(
                work.getId(),
                work.getVersion(),
                work.getProjectId(),
                work.getDeliverableSetObjectId(),
                work.getManifestObjectVersionId(),
                work.getTitle(),
                work.getCoverMediaVersionId(),
                work.getStatus(),
                work.getVisibility(),
                work.getUserId(),
                work.getCreateTime(),
                work.getUpdateTime());
    }

    @Override
    protected AigcWork toEntity(Void ignored) {
        throw badRequest("Work 只能通过收录命令创建");
    }

    @Override
    protected void beforeUpdate(AigcWork work, AigcWorkUpdateDTO request) {
        projectApi.lockForWorkMutation(
                work.getProjectId(), work.getUserId(), request.expectedProjectVersion());
        repository.findLockedById(work.getId()).orElseThrow(() -> notFound("Work 不存在"));
    }

    @Override
    protected void updateEntity(AigcWork work, AigcWorkUpdateDTO request) {
        requireOwner(work);
        requireExpectedVersion(work, request.expectedVersion());
        requireMutable(work);
        if (request.title() != null) {
            work.setTitle(requireText(request.title(), "作品标题不能为空"));
        }
        if (request.coverMediaVersionId() != null) {
            projectApi.requireAdoptedMediaVersion(
                    work.getProjectId(), null, request.coverMediaVersionId());
            work.setCoverMediaVersionId(request.coverMediaVersionId());
        }
        if (request.visibility() != null) {
            work.setVisibility(requireVisibility(request.visibility()));
        }
        work.setVersion(work.getVersion() + 1);
        publishWorkActivity(work, "work.updated", null, Map.of("status", work.getStatus()));
    }

    @Override
    protected void beforeDelete(AigcWork work) {
        throw badRequest("Work 不开放删除，请使用归档命令");
    }

    @Override
    protected Long extractOwnerId(AigcWork work) {
        return work.getUserId();
    }

    @Override
    protected Specification<AigcWork> buildSpec(AigcWorkPageDTO request) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (request.getProjectId() != null) {
                predicates.add(cb.equal(root.get("projectId"), request.getProjectId()));
            }
            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }
            if (request.getVisibility() != null) {
                predicates.add(cb.equal(root.get("visibility"), request.getVisibility()));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize(
            com.xuejiai.aaf.module.ai.aigc.AigcAuthorities.HAS_WORK_COLLECT)
    public AigcWorkView collect(AigcWorkCollectCommand command) {
        requireText(command.idempotencyKey(), "收录幂等键不能为空");
        var ownerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.UNAUTHORIZED, "当前用户未登录"));
        var project =
                projectApi.lockForWorkMutation(
                        command.projectId(), ownerId, command.expectedProjectVersion());
        if (!AigcProjectLifecycle.DELIVERING.equals(project.lifecycleStage())) {
            throw badRequest("项目审核通过并进入 DELIVERING 后才能收录 Work");
        }
        var requestHash = collectRequestHash(command);
        var replay =
                repository.findByProjectIdAndCollectIdempotencyKey(
                        command.projectId(), command.idempotencyKey());
        if (replay.isPresent()) {
            requireOwner(replay.get());
            requireRequestHash(replay.get().getCollectRequestHash(), requestHash, "同一收录幂等键对应不同请求");
            return toApiView(replay.get());
        }
        var existing =
                repository.findByDeliverableSetObjectIdAndManifestObjectVersionId(
                        command.deliverableSetObjectId(), command.manifestObjectVersionId());
        if (existing.isPresent()) {
            requireOwner(existing.get());
            requireRequestHash(existing.get().getCollectRequestHash(), requestHash, "该 manifest 已以不同参数收录");
            return toApiView(existing.get());
        }
        projectApi.requireApprovedManifest(
                command.projectId(),
                command.deliverableSetObjectId(),
                command.manifestObjectVersionId());
        if (command.coverMediaVersionId() != null) {
            projectApi.requireAdoptedMediaVersion(
                    command.projectId(), null, command.coverMediaVersionId());
        }
        var work = new AigcWork();
        work.setProjectId(command.projectId());
        work.setDeliverableSetObjectId(command.deliverableSetObjectId());
        work.setManifestObjectVersionId(command.manifestObjectVersionId());
        work.setCollectIdempotencyKey(command.idempotencyKey());
        work.setCollectRequestHash(requestHash);
        work.setTitle(project.name());
        work.setCoverMediaVersionId(command.coverMediaVersionId());
        work.setVisibility(requireVisibility(command.visibility()));
        work.setStatus("COLLECTED");
        work.setUserId(project.userId());
        work.setOrgId(project.orgId());
        work.setWorkspaceId(project.workspaceId());
        work.setOwnerId(project.userId());
        repository.saveAndFlush(work);
        eventPublisher.publishEvent(
                new AigcWorkCollectedEvent(
                        UUID.randomUUID(),
                        work.getId(),
                        work.getProjectId(),
                        work.getDeliverableSetObjectId(),
                        work.getManifestObjectVersionId(),
                        Instant.now()));
        publishWorkActivity(work, "work.collected", null, Map.of("status", work.getStatus()));
        return toApiView(work);
    }

    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize(
            com.xuejiai.aaf.module.ai.aigc.AigcAuthorities.HAS_WORK_PUBLISH)
    public AigcPublicationView publish(AigcWorkPublishCommand command) {
        requireText(command.idempotencyKey(), "发布幂等键不能为空");
        var work =
                requireProjectThenWork(
                        command.workId(), command.expectedProjectVersion());
        var requestHash = publishRequestHash(command);
        var replay = publicationRepository.findByWorkIdAndIdempotencyKey(work.getId(), command.idempotencyKey());
        if (replay.isPresent()) {
            requireRequestHash(replay.get().getRequestHash(), requestHash, "同一发布幂等键对应不同请求");
            return toApiView(replay.get());
        }
        requireMutable(work);
        requireExpectedVersion(work, command.expectedWorkVersion());
        var project = projectApi.requireProject(work.getProjectId());
        requireProjectWritable(project.lifecycleStage());
        if (!project.channelSpecVersionIds().contains(command.channelSpecVersionId())) {
            throw badRequest("发布渠道规格版本未绑定到项目");
        }
        var channel = channelSpecApi.requirePublishedVersion(command.channelSpecVersionId());
        var publication = newPublication(
                work,
                channel.id(),
                channel.code(),
                command.scheduledAt(),
                command.idempotencyKey(),
                requestHash,
                null,
                0);
        touchWork(work);
        publishPublicationEvent(work, publication);
        return toApiView(publication);
    }

    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize(
            com.xuejiai.aaf.module.ai.aigc.AigcAuthorities.HAS_WORK_PUBLISH)
    public AigcPublicationView cancelPublication(AigcPublicationCancelCommand command) {
        requireText(command.reason(), "取消原因不能为空");
        requireText(command.idempotencyKey(), "取消幂等键不能为空");
        var requestHash = cancelRequestHash(command);
        var work =
                requireProjectThenWork(
                        command.workId(), command.expectedProjectVersion());
        requireMutable(work);
        var publication = requireLockedPublication(work.getId(), command.publicationId());
        if ("CANCELED".equals(publication.getStatus())) {
            if (!Objects.equals(publication.getCancelIdempotencyKey(), command.idempotencyKey())
                    || !Objects.equals(publication.getCancelRequestHash(), requestHash)) {
                throw conflict("取消重放幂等键或摘要与原请求不一致");
            }
            return toApiView(publication);
        }
        requireExpectedVersion(work, command.expectedWorkVersion());
        requirePublicationVersion(publication, command.expectedPublicationVersion());
        if (!ACTIVE_PUBLICATION_STATUSES.contains(publication.getStatus())) {
            throw badRequest("只有活动 Publication 可以取消");
        }
        publication.setStatus("CANCELED");
        publication.setCanceledTime(LocalDateTime.now(ZoneOffset.UTC));
        publication.setCancelIdempotencyKey(command.idempotencyKey());
        publication.setCancelRequestHash(requestHash);
        publication.setCancelReason(command.reason().trim());
        publication.setVersion(publication.getVersion() + 1);
        publicationRepository.save(publication);
        refreshWorkPublicationStatus(work, true);
        publishPublicationEvent(work, publication);
        return toApiView(publication);
    }

    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize(
            com.xuejiai.aaf.module.ai.aigc.AigcAuthorities.HAS_WORK_PUBLISH)
    public AigcPublicationView retryPublication(AigcPublicationRetryCommand command) {
        requireText(command.idempotencyKey(), "重试幂等键不能为空");
        var work =
                requireProjectThenWork(
                        command.workId(), command.expectedProjectVersion());
        requireMutable(work);
        var failed = requireLockedPublication(work.getId(), command.failedPublicationId());
        if (!"FAILED".equals(failed.getStatus())) {
            throw badRequest("只有 FAILED Publication 可以重试");
        }
        var requestHash = retryRequestHash(command, failed);
        var replay = publicationRepository.findByWorkIdAndIdempotencyKey(work.getId(), command.idempotencyKey());
        if (replay.isPresent()) {
            requireRequestHash(replay.get().getRequestHash(), requestHash, "同一重试幂等键对应不同请求");
            return toApiView(replay.get());
        }
        requireExpectedVersion(work, command.expectedWorkVersion());
        requirePublicationVersion(failed, command.expectedPublicationVersion());
        var publication = newPublication(
                work,
                failed.getChannelSpecVersionId(),
                failed.getChannelCode(),
                command.scheduledAt(),
                command.idempotencyKey(),
                requestHash,
                failed.getId(),
                failed.getRetryCount() + 1);
        touchWork(work);
        publishPublicationEvent(work, publication);
        return toApiView(publication);
    }

    @Override
    @Transactional
    public AigcPublicationView markPublicationResult(AigcPublicationResultCommand command) {
        var work =
                requireProjectThenWork(
                        command.workId(), command.expectedProjectVersion());
        requireMutable(work);
        var publication = requireLockedPublication(command.workId(), command.publicationId());
        requirePublicationVersion(publication, command.expectedPublicationVersion());
        var target = command.status() == null ? "" : command.status().toUpperCase();
        if (!RESULT_STATUSES.contains(target)) {
            throw badRequest("内部结果只接受 PUBLISHING/SUCCEEDED/FAILED");
        }
        if (target.equals(publication.getStatus())) {
            return toApiView(publication);
        }
        requirePublicationTransition(publication.getStatus(), target);
        publication.setStatus(target);
        publication.setExternalId(command.externalId());
        publication.setExternalUrl(command.externalUrl());
        publication.setResponsePayload(parseMap(command.responseJson()));
        publication.setVersion(publication.getVersion() + 1);
        if ("SUCCEEDED".equals(target)) {
            publication.setPublishedTime(LocalDateTime.now(ZoneOffset.UTC));
            publication.setFailureCode(null);
            publication.setFailureMessage(null);
        }
        if ("FAILED".equals(target)) {
            publication.setFailureCode(requireText(command.failureCode(), "失败结果必须包含 failureCode"));
            publication.setFailureMessage(requireText(command.failureMessage(), "失败结果必须包含 failureMessage"));
        }
        publicationRepository.save(publication);
        refreshWorkPublicationStatus(work, false);
        publishPublicationEvent(work, publication);
        return toApiView(publication);
    }

    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize(
            com.xuejiai.aaf.module.ai.aigc.AigcAuthorities.HAS_WORK_ARCHIVE)
    public AigcWorkView archive(AigcWorkArchiveCommand command) {
        requireText(command.idempotencyKey(), "归档幂等键不能为空");
        requireText(command.reason(), "归档原因不能为空");
        var work =
                requireProjectThenWork(
                        command.workId(), command.expectedProjectVersion());
        var business = new TreeMap<String, Object>();
        business.put("workId", command.workId());
        business.put("reason", command.reason().trim());
        var cas = new TreeMap<String, Object>();
        cas.put("expectedProjectVersion", command.expectedProjectVersion());
        cas.put("expectedWorkVersion", command.expectedWorkVersion());
        var requestHash = AigcCanonicalRequest.of("work.archive", business, cas).sha256();
        if ("ARCHIVED".equals(work.getStatus())) {
            if (!Objects.equals(work.getArchiveIdempotencyKey(), command.idempotencyKey())
                    || !Objects.equals(work.getArchiveRequestHash(), requestHash)) {
                throw conflict("Work 归档幂等键或请求参数与原请求不一致");
            }
            return toApiView(work);
        }
        requireExpectedVersion(work, command.expectedWorkVersion());
        var active =
                hasActivePublication(
                        publicationRepository.findByWorkIdOrderByIdDesc(work.getId()));
        if (active) {
            throw badRequest("存在活动 Publication，不能归档 Work");
        }
        work.setStatus("ARCHIVED");
        work.setArchiveIdempotencyKey(command.idempotencyKey());
        work.setArchiveRequestHash(requestHash);
        touchWork(work);
        eventPublisher.publishEvent(
                new AigcWorkArchivedEvent(
                        UUID.randomUUID(), work.getId(), work.getProjectId(), Instant.now()));
        publishWorkActivity(work, "work.archived", null, Map.of("status", work.getStatus()));
        return toApiView(work);
    }

    public List<AigcWorkPublicationVO> publications(Long workId) {
        var work = requireEntity(workId, CrudOperation.GET, AccessMode.DEFAULT);
        requireOwner(work);
        return publicationRepository.findByWorkIdOrderByIdDesc(workId).stream()
                .map(this::toVO)
                .toList();
    }

    private AigcWorkPublication newPublication(
            AigcWork work,
            Long channelSpecVersionId,
            String channelCode,
            Instant scheduledAt,
            String idempotencyKey,
            String requestHash,
            Long retryOfPublicationId,
            int retryCount) {
        var publication = new AigcWorkPublication();
        copyScope(work, publication);
        publication.setWorkId(work.getId());
        publication.setChannelSpecVersionId(channelSpecVersionId);
        publication.setChannelCode(channelCode);
        publication.setIdempotencyKey(idempotencyKey);
        publication.setRequestHash(requestHash);
        publication.setRetryOfPublicationId(retryOfPublicationId);
        publication.setRetryCount(retryCount);
        publication.setScheduledTime(toLocalDateTime(scheduledAt));
        publication.setStatus(
                scheduledAt != null && scheduledAt.isAfter(Instant.now())
                        ? "SCHEDULED"
                        : "PENDING");
        return publicationRepository.saveAndFlush(publication);
    }

    private void requirePublicationTransition(String current, String target) {
        var allowed = switch (current) {
            case "PENDING", "SCHEDULED" -> Set.of("PUBLISHING", "FAILED").contains(target);
            case "PUBLISHING" -> Set.of("SUCCEEDED", "FAILED").contains(target);
            default -> false;
        };
        if (!allowed) {
            throw badRequest("不支持的 Publication 状态流转: " + current + " -> " + target);
        }
    }

    private void refreshWorkPublicationStatus(AigcWork work, boolean forceTouch) {
        var succeeded = publicationRepository.findByWorkIdOrderByIdDesc(work.getId()).stream()
                .anyMatch(publication -> "SUCCEEDED".equals(publication.getStatus()));
        var target = succeeded ? "PUBLISHED" : "COLLECTED";
        if (!target.equals(work.getStatus())) {
            work.setStatus(target);
            touchWork(work);
        } else if (forceTouch) {
            touchWork(work);
        }
    }

    private AigcWork requireProjectThenWork(Long workId, Integer expectedProjectVersion) {
        var snapshot = requireEntity(workId, CrudOperation.UPDATE, AccessMode.DEFAULT);
        requireOwner(snapshot);
        projectApi.lockForWorkMutation(
                snapshot.getProjectId(), snapshot.getUserId(), expectedProjectVersion);
        return requireLockedWorkForUpdate(workId);
    }

    private AigcWork requireLockedWorkForUpdate(Long workId) {
        requireEntity(workId, CrudOperation.UPDATE, AccessMode.DEFAULT);
        var work = repository.findLockedById(workId).orElseThrow(() -> notFound("Work 不存在"));
        requireOwner(work);
        return work;
    }

    private AigcWorkPublication requireLockedPublication(Long workId, Long publicationId) {
        return publicationRepository.findLockedById(publicationId)
                .filter(publication -> workId.equals(publication.getWorkId()))
                .orElseThrow(() -> notFound("Publication 不存在"));
    }

    private void publishPublicationEvent(AigcWork work, AigcWorkPublication publication) {
        eventPublisher.publishEvent(
                new AigcWorkPublicationChangedEvent(
                        UUID.randomUUID(),
                        work.getId(),
                        work.getProjectId(),
                        publication.getId(),
                        publication.getStatus(),
                        Instant.now()));
        publishWorkActivity(
                work,
                "publication.changed",
                publication.getId(),
                Map.of("status", publication.getStatus()));
    }

    private void publishWorkActivity(
            AigcWork work, String eventType, Long publicationId, Object payload) {
        activityEventService.publish(
                work.getUserId(),
                eventType,
                work.getProjectId(),
                null,
                null,
                null,
                null,
                work.getId(),
                publicationId,
                payload);
    }

    private AigcWorkPublicationVO toVO(AigcWorkPublication publication) {
        return new AigcWorkPublicationVO(
                publication.getId(),
                publication.getVersion(),
                publication.getWorkId(),
                publication.getChannelSpecVersionId(),
                publication.getChannelCode(),
                publication.getExternalId(),
                publication.getExternalUrl(),
                publication.getStatus(),
                publication.getRetryOfPublicationId(),
                publication.getRetryCount(),
                publication.getFailureCode(),
                publication.getFailureMessage(),
                publication.getScheduledTime(),
                publication.getPublishedTime(),
                publication.getCanceledTime(),
                publication.getCancelReason(),
                publication.getResponsePayload(),
                publication.getCreateTime(),
                publication.getUpdateTime());
    }

    private AigcWorkView toApiView(AigcWork work) {
        return new AigcWorkView(
                work.getId(),
                work.getProjectId(),
                work.getDeliverableSetObjectId(),
                work.getManifestObjectVersionId(),
                work.getStatus(),
                work.getVersion());
    }

    private AigcPublicationView toApiView(AigcWorkPublication publication) {
        return new AigcPublicationView(
                publication.getId(),
                publication.getVersion(),
                publication.getWorkId(),
                publication.getChannelSpecVersionId(),
                publication.getChannelCode(),
                publication.getStatus(),
                publication.getRetryOfPublicationId(),
                publication.getRetryCount(),
                publication.getExternalId(),
                publication.getExternalUrl(),
                publication.getFailureCode(),
                publication.getFailureMessage());
    }

    private String collectRequestHash(AigcWorkCollectCommand command) {
        var business = new TreeMap<String, Object>();
        business.put("projectId", command.projectId());
        business.put("deliverableSetObjectId", command.deliverableSetObjectId());
        business.put("manifestObjectVersionId", command.manifestObjectVersionId());
        business.put("coverMediaVersionId", command.coverMediaVersionId());
        business.put("visibility", requireVisibility(command.visibility()));
        var cas = new TreeMap<String, Object>();
        cas.put("expectedProjectVersion", command.expectedProjectVersion());
        return AigcCanonicalRequest.of("work.collect", business, cas).sha256();
    }

    private String publishRequestHash(AigcWorkPublishCommand command) {
        var business = new TreeMap<String, Object>();
        business.put("workId", command.workId());
        business.put("channelSpecVersionId", command.channelSpecVersionId());
        business.put("scheduledAt", command.scheduledAt());
        var cas = new TreeMap<String, Object>();
        cas.put("expectedProjectVersion", command.expectedProjectVersion());
        cas.put("expectedWorkVersion", command.expectedWorkVersion());
        return AigcCanonicalRequest.of("work.publish", business, cas).sha256();
    }

    private String cancelRequestHash(AigcPublicationCancelCommand command) {
        var business = new TreeMap<String, Object>();
        business.put("workId", command.workId());
        business.put("publicationId", command.publicationId());
        business.put("reason", command.reason().trim());
        var cas = new TreeMap<String, Object>();
        cas.put("expectedProjectVersion", command.expectedProjectVersion());
        cas.put("expectedWorkVersion", command.expectedWorkVersion());
        cas.put("expectedPublicationVersion", command.expectedPublicationVersion());
        return AigcCanonicalRequest.of("work.publication.cancel", business, cas).sha256();
    }

    private String retryRequestHash(
            AigcPublicationRetryCommand command, AigcWorkPublication failed) {
        var business = new TreeMap<String, Object>();
        business.put("workId", command.workId());
        business.put("failedPublicationId", failed.getId());
        business.put("channelSpecVersionId", failed.getChannelSpecVersionId());
        business.put("scheduledAt", command.scheduledAt());
        var cas = new TreeMap<String, Object>();
        cas.put("expectedProjectVersion", command.expectedProjectVersion());
        cas.put("expectedWorkVersion", command.expectedWorkVersion());
        cas.put("expectedPublicationVersion", command.expectedPublicationVersion());
        return AigcCanonicalRequest.of("work.publication.retry", business, cas).sha256();
    }

    private void requireExpectedVersion(AigcWork work, Integer expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(work.getVersion())) {
            throw conflict("Work 已被其他操作更新，请刷新后重试");
        }
    }

    static boolean hasActivePublication(List<AigcWorkPublication> publications) {
        return publications.stream()
                .anyMatch(
                        publication ->
                                ACTIVE_PUBLICATION_STATUSES.contains(publication.getStatus()));
    }

    void requirePublicationVersion(
            AigcWorkPublication publication, Integer expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(publication.getVersion())) {
            throw conflict("Publication 已被其他操作更新，请刷新后重试");
        }
    }

    private void requireRequestHash(String actual, String expected, String message) {
        if (!Objects.equals(actual, expected)) {
            throw conflict(message);
        }
    }

    private void requireProjectWritable(AigcProjectLifecycle lifecycleStage) {
        if (AigcProjectLifecycle.ARCHIVED.equals(lifecycleStage)) {
            throw badRequest("归档项目的 Work 与 Publication 历史只读");
        }
        if (!AigcProjectLifecycle.DELIVERING.equals(lifecycleStage)) {
            throw badRequest("只有 DELIVERING 阶段允许修改 Work 与 Publication");
        }
    }

    private void requireMutable(AigcWork work) {
        if ("ARCHIVED".equals(work.getStatus())) {
            throw badRequest("归档 Work 只读");
        }
    }

    private void requireOwner(AigcWork work) {
        requireCurrentOwner(work.getUserId());
    }

    private void requireCurrentOwner(Long ownerId) {
        if (operatorContext.currentOwnerId().filter(ownerId::equals).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "资源不存在");
        }
    }

    private void touchWork(AigcWork work) {
        work.setVersion(work.getVersion() + 1);
        repository.save(work);
    }

    private String requireVisibility(String visibility) {
        var normalized = visibility == null || visibility.isBlank() ? "PRIVATE" : visibility.toUpperCase();
        if (!VISIBILITIES.contains(normalized)) {
            throw badRequest("不支持的可见范围");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        var normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw badRequest(message);
        }
        return normalized;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        var result = JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
        return result == null ? Map.of() : result;
    }

    private void copyScope(AigcWork work, AigcWorkPublication publication) {
        publication.setOrgId(work.getOrgId());
        publication.setWorkspaceId(work.getWorkspaceId());
        publication.setOwnerId(work.getOwnerId());
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(GlobalErrorCode.NOT_FOUND, message);
    }
}
