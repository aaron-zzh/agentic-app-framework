package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.AigcCanonicalRequest;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcApprovedManifestView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcCompletionEvaluationView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetCompletionView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetEvaluateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetManifestFreezeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSlotEvidence;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionAdoptCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionComparisonView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionRejectCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewDecisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewSubmitCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewView;
import com.xuejiai.aaf.module.ai.aigc.project.api.CompletionEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcObjectVersion;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectExecutionReservation;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectMediaRef;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectObject;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRevision;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcObjectVersionAdoptedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectReviewApprovedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcObjectVersionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectExecutionReservationRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectExecutionReservationTargetRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectMediaRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectObjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRevisionRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** Project 交付事实服务：候选、采用、清单、审核与完成策略均在同一聚合锁内闭环。 */
@Service
@RequiredArgsConstructor
public class AigcProjectDeliveryService {

    private static final Set<AigcProjectLifecycle> CONTENT_WRITABLE =
            Set.of(
                    AigcProjectLifecycle.CREATING,
                    AigcProjectLifecycle.EXECUTING,
                    AigcProjectLifecycle.ADOPTING);
    private static final Set<String> ACTIVE_REVIEW_STATUSES = Set.of("PENDING", "APPROVED");

    private final AigcProjectRepository projectRepository;
    private final AigcProjectObjectRepository objectRepository;
    private final AigcObjectVersionRepository versionRepository;
    private final AigcProjectRevisionRepository revisionRepository;
    private final AigcProjectConfigSnapshotRepository snapshotRepository;
    private final AigcProjectMediaRefRepository mediaRefRepository;
    private final AigcProjectExecutionReservationRepository reservationRepository;
    private final AigcProjectExecutionReservationTargetRepository reservationTargetRepository;
    private final AigcMediaApi mediaApi;
    private final CompletionEvidencePort completionEvidencePort;
    private final OperatorContext operatorContext;
    private final ApplicationEventPublisher eventPublisher;
    private final AigcActivityEventService activityEventService;

    @Transactional
    public AigcObjectVersionView appendCandidate(AigcObjectVersionCandidateCommand command) {
        var project = requireLockedProject(command.projectId());
        var object = requireObject(project.getId(), command.objectId(), true);
        var content = parseMap(command.contentJson());
        var requestHash = candidateRequestHash(command, content);
        var replay =
                versionRepository.findByObjectIdAndExecutionRunIdAndRequestHash(
                        object.getId(), command.executionRunId(), requestHash);
        if (replay.isPresent()) {
            return toVersionView(replay.get());
        }
        requireContentWritable(project);
        var reservation =
                reservationRepository
                        .findLockedById(command.executionReservationId())
                        .filter(
                                candidate ->
                                        Objects.equals(candidate.getProjectId(), project.getId()))
                        .orElseThrow(() -> badRequest("候选版本缺少项目执行 reservation 证据"));
        if (!"BOUND".equals(reservation.getStatus())
                || !Objects.equals(
                        reservation.getExecutionSubmissionId(), command.executionSubmissionId())
                || !Objects.equals(
                        reservation.getRootExecutionRunId(), command.rootExecutionRunId())
                || !reservationTargetRepository.existsByReservationIdAndProjectObjectId(
                        reservation.getId(), object.getId())) {
            throw badRequest("候选版本执行证据与目标项目对象不一致");
        }
        var version = new AigcObjectVersion();
        copyScope(project, version);
        version.setProjectId(project.getId());
        version.setObjectId(object.getId());
        version.setVersionNo(nextVersionNo(object.getId()));
        version.setStatus("candidate");
        version.setContentPayload(content);
        version.setDocumentVersionId(command.documentVersionId());
        version.setExecutionRunId(command.executionRunId());
        version.setRequestHash(requestHash);
        version.setSummary("执行候选");
        versionRepository.saveAndFlush(version);
        attachCandidateMedia(project, object, version, command.mediaVersionIds());
        object.setStatus("pending_confirm");
        objectRepository.save(object);
        var autoAdopt = isAutoAdoptIfEmpty(project, object) && object.getAdoptedVersionId() == null;
        if (!autoAdopt) {
            project.setStatus(AigcProjectLifecycle.ADOPTING);
        }
        bumpRevision(project, List.of(object.getId()), command.executionRunId(), "追加对象候选版本");
        if (autoAdopt) {
            adoptInternal(project, object, version, null, "AUTO_ADOPT_IF_EMPTY", "自动采用首个候选版本");
        }
        publishDeliveryActivity(
                project,
                "candidate.registered",
                command.executionRunId(),
                version.getId(),
                null,
                Map.of("objectId", object.getId(), "status", version.getStatus()));
        return toVersionView(version);
    }

    @Transactional
    public AigcObjectVersionView adopt(AigcObjectVersionAdoptCommand command) {
        requireText(command.idempotencyKey(), "采用幂等键不能为空");
        var requestHash = adoptRequestHash(command);
        var project = requireLockedProject(command.projectId());
        var object = requireObject(project.getId(), command.objectId(), true);
        var desired = requireVersion(project.getId(), object.getId(), command.objectVersionId());
        versionRepository
                .findByObjectIdAndIdempotencyKey(object.getId(), command.idempotencyKey())
                .filter(existing -> !existing.getId().equals(desired.getId()))
                .ifPresent(
                        existing -> {
                            throw conflict("采用幂等键已用于其他对象版本");
                        });
        if (Objects.equals(desired.getIdempotencyKey(), command.idempotencyKey())) {
            requireAdoptReplay(desired, command, requestHash);
            return toVersionView(desired);
        }
        if (desired.getId().equals(object.getAdoptedVersionId())) {
            requireAdoptReplay(desired, command, requestHash);
            return toVersionView(desired);
        }
        requireExpectedVersion(project, command.expectedProjectVersion());
        requireContentWritable(project);
        if (!Objects.equals(object.getAdoptedVersionId(), command.expectedAdoptedVersionId())) {
            throw conflict("已采用版本已变化，请刷新后重试");
        }
        if (!"candidate".equals(desired.getStatus())) {
            throw badRequest("只有候选版本可以采用");
        }
        if (object.getAdoptedVersionId() != null) {
            if (!command.confirmedReplacement()) {
                throw badRequest("替换已采用版本必须显式确认");
            }
            requireText(command.reason(), "替换已采用版本必须填写原因");
        }
        desired.setIdempotencyKey(command.idempotencyKey());
        desired.setRequestHash(requestHash);
        adoptInternal(
                project,
                object,
                desired,
                object.getAdoptedVersionId(),
                "MANUAL",
                normalizedReason(command.reason()));
        return toVersionView(desired);
    }

    @Transactional
    public AigcObjectVersionView reject(AigcObjectVersionRejectCommand command) {
        requireText(command.reason(), "否决原因不能为空");
        requireText(command.idempotencyKey(), "否决幂等键不能为空");
        var requestHash = rejectRequestHash(command);
        var project = requireLockedProject(command.projectId());
        var object = requireObject(project.getId(), command.objectId(), true);
        var version = requireVersion(project.getId(), object.getId(), command.objectVersionId());
        versionRepository
                .findByObjectIdAndIdempotencyKey(object.getId(), command.idempotencyKey())
                .filter(existing -> !existing.getId().equals(version.getId()))
                .ifPresent(
                        existing -> {
                            throw conflict("否决幂等键已用于其他对象版本");
                        });
        if ("rejected".equals(version.getStatus())) {
            if (!Objects.equals(version.getIdempotencyKey(), command.idempotencyKey())
                    || !Objects.equals(version.getRequestHash(), requestHash)) {
                throw conflict("否决请求幂等键或摘要与原请求不一致");
            }
            return toVersionView(version);
        }
        requireExpectedVersion(project, command.expectedProjectVersion());
        requireContentWritable(project);
        if (!"candidate".equals(version.getStatus())) {
            throw badRequest("只有候选版本可以否决");
        }
        version.setStatus("rejected");
        version.setIdempotencyKey(command.idempotencyKey());
        version.setRequestHash(requestHash);
        version.setDecisionMode("REJECTED");
        version.setDecisionReason(command.reason().trim());
        version.setDecidedTime(LocalDateTime.now());
        version.setDecidedBy(operatorContext.currentOperatorId().orElse(null));
        versionRepository.save(version);
        bumpRevision(project, List.of(object.getId()), version.getExecutionRunId(), "否决对象版本");
        staleReviews(project.getId(), List.of(object.getId()), "交付对象版本被否决");
        publishDeliveryActivity(
                project,
                "candidate.rejected",
                version.getExecutionRunId(),
                version.getId(),
                null,
                Map.of("objectId", object.getId(), "reason", command.reason().trim()));
        return toVersionView(version);
    }

    @Transactional(readOnly = true)
    public AigcDeliverableSetCompletionView evaluate(AigcDeliverableSetEvaluateCommand command) {
        var project = requireProject(command.projectId());
        if (command.expectedGraphRevision() != null
                && !command.expectedGraphRevision()
                        .equals(project.getGraphRevision().longValue())) {
            throw conflict("项目图谱修订已变化，请重新评估");
        }
        return evaluateInternal(
                project,
                command.setObjectId(),
                command.includedOptionalObjectIds(),
                project.getGraphRevision().longValue());
    }

    @Transactional
    public AigcObjectVersionView freeze(AigcDeliverableSetManifestFreezeCommand command) {
        requireText(command.idempotencyKey(), "冻结幂等键不能为空");
        var project = requireLockedProject(command.projectId());
        var setObject = requireDeliverableSet(project.getId(), command.setObjectId(), true);
        var requestHash = freezeRequestHash(command);
        var replay =
                versionRepository.findByObjectIdAndIdempotencyKey(
                        setObject.getId(), command.idempotencyKey());
        if (replay.isPresent()) {
            if (!Objects.equals(replay.get().getRequestHash(), requestHash)) {
                throw conflict("同一 manifest 幂等键对应不同请求");
            }
            return toVersionView(replay.get());
        }
        requireExpectedVersion(project, command.expectedProjectVersion());
        requireContentWritable(project);
        if (!Objects.equals(
                command.expectedGraphRevision(), project.getGraphRevision().longValue())) {
            throw conflict("项目图谱修订已变化，请重新评估");
        }
        var evaluated =
                evaluateInternal(
                        project,
                        setObject.getId(),
                        command.includedOptionalObjectIds(),
                        project.getGraphRevision().longValue());
        if (!Objects.equals(command.expectedEvidenceHash(), evaluated.evidenceHash())) {
            throw conflict("交付证据已变化，请重新评估");
        }
        if (!evaluated.complete()) {
            throw badRequest("DeliverableSet 尚不完整: " + String.join("；", evaluated.blockers()));
        }
        var finalRevision = project.getGraphRevision().longValue() + 1;
        var finalEvaluation =
                evaluateInternal(
                        project,
                        setObject.getId(),
                        command.includedOptionalObjectIds(),
                        finalRevision);
        var manifest = new AigcObjectVersion();
        copyScope(project, manifest);
        manifest.setProjectId(project.getId());
        manifest.setObjectId(setObject.getId());
        manifest.setVersionNo(nextVersionNo(setObject.getId()));
        manifest.setStatus("adopted");
        manifest.setContentPayload(manifestPayload(project, finalEvaluation));
        manifest.setSummary("DeliverableSet immutable manifest");
        manifest.setIdempotencyKey(command.idempotencyKey());
        manifest.setRequestHash(requestHash);
        manifest.setDecisionMode("MANIFEST_FREEZE");
        manifest.setDecisionReason("冻结交付清单");
        manifest.setDecidedTime(LocalDateTime.now());
        manifest.setDecidedBy(operatorContext.currentOperatorId().orElse(null));
        manifest.setAdoptedTime(LocalDateTime.now());
        manifest.setAdoptedBy(operatorContext.currentOwnerId().orElse(null));
        versionRepository.saveAndFlush(manifest);
        supersedeCurrent(setObject, manifest);
        setObject.setAdoptedVersionId(manifest.getId());
        setObject.setStatus("manifest_frozen");
        objectRepository.save(setObject);
        bumpRevision(project, List.of(setObject.getId()), null, "冻结 DeliverableSet manifest");
        staleReviews(
                project.getId(),
                Stream.concat(
                                Stream.of(setObject.getId()),
                                finalEvaluation.includedProjectObjectIds().stream())
                        .toList(),
                "交付清单已重新冻结");
        return toVersionView(manifest);
    }

    @Transactional
    public AigcReviewView submitReview(AigcReviewSubmitCommand command) {
        requireText(command.idempotencyKey(), "送审幂等键不能为空");
        var project = requireLockedProject(command.projectId());
        var business = new TreeMap<String, Object>();
        business.put("projectId", project.getId());
        business.put("setObjectId", command.setObjectId());
        business.put("manifestObjectVersionId", command.manifestObjectVersionId());
        var cas = new TreeMap<String, Object>();
        cas.put("expectedProjectVersion", command.expectedProjectVersion());
        var requestHash = AigcCanonicalRequest.of("project.review.submit", business, cas).sha256();
        var stableKey = "review.command." + hash(command.idempotencyKey()).substring(0, 32);
        var replay = objectRepository.findByProjectIdAndStableKey(project.getId(), stableKey);
        if (replay.isPresent()) {
            requirePayloadEquals(replay.get(), "requestHash", requestHash, "同一送审幂等键对应不同 manifest");
            return toReviewView(replay.get());
        }
        requireExpectedVersion(project, command.expectedProjectVersion());
        if (!Set.of(AigcProjectLifecycle.CREATING, AigcProjectLifecycle.ADOPTING)
                .contains(project.getStatus())) {
            throw badRequest("当前项目阶段不能送审");
        }
        var setObject = requireDeliverableSet(project.getId(), command.setObjectId(), true);
        var manifest =
                requireVersion(
                        project.getId(), setObject.getId(), command.manifestObjectVersionId());
        requireCurrentManifest(setObject, manifest);
        requireManifestCurrentEvidence(project, setObject, manifest);
        var review = new AigcProjectObject();
        copyScope(project, review);
        review.setProjectId(project.getId());
        review.setObjectType("review");
        review.setStableKey(stableKey);
        review.setInstanceNo(
                objectRepository.findHistoricalMaxCustomInstanceNo(project.getId(), "review") + 1);
        review.setContractRole("EXCLUDED");
        review.setParentId(setObject.getId());
        review.setTitle("Review for manifest " + manifest.getId());
        review.setStatus("PENDING");
        review.setSource("user");
        review.setPayload(
                reviewPayload(manifest, setObject, command.idempotencyKey(), requestHash));
        objectRepository.saveAndFlush(review);
        project.setStatus(AigcProjectLifecycle.REVIEWING);
        touchProject(project);
        publishDeliveryActivity(
                project,
                "review.submitted",
                null,
                command.manifestObjectVersionId(),
                review.getId(),
                Map.of("status", review.getStatus()));
        return toReviewView(review);
    }

    @Transactional
    public AigcReviewView approveReview(AigcReviewDecisionCommand command) {
        return decideReview(command, "APPROVED");
    }

    @Transactional
    public AigcReviewView returnReview(AigcReviewDecisionCommand command) {
        requireText(command.comment(), "退回审核必须填写意见");
        return decideReview(command, "RETURNED");
    }

    @Transactional(readOnly = true)
    public List<AigcReviewView> reviews(Long projectId) {
        return objectRepository
                .findByProjectIdAndObjectTypeOrderByIdAsc(projectId, "review")
                .stream()
                .map(this::toReviewView)
                .toList();
    }

    @Transactional(readOnly = true)
    public AigcApprovedManifestView requireApprovedManifest(
            Long projectId, Long setObjectId, Long manifestVersionId) {
        var project = requireProject(projectId);
        var setObject = requireDeliverableSet(projectId, setObjectId, false);
        var manifest = requireVersion(projectId, setObjectId, manifestVersionId);
        requireCurrentManifest(setObject, manifest);
        requireManifestCurrentEvidence(project, setObject, manifest);
        var review =
                objectRepository
                        .findByProjectIdAndObjectTypeOrderByIdAsc(projectId, "review")
                        .stream()
                        .filter(candidate -> "APPROVED".equals(candidate.getStatus()))
                        .filter(
                                candidate ->
                                        Objects.equals(
                                                payloadLong(candidate, "subjectObjectVersionId"),
                                                manifestVersionId))
                        .findFirst()
                        .orElseThrow(() -> badRequest("manifest 尚未通过审核或审核已 stale"));
        return new AigcApprovedManifestView(
                projectId,
                setObjectId,
                manifestVersionId,
                review.getId(),
                payloadString(review, "evidenceHash"));
    }

    @Transactional(readOnly = true)
    public AigcObjectVersionComparisonView compare(
            Long projectId, Long objectId, Long leftId, Long rightId) {
        var left = requireVersion(projectId, objectId, leftId);
        var right = requireVersion(projectId, objectId, rightId);
        return new AigcObjectVersionComparisonView(toComparisonItem(left), toComparisonItem(right));
    }

    @Transactional(readOnly = true)
    public AigcCompletionEvaluationView completion(Long projectId) {
        var project = requireProject(projectId);
        var snapshot =
                snapshotRepository
                        .findById(project.getConfigSnapshotId())
                        .filter(value -> projectId.equals(value.getProjectId()))
                        .orElseThrow(() -> notFound("项目配置快照不存在"));
        var policy = publicationPolicy(snapshot.getSnapshot());
        var evidence = completionEvidencePort.load(projectId);
        var requiredChannels =
                snapshot.getChannelVersions() == null
                        ? List.<Long>of()
                        : snapshot.getChannelVersions().stream().distinct().sorted().toList();
        var succeededChannels = evidence.succeededChannelSpecVersionIds();
        var blockers = new ArrayList<String>();
        if (!evidence.hasActiveWork()) {
            blockers.add("至少需要一个固定已批准 manifest 的未归档 Work");
        }
        if (evidence.activePublicationCount() > 0) {
            blockers.add("存在 PENDING/SCHEDULED/PUBLISHING Publication");
        }
        switch (policy) {
            case "NONE", "OPTIONAL" -> {
                // 仅要求共同前置条件。
            }
            case "AT_LEAST_ONE_SUCCESS" -> {
                if (succeededChannels.isEmpty()) {
                    blockers.add("至少需要一个发布成功的渠道");
                }
            }
            case "ALL_SELECTED_CHANNELS" -> {
                var missing =
                        requiredChannels.stream()
                                .filter(id -> !succeededChannels.contains(id))
                                .toList();
                if (!missing.isEmpty()) {
                    blockers.add("以下已选渠道尚未发布成功: " + missing);
                }
            }
            default -> throw badRequest("不支持的 publicationPolicy: " + policy);
        }
        return new AigcCompletionEvaluationView(
                policy,
                blockers.isEmpty(),
                evidence.activeWorkCount(),
                requiredChannels,
                succeededChannels,
                blockers);
    }

    @Transactional
    public void staleReviews(Long projectId, Collection<Long> changedObjectIds, String reason) {
        if (changedObjectIds == null || changedObjectIds.isEmpty()) {
            return;
        }
        var changed = Set.copyOf(changedObjectIds);
        var project = requireProject(projectId);
        var reviews =
                objectRepository.findByProjectIdAndObjectTypeAndStatusInOrderByIdAsc(
                        projectId, "review", ACTIVE_REVIEW_STATUSES);
        for (var review : reviews) {
            var included = payloadLongList(review, "includedObjectIds");
            if (changed.stream().noneMatch(included::contains)
                    && !changed.contains(payloadLong(review, "subjectObjectId"))) {
                continue;
            }
            var payload = mutablePayload(review);
            payload.put("staleReason", reason);
            payload.put("staleAt", LocalDateTime.now().toString());
            review.setPayload(payload);
            review.setStatus("STALE");
            review.setVersion(review.getVersion() + 1);
            objectRepository.save(review);
            publishDeliveryActivity(
                    project,
                    "review.stale",
                    null,
                    payloadLong(review, "subjectObjectVersionId"),
                    review.getId(),
                    Map.of("reason", reason));
        }
    }

    private AigcReviewView decideReview(AigcReviewDecisionCommand command, String decision) {
        requireText(command.idempotencyKey(), "审核决策幂等键不能为空");
        var requestHash = reviewDecisionRequestHash(command, decision);
        var project = requireLockedProject(command.projectId());
        var review = requireObject(project.getId(), command.reviewObjectId(), true);
        if (!"review".equals(review.getObjectType())) {
            throw badRequest("目标对象不是 Review");
        }
        if (Objects.equals(review.getStatus(), decision)) {
            requirePayloadEquals(
                    review, "decisionIdempotencyKey", command.idempotencyKey(), "审核决策幂等键冲突");
            requirePayloadEquals(review, "decisionRequestHash", requestHash, "审核决策摘要与原请求不一致");
            return toReviewView(review);
        }
        requireExpectedVersion(project, command.expectedProjectVersion());
        if (!AigcProjectLifecycle.REVIEWING.equals(project.getStatus())) {
            throw badRequest("项目不在 REVIEWING 阶段");
        }
        if (!"PENDING".equals(review.getStatus())) {
            throw conflict("Review 已被处理或已 stale");
        }
        if (!Objects.equals(review.getVersion(), command.expectedReviewVersion())) {
            throw conflict("Review 已被其他操作更新，请刷新后重试");
        }
        var manifestId = payloadLong(review, "subjectObjectVersionId");
        if (!Objects.equals(manifestId, command.expectedManifestObjectVersionId())) {
            throw conflict("审核目标 manifest 与预期不一致");
        }
        var setObjectId = payloadLong(review, "subjectObjectId");
        var setObject = requireDeliverableSet(project.getId(), setObjectId, true);
        var manifest = requireVersion(project.getId(), setObjectId, manifestId);
        requireCurrentManifest(setObject, manifest);
        requireManifestCurrentEvidence(project, setObject, manifest);
        var payload = mutablePayload(review);
        payload.put("decision", decision);
        payload.put("decisionIdempotencyKey", command.idempotencyKey());
        payload.put("decisionRequestHash", requestHash);
        payload.put("comment", command.comment());
        payload.put("decidedAt", LocalDateTime.now().toString());
        review.setPayload(payload);
        review.setSummary(command.comment());
        review.setStatus(decision);
        review.setVersion(review.getVersion() + 1);
        objectRepository.save(review);
        project.setStatus(
                "APPROVED".equals(decision)
                        ? AigcProjectLifecycle.DELIVERING
                        : AigcProjectLifecycle.CREATING);
        touchProject(project);
        publishDeliveryActivity(
                project,
                "review.decided",
                null,
                manifestId,
                review.getId(),
                Map.of("status", decision));
        if ("APPROVED".equals(decision)) {
            eventPublisher.publishEvent(
                    new AigcProjectReviewApprovedEvent(
                            UUID.randomUUID(),
                            project.getId(),
                            review.getId(),
                            setObjectId,
                            manifestId,
                            Instant.now()));
        }
        return toReviewView(review);
    }

    private AigcDeliverableSetCompletionView evaluateInternal(
            AigcProject project, Long setObjectId, List<Long> optionalIds, long graphRevision) {
        var setObject = requireDeliverableSet(project.getId(), setObjectId, false);
        var children =
                objectRepository.findByProjectIdAndParentIdOrderBySortOrderAscIdAsc(
                        project.getId(), setObject.getId());
        if (optionalIds.size() != optionalIds.stream().distinct().count()) {
            throw badRequest("显式 OPTIONAL 对象不能重复");
        }
        var byId =
                children.stream()
                        .collect(Collectors.toMap(AigcProjectObject::getId, Function.identity()));
        for (var optionalId : optionalIds) {
            var optional = byId.get(optionalId);
            if (optional == null || !"OPTIONAL".equals(optional.getContractRole())) {
                throw badRequest("显式 OPTIONAL 对象必须是该 DeliverableSet 的直接 OPTIONAL 子对象");
            }
        }
        var selectedOptional = Set.copyOf(optionalIds);
        var included =
                children.stream()
                        .filter(
                                child ->
                                        "REQUIRED".equals(child.getContractRole())
                                                || ("OPTIONAL".equals(child.getContractRole())
                                                        && selectedOptional.contains(
                                                                child.getId())))
                        .sorted(Comparator.comparing(AigcProjectObject::getId))
                        .toList();
        var activeReservations =
                activeReservations(included.stream().map(AigcProjectObject::getId).toList());
        var slots = new ArrayList<AigcDeliverableSlotEvidence>();
        var blockers = new ArrayList<String>();
        for (var child : included) {
            var adoptedId = child.getAdoptedVersionId();
            var validationStatus = validationStatus(child, adoptedId);
            var reservation = activeReservations.get(child.getId());
            if (adoptedId == null) {
                blockers.add("%s 尚无已采用版本".formatted(child.getStableKey()));
            }
            if (!"VALID".equals(validationStatus)) {
                blockers.add("%s 校验未通过: %s".formatted(child.getStableKey(), validationStatus));
            }
            if (reservation != null) {
                blockers.add(
                        "%s 正被 reservation %d 占用"
                                .formatted(child.getStableKey(), reservation.getId()));
            }
            slots.add(
                    new AigcDeliverableSlotEvidence(
                            child.getStableKey(),
                            child.getId(),
                            child.getContractRole(),
                            adoptedId,
                            validationStatus,
                            reservation == null ? null : reservation.getId(),
                            reservation == null ? null : reservation.getRootExecutionRunId()));
        }
        var includedIds = included.stream().map(AigcProjectObject::getId).toList();
        var evidenceHash = evidenceHash(graphRevision, slots);
        return new AigcDeliverableSetCompletionView(
                setObjectId,
                graphRevision,
                includedIds,
                blockers.isEmpty(),
                slots,
                blockers,
                evidenceHash);
    }

    private Map<Long, AigcProjectExecutionReservation> activeReservations(List<Long> objectIds) {
        if (objectIds.isEmpty()) {
            return Map.of();
        }
        var targets = reservationTargetRepository.findByProjectObjectIdInOrderByIdAsc(objectIds);
        var reservations =
                reservationRepository
                        .findAllById(
                                targets.stream()
                                        .map(target -> target.getReservationId())
                                        .distinct()
                                        .toList())
                        .stream()
                        .filter(
                                reservation ->
                                        Set.of("PREPARED", "BOUND")
                                                .contains(reservation.getStatus()))
                        .collect(
                                Collectors.toMap(
                                        AigcProjectExecutionReservation::getId,
                                        Function.identity()));
        var result = new LinkedHashMap<Long, AigcProjectExecutionReservation>();
        for (var target : targets) {
            var reservation = reservations.get(target.getReservationId());
            if (reservation != null) {
                result.putIfAbsent(target.getProjectObjectId(), reservation);
            }
        }
        return result;
    }

    private String validationStatus(AigcProjectObject object, Long adoptedId) {
        if (adoptedId == null) {
            return "MISSING";
        }
        var version = requireVersion(object.getProjectId(), object.getId(), adoptedId);
        if (!Set.of("adopted", "superseded").contains(version.getStatus())) {
            return "INVALID_VERSION_STATE";
        }
        var value =
                version.getContentPayload() == null
                        ? null
                        : version.getContentPayload().get("validationStatus");
        return value == null ? "VALID" : String.valueOf(value).toUpperCase();
    }

    private void adoptInternal(
            AigcProject project,
            AigcProjectObject object,
            AigcObjectVersion desired,
            Long replacedId,
            String mode,
            String reason) {
        if (replacedId != null) {
            var previous = requireVersion(project.getId(), object.getId(), replacedId);
            previous.setStatus("superseded");
            previous.setSupersededByVersionId(desired.getId());
            versionRepository.save(previous);
        }
        desired.setStatus("adopted");
        desired.setReplacedVersionId(replacedId);
        desired.setDecisionMode(mode);
        desired.setDecisionReason(reason);
        desired.setDecidedTime(LocalDateTime.now());
        desired.setDecidedBy(operatorContext.currentOperatorId().orElse(null));
        desired.setAdoptedTime(LocalDateTime.now());
        desired.setAdoptedBy(operatorContext.currentOwnerId().orElse(null));
        versionRepository.save(desired);
        object.setAdoptedVersionId(desired.getId());
        object.setStatus("adopted");
        objectRepository.save(object);
        for (var reference :
                mediaRefRepository.findByProjectIdOrderBySortOrderAscIdAsc(project.getId())) {
            if (desired.getId().equals(reference.getObjectVersionId())) {
                reference.setAdoptionStatus("adopted");
                mediaRefRepository.save(reference);
            } else if (replacedId != null && replacedId.equals(reference.getObjectVersionId())) {
                reference.setAdoptionStatus("superseded");
                mediaRefRepository.save(reference);
            }
        }
        var revision =
                bumpRevision(project, List.of(object.getId()), desired.getExecutionRunId(), reason);
        staleReviews(project.getId(), List.of(object.getId()), "交付对象采用版本已变化");
        publishDeliveryActivity(
                project,
                "candidate.adopted",
                desired.getExecutionRunId(),
                desired.getId(),
                null,
                Map.of("objectId", object.getId(), "mode", mode));
        eventPublisher.publishEvent(
                new AigcObjectVersionAdoptedEvent(
                        UUID.randomUUID(),
                        project.getId(),
                        object.getId(),
                        desired.getId(),
                        replacedId,
                        mode,
                        operatorContext.currentOperatorType().name(),
                        operatorContext.currentOperatorId().orElse(null),
                        desired.getExecutionRunId(),
                        revision.getRevisionNo().longValue(),
                        Instant.now()));
    }

    private void supersedeCurrent(AigcProjectObject object, AigcObjectVersion desired) {
        if (object.getAdoptedVersionId() == null) {
            return;
        }
        var previous =
                requireVersion(object.getProjectId(), object.getId(), object.getAdoptedVersionId());
        previous.setStatus("superseded");
        previous.setSupersededByVersionId(desired.getId());
        versionRepository.save(previous);
        desired.setReplacedVersionId(previous.getId());
    }

    private void requireAdoptReplay(
            AigcObjectVersion desired, AigcObjectVersionAdoptCommand command, String requestHash) {
        if (!Objects.equals(desired.getIdempotencyKey(), command.idempotencyKey())
                || !Objects.equals(desired.getRequestHash(), requestHash)) {
            throw conflict("采用重放幂等键或摘要与原请求不一致");
        }
    }

    private void attachCandidateMedia(
            AigcProject project,
            AigcProjectObject object,
            AigcObjectVersion version,
            List<Long> mediaVersionIds) {
        var sortOrder = 0;
        for (var mediaVersionId : mediaVersionIds) {
            mediaApi.getByVersionId(mediaVersionId, project.getUserId());
            var reference = new AigcProjectMediaRef();
            copyScope(project, reference);
            reference.setProjectId(project.getId());
            reference.setObjectId(object.getId());
            reference.setObjectVersionId(version.getId());
            reference.setMediaVersionId(mediaVersionId);
            reference.setRole("candidate");
            reference.setSortOrder(sortOrder++);
            reference.setAdoptionStatus("candidate");
            mediaRefRepository.save(reference);
        }
    }

    private boolean isAutoAdoptIfEmpty(AigcProject project, AigcProjectObject object) {
        var snapshot =
                snapshotRepository
                        .findById(project.getConfigSnapshotId())
                        .filter(value -> project.getId().equals(value.getProjectId()))
                        .orElseThrow(() -> notFound("项目配置快照不存在"));
        var resolved = snapshot.getSnapshot().get("resolvedObjects");
        if (!(resolved instanceof List<?> objects)) {
            return false;
        }
        for (var candidate : objects) {
            if (candidate instanceof Map<?, ?> value
                    && object.getStableKey().equals(String.valueOf(value.get("stableKey")))) {
                return "AUTO_ADOPT_IF_EMPTY".equals(String.valueOf(value.get("adoptionPolicy")));
            }
        }
        return false;
    }

    private Map<String, Object> manifestPayload(
            AigcProject project, AigcDeliverableSetCompletionView evidence) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("kind", "DELIVERABLE_SET_MANIFEST");
        payload.put("configurationSnapshotId", project.getConfigSnapshotId());
        payload.put("graphRevision", evidence.graphRevision());
        payload.put("includedObjectIds", evidence.includedProjectObjectIds());
        payload.put("slots", evidence.slots().stream().map(this::slotPayload).toList());
        payload.put("evidenceHash", evidence.evidenceHash());
        payload.put("createdAt", LocalDateTime.now().toString());
        return payload;
    }

    private Map<String, Object> slotPayload(AigcDeliverableSlotEvidence slot) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("stableKey", slot.stableKey());
        payload.put("objectId", slot.objectId());
        payload.put("contractRole", slot.contractRole());
        payload.put("adoptedObjectVersionId", slot.adoptedObjectVersionId());
        payload.put("validationStatus", slot.validationStatus());
        return payload;
    }

    private Map<String, Object> reviewPayload(
            AigcObjectVersion manifest,
            AigcProjectObject setObject,
            String idempotencyKey,
            String requestHash) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("subjectObjectId", setObject.getId());
        payload.put("subjectObjectVersionId", manifest.getId());
        payload.put("evidenceHash", manifest.getContentPayload().get("evidenceHash"));
        payload.put("manifestGraphRevision", manifest.getContentPayload().get("graphRevision"));
        payload.put("includedObjectIds", manifest.getContentPayload().get("includedObjectIds"));
        payload.put("idempotencyKey", idempotencyKey);
        payload.put("requestHash", requestHash);
        payload.put("submittedAt", LocalDateTime.now().toString());
        return payload;
    }

    private String evidenceHash(long graphRevision, List<AigcDeliverableSlotEvidence> slots) {
        var root = new TreeMap<String, Object>();
        root.put(
                "slots",
                slots.stream()
                        .map(
                                slot -> {
                                    var value = new TreeMap<String, Object>();
                                    value.put("stableKey", slot.stableKey());
                                    value.put("objectId", slot.objectId());
                                    value.put("contractRole", slot.contractRole());
                                    value.put(
                                            "adoptedObjectVersionId",
                                            slot.adoptedObjectVersionId());
                                    value.put("validationStatus", slot.validationStatus());
                                    value.put(
                                            "activeExecutionReservationId",
                                            slot.activeExecutionReservationId());
                                    value.put("activeExecutionRunId", slot.activeExecutionRunId());
                                    return value;
                                })
                        .toList());
        return hash(root);
    }

    private String adoptRequestHash(AigcObjectVersionAdoptCommand command) {
        var business = new TreeMap<String, Object>();
        business.put("projectId", command.projectId());
        business.put("objectId", command.objectId());
        business.put("objectVersionId", command.objectVersionId());
        business.put("confirmedReplacement", command.confirmedReplacement());
        business.put("reason", normalizedReason(command.reason()));
        var cas = new TreeMap<String, Object>();
        cas.put("expectedAdoptedVersionId", command.expectedAdoptedVersionId());
        cas.put("expectedProjectVersion", command.expectedProjectVersion());
        return AigcCanonicalRequest.of("project.object-version.adopt", business, cas).sha256();
    }

    private String rejectRequestHash(AigcObjectVersionRejectCommand command) {
        var business = new TreeMap<String, Object>();
        business.put("projectId", command.projectId());
        business.put("objectId", command.objectId());
        business.put("objectVersionId", command.objectVersionId());
        business.put("reason", command.reason().trim());
        var cas = new TreeMap<String, Object>();
        cas.put("expectedProjectVersion", command.expectedProjectVersion());
        return AigcCanonicalRequest.of("project.object-version.reject", business, cas).sha256();
    }

    private String reviewDecisionRequestHash(AigcReviewDecisionCommand command, String decision) {
        var business = new TreeMap<String, Object>();
        business.put("projectId", command.projectId());
        business.put("reviewObjectId", command.reviewObjectId());
        business.put("decision", decision);
        business.put("comment", command.comment());
        var cas = new TreeMap<String, Object>();
        cas.put("expectedManifestObjectVersionId", command.expectedManifestObjectVersionId());
        cas.put("expectedProjectVersion", command.expectedProjectVersion());
        cas.put("expectedReviewVersion", command.expectedReviewVersion());
        return AigcCanonicalRequest.of("project.review.decision", business, cas).sha256();
    }

    private String candidateRequestHash(
            AigcObjectVersionCandidateCommand command, Map<String, Object> content) {
        var value = new TreeMap<String, Object>();
        value.put("projectId", command.projectId());
        value.put("objectId", command.objectId());
        value.put("executionRunId", command.executionRunId());
        value.put("executionSubmissionId", command.executionSubmissionId());
        value.put("executionReservationId", command.executionReservationId());
        value.put("rootExecutionRunId", command.rootExecutionRunId());
        value.put("content", content);
        value.put("documentVersionId", command.documentVersionId());
        value.put("mediaVersionIds", command.mediaVersionIds());
        return hash(value);
    }

    private String freezeRequestHash(AigcDeliverableSetManifestFreezeCommand command) {
        var business = new TreeMap<String, Object>();
        business.put("projectId", command.projectId());
        business.put("setObjectId", command.setObjectId());
        business.put(
                "includedOptionalObjectIds",
                command.includedOptionalObjectIds().stream().sorted().toList());
        var cas = new TreeMap<String, Object>();
        cas.put("expectedProjectVersion", command.expectedProjectVersion());
        cas.put("expectedGraphRevision", command.expectedGraphRevision());
        cas.put("expectedEvidenceHash", command.expectedEvidenceHash());
        return AigcCanonicalRequest.of("project.manifest.freeze", business, cas).sha256();
    }

    private String hash(Object value) {
        return AigcCanonicalRequest.of("project.delivery", Map.of("value", value), Map.of())
                .sha256();
    }

    private AigcProjectRevision bumpRevision(
            AigcProject project, List<Long> changedObjectIds, Long sourceRunId, String summary) {
        project.setGraphRevision(project.getGraphRevision() + 1);
        touchProject(project);
        var revision = new AigcProjectRevision();
        revision.setProjectId(project.getId());
        revision.setRevisionNo(project.getGraphRevision());
        revision.setChangedObjectIds(List.copyOf(changedObjectIds));
        revision.setChangedRelationIds(List.of());
        revision.setActorType(operatorContext.currentOperatorType().name());
        revision.setActorId(operatorContext.currentOperatorId().orElse(null));
        revision.setSourceExecutionRunId(sourceRunId);
        revision.setSummary(summary);
        return revisionRepository.save(revision);
    }

    private void touchProject(AigcProject project) {
        project.setVersion(project.getVersion() + 1);
        project.setLastActiveTime(LocalDateTime.now());
        projectRepository.save(project);
    }

    private AigcProject requireLockedProject(Long projectId) {
        return projectRepository.findLockedById(projectId).orElseThrow(() -> notFound("项目不存在"));
    }

    private AigcProject requireProject(Long projectId) {
        return projectRepository.findById(projectId).orElseThrow(() -> notFound("项目不存在"));
    }

    private AigcProjectObject requireObject(Long projectId, Long objectId, boolean locked) {
        var result =
                locked
                        ? objectRepository.findLockedById(objectId)
                        : objectRepository.findById(objectId);
        return result.filter(object -> projectId.equals(object.getProjectId()))
                .orElseThrow(() -> notFound("项目对象不存在"));
    }

    private AigcProjectObject requireDeliverableSet(
            Long projectId, Long setObjectId, boolean locked) {
        var object = requireObject(projectId, setObjectId, locked);
        if ("deliverable_set".equals(object.getObjectType())) {
            return object;
        }
        var project = requireProject(projectId);
        var snapshot =
                snapshotRepository
                        .findById(project.getConfigSnapshotId())
                        .filter(value -> projectId.equals(value.getProjectId()))
                        .orElseThrow(() -> notFound("项目配置快照不存在"));
        var configured = snapshot.getSnapshot().get("deliverableSets");
        if (configured instanceof List<?> sets) {
            for (var candidate : sets) {
                if (candidate instanceof Map<?, ?> value) {
                    var templateKey = String.valueOf(value.get("setTemplateKey"));
                    if (templateKey.equals(object.getBlueprintTemplateKey())
                            || templateKey.equals(object.getStableKey())) {
                        return object;
                    }
                }
            }
        }
        throw badRequest("目标对象不是 DeliverableSet");
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

    private void requireManifestCurrentEvidence(
            AigcProject project, AigcProjectObject setObject, AigcObjectVersion manifest) {
        var optionalIds = new ArrayList<Long>();
        var slots = manifest.getContentPayload().get("slots");
        if (slots instanceof List<?> values) {
            for (var candidate : values) {
                if (!(candidate instanceof Map<?, ?> slot)
                        || !"OPTIONAL".equals(String.valueOf(slot.get("contractRole")))) {
                    continue;
                }
                var objectId = slot.get("objectId");
                if (objectId instanceof Number number) {
                    optionalIds.add(number.longValue());
                } else if (objectId != null) {
                    optionalIds.add(Long.valueOf(String.valueOf(objectId)));
                }
            }
        }
        var current =
                evaluateInternal(
                        project,
                        setObject.getId(),
                        optionalIds,
                        project.getGraphRevision().longValue());
        var storedHash = String.valueOf(manifest.getContentPayload().get("evidenceHash"));
        if (!current.complete() || !Objects.equals(storedHash, current.evidenceHash())) {
            throw conflict("manifest evidence 已变化或出现 blocker，请重新冻结");
        }
    }

    private void requireCurrentManifest(AigcProjectObject setObject, AigcObjectVersion manifest) {
        if (!manifest.getId().equals(setObject.getAdoptedVersionId())
                || !"adopted".equals(manifest.getStatus())
                || manifest.getContentPayload() == null
                || !"DELIVERABLE_SET_MANIFEST".equals(manifest.getContentPayload().get("kind"))) {
            throw conflict("manifest 已不是 DeliverableSet 当前有效清单");
        }
    }

    private void requireContentWritable(AigcProject project) {
        if (!CONTENT_WRITABLE.contains(project.getStatus())) {
            throw badRequest("当前项目阶段只读: " + project.getStatus());
        }
    }

    private void requireExpectedVersion(AigcProject project, Integer expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(project.getVersion())) {
            throw conflict("项目已被其他操作更新，请刷新后重试");
        }
    }

    private int nextVersionNo(Long objectId) {
        return versionRepository
                        .findFirstByObjectIdOrderByVersionNoDesc(objectId)
                        .map(AigcObjectVersion::getVersionNo)
                        .orElse(0)
                + 1;
    }

    private Map<String, Object> parseMap(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return Map.of();
        }
        var result =
                JsonUtils.parseObject(contentJson, new TypeReference<Map<String, Object>>() {});
        return result == null ? Map.of() : result;
    }

    private String publicationPolicy(Map<String, Object> snapshot) {
        var process = snapshot.get("processPolicy");
        if (process instanceof Map<?, ?> value && value.get("publicationPolicy") != null) {
            return String.valueOf(value.get("publicationPolicy")).toUpperCase();
        }
        throw badRequest("项目配置快照缺少 publicationPolicy");
    }

    private AigcReviewView toReviewView(AigcProjectObject review) {
        return new AigcReviewView(
                review.getId(),
                review.getVersion(),
                review.getProjectId(),
                payloadLong(review, "subjectObjectId"),
                payloadLong(review, "subjectObjectVersionId"),
                payloadString(review, "evidenceHash"),
                review.getStatus(),
                payloadString(review, "comment"),
                payloadString(review, "staleReason"),
                parseTime(payloadString(review, "submittedAt")),
                parseTime(payloadString(review, "decidedAt")));
    }

    private void publishDeliveryActivity(
            AigcProject project,
            String eventType,
            Long executionRunId,
            Long objectVersionId,
            Long reviewId,
            Object payload) {
        activityEventService.publish(
                project.getUserId(),
                eventType,
                project.getId(),
                executionRunId,
                null,
                objectVersionId,
                reviewId,
                null,
                null,
                payload);
    }

    private AigcObjectVersionComparisonView.AigcObjectVersionComparisonItem toComparisonItem(
            AigcObjectVersion version) {
        var mediaIds =
                mediaRefRepository
                        .findByProjectIdOrderBySortOrderAscIdAsc(version.getProjectId())
                        .stream()
                        .filter(reference -> version.getId().equals(reference.getObjectVersionId()))
                        .map(AigcProjectMediaRef::getMediaVersionId)
                        .toList();
        Long creditCost = null;
        return new AigcObjectVersionComparisonView.AigcObjectVersionComparisonItem(
                version.getId(),
                version.getStatus(),
                version.getContentPayload(),
                version.getDocumentVersionId(),
                mediaIds,
                version.getExecutionRunId(),
                creditCost,
                version.getCreateTime());
    }

    private AigcObjectVersionView toVersionView(AigcObjectVersion version) {
        return new AigcObjectVersionView(
                version.getId(),
                version.getObjectId(),
                version.getVersionNo(),
                version.getStatus(),
                version.getExecutionRunId());
    }

    private void copyScope(AigcProject project, BaseEntity target) {
        target.setOwnerId(project.getOwnerId());
        target.setOrgId(project.getOrgId());
        target.setWorkspaceId(project.getWorkspaceId());
    }

    private Map<String, Object> mutablePayload(AigcProjectObject object) {
        return object.getPayload() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(object.getPayload());
    }

    private void requirePayloadEquals(
            AigcProjectObject object, String key, Object expected, String message) {
        if (!Objects.equals(
                object.getPayload() == null ? null : object.getPayload().get(key), expected)) {
            throw conflict(message);
        }
    }

    private Long payloadLong(AigcProjectObject object, String key) {
        var value = object.getPayload() == null ? null : object.getPayload().get(key);
        return value instanceof Number number
                ? number.longValue()
                : value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private List<Long> payloadLongList(AigcProjectObject object, String key) {
        var value = object.getPayload() == null ? null : object.getPayload().get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(
                        item ->
                                item instanceof Number number
                                        ? number.longValue()
                                        : Long.valueOf(String.valueOf(item)))
                .toList();
    }

    private String payloadString(AigcProjectObject object, String key) {
        var value = object.getPayload() == null ? null : object.getPayload().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime parseTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }

    private String normalizedReason(String reason) {
        return reason == null || reason.isBlank() ? "采用对象版本" : reason.trim();
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
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
