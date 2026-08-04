package com.xuejiai.aaf.module.ai.aigc.work.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcChannelSpecApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationResultCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationView;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkApi;
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

    private static final Set<String> RESULT_STATUSES =
            Set.of("publishing", "published", "failed", "canceled");
    private static final Set<String> TERMINAL_PUBLICATION_STATUSES =
            Set.of("published", "failed", "canceled");
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "WORKSPACE", "PUBLIC");

    private final AigcWorkRepository repository;
    private final AigcWorkPublicationRepository publicationRepository;
    private final AigcProjectApi projectApi;
    private final AigcChannelSpecApi channelSpecApi;
    private final OperatorContext operatorContext;
    private final ApplicationEventPublisher eventPublisher;

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
                work.getDeliverableObjectId(),
                work.getAdoptedObjectVersionId(),
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
    public AigcWorkView collect(AigcWorkCollectCommand command) {
        var existing =
                repository.findByDeliverableObjectIdAndAdoptedObjectVersionId(
                        command.deliverableObjectId(), command.adoptedObjectVersionId());
        if (existing.isPresent()) {
            requireOwner(existing.get());
            return toApiView(existing.get());
        }

        var project = projectApi.requireProject(command.projectId());
        requireCurrentOwner(project.userId());
        if (!"delivering".equals(project.lifecycleStage())) {
            throw badRequest("项目审核通过后才能收录 Work");
        }
        var object =
                projectApi.getGraph(command.projectId()).objects().stream()
                        .filter(candidate -> candidate.id().equals(command.deliverableObjectId()))
                        .findFirst()
                        .orElseThrow(() -> notFound("交付对象不存在"));
        if (!(object.objectType().endsWith("_deliverable")
                || "deliverable_set".equals(object.objectType()))) {
            throw badRequest("只有 Deliverable 对象可以收录为 Work");
        }
        if (!command.adoptedObjectVersionId().equals(object.adoptedVersionId())) {
            throw badRequest("Work 必须引用当前已采用的对象版本");
        }
        var version =
                projectApi.requireObjectVersion(
                        command.projectId(),
                        command.deliverableObjectId(),
                        command.adoptedObjectVersionId());
        if (!"adopted".equals(version.status())) {
            throw badRequest("Work 必须引用已采用的对象版本");
        }
        if (command.coverMediaVersionId() != null) {
            projectApi.requireAdoptedMediaVersion(
                    command.projectId(), null, command.coverMediaVersionId());
        }

        var work = new AigcWork();
        work.setProjectId(command.projectId());
        work.setDeliverableObjectId(command.deliverableObjectId());
        work.setAdoptedObjectVersionId(command.adoptedObjectVersionId());
        work.setTitle(project.name());
        work.setCoverMediaVersionId(command.coverMediaVersionId());
        work.setVisibility(requireVisibility(command.visibility()));
        work.setStatus("collected");
        work.setUserId(project.userId());
        work.setOrgId(project.orgId());
        work.setWorkspaceId(project.workspaceId());
        work.setOwnerId(project.userId());
        repository.save(work);
        eventPublisher.publishEvent(
                new AigcWorkCollectedEvent(
                        UUID.randomUUID(),
                        work.getId(),
                        work.getProjectId(),
                        work.getDeliverableObjectId(),
                        work.getAdoptedObjectVersionId(),
                        Instant.now()));
        return toApiView(work);
    }

    @Override
    @Transactional
    public AigcPublicationView publish(AigcWorkPublishCommand command) {
        var work = requireLockedWork(command.workId());
        requireOwner(work);
        requireMutable(work);
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw badRequest("发布幂等键不能为空");
        }
        var existing =
                publicationRepository.findByWorkIdAndIdempotencyKey(
                        work.getId(), command.idempotencyKey());
        if (existing.isPresent()) {
            return toApiView(existing.get());
        }
        var project = projectApi.requireProject(work.getProjectId());
        if (!project.channelSpecVersionIds().contains(command.channelSpecVersionId())) {
            throw badRequest("发布渠道规格版本未绑定到项目");
        }
        var channel = channelSpecApi.requirePublishedVersion(command.channelSpecVersionId());

        var publication = new AigcWorkPublication();
        copyScope(work, publication);
        publication.setWorkId(work.getId());
        publication.setChannelSpecVersionId(channel.id());
        publication.setChannelCode(channel.code());
        publication.setIdempotencyKey(command.idempotencyKey());
        publication.setScheduledTime(toLocalDateTime(command.scheduledAt()));
        publication.setStatus(
                command.scheduledAt() != null && command.scheduledAt().isAfter(Instant.now())
                        ? "scheduled"
                        : "pending");
        publicationRepository.save(publication);
        refreshWorkPublicationStatus(work);
        publishPublicationEvent(work, publication);
        return toApiView(publication);
    }

    @Override
    @Transactional
    public AigcPublicationView markPublicationResult(AigcPublicationResultCommand command) {
        var publication =
                publicationRepository
                        .findLockedById(command.publicationId())
                        .filter(candidate -> command.workId().equals(candidate.getWorkId()))
                        .orElseThrow(() -> notFound("Publication 不存在"));
        var work = requireLockedWork(publication.getWorkId());
        requireOwner(work);
        if ("archived".equals(work.getStatus())) {
            throw badRequest("归档 Work 不接受发布结果");
        }
        var status = command.status() == null ? "" : command.status().toLowerCase();
        if (!RESULT_STATUSES.contains(status)) {
            throw badRequest("不支持的 Publication 状态");
        }
        if (status.equals(publication.getStatus())) {
            return toApiView(publication);
        }
        requirePublicationTransition(publication.getStatus(), status);
        publication.setStatus(status);
        publication.setExternalId(command.externalId());
        publication.setExternalUrl(command.externalUrl());
        publication.setResponsePayload(parseMap(command.responseJson()));
        publication.setVersion(publication.getVersion() + 1);
        if ("published".equals(status)) {
            publication.setPublishedTime(LocalDateTime.now(ZoneOffset.UTC));
        }
        publicationRepository.save(publication);
        refreshWorkPublicationStatus(work);
        publishPublicationEvent(work, publication);
        return toApiView(publication);
    }

    private void requirePublicationTransition(String currentStatus, String targetStatus) {
        if (TERMINAL_PUBLICATION_STATUSES.contains(currentStatus)) {
            throw badRequest("Publication 已进入终态，不能再次变更");
        }
        var allowed =
                switch (currentStatus) {
                    case "pending", "scheduled" -> RESULT_STATUSES.contains(targetStatus);
                    case "publishing" -> !"publishing".equals(targetStatus);
                    default -> false;
                };
        if (!allowed) {
            throw badRequest(
                    "不支持的 Publication 状态流转: " + currentStatus + " -> " + targetStatus);
        }
    }

    private void refreshWorkPublicationStatus(AigcWork work) {
        var publications = publicationRepository.findByWorkIdOrderByIdDesc(work.getId());
        var status =
                !publications.isEmpty()
                                && publications.stream()
                                        .allMatch(
                                                publication ->
                                                        "published".equals(publication.getStatus()))
                        ? "published"
                        : "collected";
        if (!status.equals(work.getStatus())) {
            work.setStatus(status);
            work.setVersion(work.getVersion() + 1);
            repository.save(work);
        }
    }

    @Override
    @Transactional
    public AigcWorkView archive(Long workId, Integer expectedVersion) {
        var work = requireLockedWork(workId);
        requireOwner(work);
        requireExpectedVersion(work, expectedVersion);
        if ("archived".equals(work.getStatus())) {
            return toApiView(work);
        }
        work.setStatus("archived");
        work.setVersion(work.getVersion() + 1);
        repository.save(work);
        eventPublisher.publishEvent(
                new AigcWorkArchivedEvent(
                        UUID.randomUUID(), work.getId(), work.getProjectId(), Instant.now()));
        return toApiView(work);
    }

    public List<AigcWorkPublicationVO> publications(Long workId) {
        var work = requireEntity(workId);
        requireOwner(work);
        return publicationRepository.findByWorkIdOrderByIdDesc(workId).stream()
                .map(this::toVO)
                .toList();
    }

    private AigcWork requireLockedWork(Long workId) {
        return repository.findLockedById(workId).orElseThrow(() -> notFound("Work 不存在"));
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
                publication.getScheduledTime(),
                publication.getPublishedTime(),
                publication.getResponsePayload(),
                publication.getCreateTime(),
                publication.getUpdateTime());
    }

    private AigcWorkView toApiView(AigcWork work) {
        return new AigcWorkView(
                work.getId(),
                work.getProjectId(),
                work.getDeliverableObjectId(),
                work.getAdoptedObjectVersionId(),
                work.getStatus(),
                work.getVersion());
    }

    private AigcPublicationView toApiView(AigcWorkPublication publication) {
        return new AigcPublicationView(
                publication.getId(),
                publication.getWorkId(),
                publication.getChannelSpecVersionId(),
                publication.getChannelCode(),
                publication.getStatus(),
                publication.getExternalId(),
                publication.getExternalUrl());
    }

    private void requireExpectedVersion(AigcWork work, Integer expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(work.getVersion())) {
            throw badRequest("Work 已被其他操作更新，请刷新后重试");
        }
    }

    private void requireMutable(AigcWork work) {
        if ("archived".equals(work.getStatus())) {
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

    private String requireVisibility(String visibility) {
        var normalized =
                visibility == null || visibility.isBlank() ? "PRIVATE" : visibility.toUpperCase();
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
        return JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
    }

    private void copyScope(AigcWork work, AigcWorkPublication publication) {
        publication.setOrgId(work.getOrgId());
        publication.setWorkspaceId(work.getWorkspaceId());
        publication.setOwnerId(work.getOwnerId());
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(GlobalErrorCode.NOT_FOUND, message);
    }
}
