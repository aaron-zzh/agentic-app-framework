package com.xuejiai.aaf.module.ai.aigc.timeline.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectView;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcStoryboardExportView;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineApi;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineClipInput;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineCreateCommand;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineReplaceCommand;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineTrackInput;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineView;
import com.xuejiai.aaf.module.ai.aigc.timeline.domain.AigcTimelineClip;
import com.xuejiai.aaf.module.ai.aigc.timeline.domain.AigcTimelineComposition;
import com.xuejiai.aaf.module.ai.aigc.timeline.domain.AigcTimelineTrack;
import com.xuejiai.aaf.module.ai.aigc.timeline.repository.AigcStoryboardExportRepository;
import com.xuejiai.aaf.module.ai.aigc.timeline.repository.AigcTimelineClipRepository;
import com.xuejiai.aaf.module.ai.aigc.timeline.repository.AigcTimelineCompositionRepository;
import com.xuejiai.aaf.module.ai.aigc.timeline.repository.AigcTimelineTrackRepository;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcStoryboardExportVO;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcTimelineClipVO;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcTimelineCompositionVO;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcTimelinePageDTO;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcTimelineTrackVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** Timeline Composition 管理根服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcTimelineService
        extends BaseCrudService<
                AigcTimelineComposition, AigcTimelineCompositionVO, Void, Void, AigcTimelinePageDTO>
        implements AigcTimelineApi {

    private static final Set<String> TRACK_TYPES =
            Set.of("VIDEO", "VOICE", "MUSIC", "SUBTITLE", "OVERLAY");

    private final AigcTimelineCompositionRepository repository;
    private final AigcTimelineTrackRepository trackRepository;
    private final AigcTimelineClipRepository clipRepository;
    private final AigcStoryboardExportRepository storyboardExportRepository;
    private final AigcProjectApi projectApi;
    private final AigcMediaApi mediaApi;
    private final OperatorContext operatorContext;

    @Override
    protected AigcTimelineCompositionRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcTimelineCompositionVO toVO(AigcTimelineComposition composition) {
        return toCompositionVO(composition, false);
    }

    @Override
    protected AigcTimelineComposition toEntity(Void ignored) {
        throw badRequest("Timeline 只能通过创建命令建立");
    }

    @Override
    protected void updateEntity(AigcTimelineComposition composition, Void ignored) {
        throw badRequest("Timeline 只能通过整体替换命令更新");
    }

    @Override
    @Transactional
    protected void beforeDelete(AigcTimelineComposition composition) {
        var project = projectApi.requireProject(composition.getProjectId());
        requireCurrentOwner(project.userId());
        requireProjectWritable(project.lifecycleStage());
        deleteChildren(composition.getId());
    }

    @Override
    protected Specification<AigcTimelineComposition> buildSpec(AigcTimelinePageDTO request) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (request.getProjectId() != null) {
                predicates.add(cb.equal(root.get("projectId"), request.getProjectId()));
            }
            if (request.getDeliverableObjectId() != null) {
                predicates.add(
                        cb.equal(
                                root.get("deliverableObjectId"), request.getDeliverableObjectId()));
            }
            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    @Transactional
    public AigcTimelineView create(AigcTimelineCreateCommand command) {
        var project = projectApi.requireProject(command.projectId());
        requireCurrentOwner(project.userId());
        requireProjectWritable(project.lifecycleStage());
        if (command.deliverableObjectId() != null) {
            var object =
                    projectApi.getGraph(command.projectId()).objects().stream()
                            .filter(
                                    candidate ->
                                            candidate.id().equals(command.deliverableObjectId()))
                            .findFirst()
                            .orElseThrow(() -> notFound("Timeline 交付对象不存在"));
            if (!"video_deliverable".equals(object.objectType())) {
                throw badRequest("Timeline 只能关联视频交付对象");
            }
        }

        var composition = new AigcTimelineComposition();
        composition.setProjectId(command.projectId());
        composition.setDeliverableObjectId(command.deliverableObjectId());
        composition.setTitle(requireText(command.title(), "Timeline 标题不能为空"));
        composition.setDurationMs(defaultLong(command.durationMs()));
        composition.setFps(
                command.frameRate() == null ? BigDecimal.valueOf(30) : command.frameRate());
        composition.setWidth(command.width() == null ? 1920 : command.width());
        composition.setHeight(command.height() == null ? 1080 : command.height());
        composition.setStatus("draft");
        composition.setOrgId(project.orgId());
        composition.setWorkspaceId(project.workspaceId());
        composition.setOwnerId(project.userId());
        repository.save(composition);
        return toApiView(composition);
    }

    @Override
    @Transactional
    public AigcTimelineView replaceComposition(AigcTimelineReplaceCommand command) {
        var composition =
                repository
                        .findLockedById(command.timelineId())
                        .orElseThrow(() -> notFound("Timeline 不存在"));
        requireExpectedVersion(composition, command.expectedVersion());
        var project = projectApi.requireProject(composition.getProjectId());
        requireCurrentOwner(project.userId());
        requireProjectWritable(project.lifecycleStage());
        var graph = projectApi.getGraph(composition.getProjectId());
        var objectById = new HashMap<Long, AigcProjectObjectView>();
        graph.objects().forEach(object -> objectById.put(object.id(), object));

        validateTracks(command.tracks(), composition.getProjectId(), project.userId(), objectById);
        deleteChildren(composition.getId());
        persistTracks(composition, command.tracks(), objectById);
        composition.setAdoptedRevisionNo(Math.toIntExact(graph.revisionNo()));
        composition.setVersion(composition.getVersion() + 1);
        repository.save(composition);
        return toApiView(composition);
    }

    @Override
    public AigcTimelineView requireTimeline(Long timelineId) {
        var composition = requireEntity(timelineId);
        var project = projectApi.requireProject(composition.getProjectId());
        requireCurrentOwner(project.userId());
        return toApiView(composition);
    }

    public AigcTimelineCompositionVO composition(Long timelineId) {
        var composition = requireEntity(timelineId);
        var project = projectApi.requireProject(composition.getProjectId());
        requireCurrentOwner(project.userId());
        return toCompositionVO(composition, true);
    }

    @Override
    public List<AigcStoryboardExportView> storyboardExports(Long timelineId) {
        var composition = requireEntity(timelineId);
        var project = projectApi.requireProject(composition.getProjectId());
        requireCurrentOwner(project.userId());
        return storyboardExportRepository
                .findByProjectIdOrderBySourceRevisionNoDescIdDesc(composition.getProjectId())
                .stream()
                .filter(
                        export ->
                                composition.getDeliverableObjectId() == null
                                        || composition
                                                .getDeliverableObjectId()
                                                .equals(export.getDeliverableObjectId()))
                .map(
                        export ->
                                new AigcStoryboardExportView(
                                        export.getId(),
                                        export.getProjectId(),
                                        export.getSourceRevisionNo(),
                                        export.getExportMediaVersionId(),
                                        export.getExportFormat()))
                .toList();
    }

    public List<AigcStoryboardExportVO> storyboardExportVOs(Long timelineId) {
        var composition = requireEntity(timelineId);
        var project = projectApi.requireProject(composition.getProjectId());
        requireCurrentOwner(project.userId());
        return storyboardExportRepository
                .findByProjectIdOrderBySourceRevisionNoDescIdDesc(composition.getProjectId())
                .stream()
                .filter(
                        export ->
                                composition.getDeliverableObjectId() == null
                                        || composition
                                                .getDeliverableObjectId()
                                                .equals(export.getDeliverableObjectId()))
                .map(
                        export ->
                                new AigcStoryboardExportVO(
                                        export.getId(),
                                        export.getProjectId(),
                                        export.getSourceRevisionNo(),
                                        export.getDeliverableObjectId(),
                                        export.getExportMediaVersionId(),
                                        export.getExportFormat(),
                                        export.getCreateTime()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteProjectResources(Long projectId) {
        storyboardExportRepository.deleteAll(
                storyboardExportRepository.findByProjectIdOrderBySourceRevisionNoDescIdDesc(
                        projectId));
        var compositions = repository.findByProjectId(projectId);
        compositions.forEach(composition -> deleteChildren(composition.getId()));
        repository.deleteAll(compositions);
    }

    @Transactional
    public void archiveProjectTimelines(Long projectId) {
        repository
                .findByProjectId(projectId)
                .forEach(
                        composition -> {
                            if (!"archived".equals(composition.getStatus())) {
                                composition.setStatus("archived");
                                composition.setVersion(composition.getVersion() + 1);
                                repository.save(composition);
                            }
                        });
    }

    private void validateTracks(
            List<AigcTimelineTrackInput> tracks,
            Long projectId,
            Long userId,
            Map<Long, AigcProjectObjectView> objectById) {
        for (var track : tracks) {
            if (!TRACK_TYPES.contains(normalizeTrackType(track.trackType()))) {
                throw badRequest("不支持的 Timeline 轨道类型");
            }
            for (var clip : track.clips()) {
                validateClip(clip, projectId, userId, objectById);
            }
        }
    }

    private void validateClip(
            AigcTimelineClipInput clip,
            Long projectId,
            Long userId,
            Map<Long, AigcProjectObjectView> objectById) {
        if (clip.sourceObjectId() == null) {
            throw badRequest("Timeline Clip 必须引用 ProjectObject");
        }
        var object = objectById.get(clip.sourceObjectId());
        if (object == null || object.adoptedVersionId() == null) {
            throw badRequest("Timeline Clip 只能引用已采用的 ProjectObject");
        }
        if (clip.sourceObjectVersionId() != null
                && !clip.sourceObjectVersionId().equals(object.adoptedVersionId())) {
            throw badRequest("Timeline Clip 的对象版本不是当前采用版本");
        }
        projectApi.requireObjectVersion(projectId, object.id(), object.adoptedVersionId());
        if (clip.mediaVersionId() != null) {
            projectApi.requireAdoptedMediaVersion(projectId, object.id(), clip.mediaVersionId());
            mediaApi.getByVersionId(clip.mediaVersionId(), userId);
        }
        var position = defaultLong(clip.positionMs());
        var in = defaultLong(clip.inMs());
        var out = defaultLong(clip.outMs());
        if (position < 0 || in < 0 || out < in) {
            throw badRequest("Timeline Clip 时间范围无效");
        }
    }

    private void persistTracks(
            AigcTimelineComposition composition,
            List<AigcTimelineTrackInput> inputs,
            Map<Long, AigcProjectObjectView> objectById) {
        for (var input : inputs) {
            var track = new AigcTimelineTrack();
            copyScope(composition, track);
            track.setCompositionId(composition.getId());
            track.setTrackType(normalizeTrackType(input.trackType()));
            track.setName(input.name());
            track.setSortOrder(input.orderNo() == null ? 0 : input.orderNo());
            track.setMuted(Boolean.TRUE.equals(input.muted()));
            track.setLocked(Boolean.TRUE.equals(input.locked()));
            trackRepository.save(track);
            for (var clipInput : input.clips()) {
                var object = objectById.get(clipInput.sourceObjectId());
                var clip = new AigcTimelineClip();
                copyScope(composition, clip);
                clip.setTrackId(track.getId());
                clip.setMediaVersionId(clipInput.mediaVersionId());
                clip.setSourceObjectId(clipInput.sourceObjectId());
                clip.setSourceObjectVersionId(object.adoptedVersionId());
                clip.setPositionMs(defaultLong(clipInput.positionMs()));
                clip.setInMs(defaultLong(clipInput.inMs()));
                clip.setOutMs(defaultLong(clipInput.outMs()));
                clip.setProperties(parseMap(clipInput.propertiesJson()));
                clip.setTransition(parseMap(clipInput.transitionJson()));
                clip.setVolume(clipInput.volume());
                clipRepository.save(clip);
            }
        }
    }

    private AigcTimelineCompositionVO toCompositionVO(
            AigcTimelineComposition composition, boolean includeTracks) {
        var tracks =
                includeTracks ? loadTracks(composition.getId()) : List.<AigcTimelineTrackVO>of();
        return new AigcTimelineCompositionVO(
                composition.getId(),
                composition.getVersion(),
                composition.getProjectId(),
                composition.getDeliverableObjectId(),
                composition.getTitle(),
                composition.getDurationMs(),
                composition.getFps(),
                composition.getWidth(),
                composition.getHeight(),
                composition.getStatus(),
                composition.getAdoptedRevisionNo(),
                tracks,
                composition.getCreateTime(),
                composition.getUpdateTime());
    }

    private List<AigcTimelineTrackVO> loadTracks(Long compositionId) {
        var tracks = trackRepository.findByCompositionIdOrderBySortOrderAscIdAsc(compositionId);
        if (tracks.isEmpty()) {
            return List.of();
        }
        var clips =
                clipRepository.findByTrackIdInOrderByTrackIdAscPositionMsAscIdAsc(
                        tracks.stream().map(AigcTimelineTrack::getId).toList());
        var clipsByTrack = new HashMap<Long, List<AigcTimelineClipVO>>();
        for (var clip : clips) {
            clipsByTrack
                    .computeIfAbsent(clip.getTrackId(), ignored -> new ArrayList<>())
                    .add(toVO(clip));
        }
        return tracks.stream()
                .map(
                        track ->
                                new AigcTimelineTrackVO(
                                        track.getId(),
                                        track.getCompositionId(),
                                        track.getTrackType(),
                                        track.getName(),
                                        track.getSortOrder(),
                                        track.getMuted(),
                                        track.getLocked(),
                                        clipsByTrack.getOrDefault(track.getId(), List.of())))
                .toList();
    }

    private AigcTimelineClipVO toVO(AigcTimelineClip clip) {
        return new AigcTimelineClipVO(
                clip.getId(),
                clip.getTrackId(),
                clip.getMediaVersionId(),
                clip.getSourceObjectId(),
                clip.getSourceObjectVersionId(),
                clip.getPositionMs(),
                clip.getInMs(),
                clip.getOutMs(),
                clip.getProperties(),
                clip.getTransition(),
                clip.getVolume());
    }

    private void deleteChildren(Long compositionId) {
        var tracks = trackRepository.findByCompositionIdOrderBySortOrderAscIdAsc(compositionId);
        if (tracks.isEmpty()) {
            return;
        }
        var trackIds = tracks.stream().map(AigcTimelineTrack::getId).toList();
        clipRepository.deleteByTrackIdIn(trackIds);
        trackRepository.deleteByIdIn(trackIds);
    }

    private AigcTimelineView toApiView(AigcTimelineComposition composition) {
        return new AigcTimelineView(
                composition.getId(),
                composition.getProjectId(),
                composition.getDeliverableObjectId(),
                composition.getStatus(),
                composition.getVersion());
    }

    private void requireExpectedVersion(
            AigcTimelineComposition composition, Integer expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(composition.getVersion())) {
            throw badRequest("Timeline 已被其他操作更新，请刷新后重试");
        }
    }

    private void requireProjectWritable(String lifecycleStage) {
        if ("archived".equals(lifecycleStage)) {
            throw badRequest("归档项目不能修改 Timeline");
        }
    }

    private void requireCurrentOwner(Long ownerId) {
        if (operatorContext.currentOwnerId().filter(ownerId::equals).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "资源不存在");
        }
    }

    private String normalizeTrackType(String trackType) {
        return trackType == null ? "" : trackType.toUpperCase();
    }

    private String requireText(String value, String message) {
        var normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw badRequest(message);
        }
        return normalized;
    }

    private Long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
    }

    private void copyScope(
            AigcTimelineComposition composition, com.xuejiai.aaf.common.model.BaseEntity target) {
        target.setOrgId(composition.getOrgId());
        target.setWorkspaceId(composition.getWorkspaceId());
        target.setOwnerId(composition.getOwnerId());
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(GlobalErrorCode.NOT_FOUND, message);
    }
}
