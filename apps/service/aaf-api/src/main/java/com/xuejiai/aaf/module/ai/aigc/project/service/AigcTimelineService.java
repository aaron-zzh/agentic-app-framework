package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcClip;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcTimeline;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcTrack;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcClipRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcTimelineRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcTrackRepository;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcClipCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcClipVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTimelineCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTimelinePageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTimelineUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTimelineVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTrackCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTrackVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 时间轴服务。 */
@Service
@RequiredArgsConstructor
public class AigcTimelineService
        extends BaseCrudService<
                AigcTimeline,
                AigcTimelineVO,
                AigcTimelineCreateDTO,
                AigcTimelineUpdateDTO,
                AigcTimelinePageDTO> {

    private final AigcTimelineRepository repository;
    private final AigcTrackRepository trackRepository;
    private final AigcClipRepository clipRepository;

    @Autowired private OperatorContext operatorContext;

    @Override
    protected JpaRepository<AigcTimeline, Long> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<AigcTimeline> getSpecExecutor() {
        return repository;
    }

    @Override
    protected String entityName() {
        return "时间轴";
    }

    @Override
    protected String entitySlug() {
        return "aigc-timeline";
    }

    @Override
    protected AigcTimelineVO toVO(AigcTimeline e) {
        var vo = new AigcTimelineVO();
        vo.setId(e.getId());
        vo.setProjectId(e.getProjectId());
        vo.setStoryboardId(e.getStoryboardId());
        vo.setTitle(e.getTitle());
        vo.setStatus(e.getStatus());
        vo.setDurationMs(e.getDurationMs());
        vo.setFps(e.getFps());
        vo.setResolution(e.getResolution());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    @Override
    protected AigcTimeline toEntity(AigcTimelineCreateDTO dto) {
        var e = new AigcTimeline();
        e.setProjectId(dto.projectId());
        e.setStoryboardId(dto.storyboardId());
        e.setTitle(dto.title());
        if (dto.fps() != null) e.setFps(dto.fps());
        if (dto.resolution() != null) e.setResolution(dto.resolution());
        return e;
    }

    @Override
    protected void updateEntity(AigcTimeline e, AigcTimelineUpdateDTO dto) {
        if (dto.title() != null) e.setTitle(dto.title());
        if (dto.status() != null) e.setStatus(dto.status());
        if (dto.durationMs() != null) e.setDurationMs(dto.durationMs());
        if (dto.fps() != null) e.setFps(dto.fps().shortValue());
        if (dto.resolution() != null) e.setResolution(dto.resolution());
    }

    @Override
    protected Specification<AigcTimeline> buildSpec(AigcTimelinePageDTO p) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (p.getProjectId() != null)
                predicates.add(cb.equal(root.get("projectId"), p.getProjectId()));
            if (p.getStoryboardId() != null)
                predicates.add(cb.equal(root.get("storyboardId"), p.getStoryboardId()));
            if (p.getStatus() != null) predicates.add(cb.equal(root.get("status"), p.getStatus()));
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 查询时间轴的所有轨道及其片段（剪辑工作台加载）。 */
    public List<AigcTrackVO> getTracks(Long timelineId) {
        return trackRepository.findByTimelineIdOrderBySortOrder(timelineId).stream()
                .map(
                        track -> {
                            var vo = toTrackVO(track);
                            vo.setClips(
                                    clipRepository
                                            .findByTrackIdOrderByPositionMs(track.getId())
                                            .stream()
                                            .map(this::toClipVO)
                                            .toList());
                            return vo;
                        })
                .toList();
    }

    /** 新增轨道。 */
    @Transactional
    public AigcTrackVO addTrack(AigcTrackCreateDTO dto) {
        var track = new AigcTrack();
        track.setTimelineId(dto.timelineId());
        track.setType(dto.type());
        if (dto.sortOrder() != null) track.setSortOrder(dto.sortOrder());
        trackRepository.save(track);
        var vo = toTrackVO(track);
        vo.setClips(List.of());
        return vo;
    }

    /** 删除轨道（同时删除轨道下所有片段）。 */
    @Transactional
    public void deleteTrack(Long trackId) {
        clipRepository.deleteByTrackId(trackId);
        trackRepository.deleteById(trackId);
    }

    /** 新增片段。 */
    @Transactional
    public AigcClipVO addClip(AigcClipCreateDTO dto) {
        var clip = new AigcClip();
        clip.setTrackId(dto.trackId());
        clip.setAssetId(dto.assetId());
        clip.setShotId(dto.shotId());
        if (dto.positionMs() != null) clip.setPositionMs(dto.positionMs());
        if (dto.inMs() != null) clip.setInMs(dto.inMs());
        if (dto.outMs() != null) clip.setOutMs(dto.outMs());
        clip.setProperties(dto.properties());
        clipRepository.save(clip);
        return toClipVO(clip);
    }

    /** 删除片段。 */
    @Transactional
    public void deleteClip(Long clipId) {
        clipRepository.deleteById(clipId);
    }

    /** 更新片段位置（拖拽）。 */
    @Transactional
    public AigcClipVO updateClipPosition(Long clipId, Long positionMs, Long inMs, Long outMs) {
        AigcClip clip =
                clipRepository
                        .findById(clipId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "片段不存在"));
        clip.setPositionMs(positionMs);
        clip.setInMs(inMs);
        clip.setOutMs(outMs);
        clipRepository.save(clip);
        return toClipVO(clip);
    }

    private AigcTrackVO toTrackVO(AigcTrack e) {
        var vo = new AigcTrackVO();
        vo.setId(e.getId());
        vo.setTimelineId(e.getTimelineId());
        vo.setType(e.getType());
        vo.setSortOrder(e.getSortOrder());
        vo.setMuted(e.getMuted());
        vo.setLocked(e.getLocked());
        return vo;
    }

    private AigcClipVO toClipVO(AigcClip e) {
        var vo = new AigcClipVO();
        vo.setId(e.getId());
        vo.setTrackId(e.getTrackId());
        vo.setAssetId(e.getAssetId());
        vo.setShotId(e.getShotId());
        vo.setPositionMs(e.getPositionMs());
        vo.setInMs(e.getInMs());
        vo.setOutMs(e.getOutMs());
        vo.setProperties(e.getProperties());
        return vo;
    }
}
