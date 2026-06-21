package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcClip;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcShot;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcShotAsset;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcStoryboard;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcTimeline;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcTrack;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcClipRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcShotAssetRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcShotRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcStoryboardRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcTimelineRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcTrackRepository;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcStoryboardCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcStoryboardPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcStoryboardUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcStoryboardVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTimelineVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 分镜规划服务。 */
@Service
@RequiredArgsConstructor
public class AigcStoryboardService
        extends BaseCrudService<
                AigcStoryboard,
                AigcStoryboardVO,
                AigcStoryboardCreateDTO,
                AigcStoryboardUpdateDTO,
                AigcStoryboardPageDTO> {

    private final AigcStoryboardRepository repository;
    private final AigcShotRepository shotRepository;
    private final AigcShotAssetRepository shotAssetRepository;
    private final AigcTimelineRepository timelineRepository;
    private final AigcTrackRepository trackRepository;
    private final AigcClipRepository clipRepository;

    @Autowired private OperatorContext operatorContext;

    @Override
    protected JpaRepository<AigcStoryboard, Long> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<AigcStoryboard> getSpecExecutor() {
        return repository;
    }

    @Override
    protected String entityName() {
        return "分镜规划";
    }

    @Override
    protected AigcStoryboardVO toVO(AigcStoryboard e) {
        var vo = new AigcStoryboardVO();
        vo.setId(e.getId());
        vo.setProjectId(e.getProjectId());
        vo.setTitle(e.getTitle());
        vo.setStatus(e.getStatus());
        vo.setDocId(e.getDocId());
        vo.setUserId(e.getUserId());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    @Override
    protected AigcStoryboard toEntity(AigcStoryboardCreateDTO dto) {
        var e = new AigcStoryboard();
        e.setProjectId(dto.projectId());
        e.setTitle(dto.title());
        e.setDocId(dto.docId());
        e.setUserId(operatorContext.currentUserId().orElseThrow());
        return e;
    }

    @Override
    protected void updateEntity(AigcStoryboard e, AigcStoryboardUpdateDTO dto) {
        if (dto.title() != null) e.setTitle(dto.title());
        if (dto.status() != null) e.setStatus(dto.status());
        if (dto.docId() != null) e.setDocId(dto.docId());
    }

    @Override
    protected Specification<AigcStoryboard> buildSpec(AigcStoryboardPageDTO p) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (p.getProjectId() != null)
                predicates.add(cb.equal(root.get("projectId"), p.getProjectId()));
            if (p.getStatus() != null) predicates.add(cb.equal(root.get("status"), p.getStatus()));
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 分镜一键导入时间轴。 按 shotNo 顺序，将每个分镜的 FINAL_VIDEO 素材创建为视频轨 clip。 */
    @Transactional
    public AigcTimelineVO importToTimeline(Long storyboardId, Long userId) {
        AigcStoryboard sb = requireEntity(storyboardId);

        // 创建时间轴
        AigcTimeline timeline = new AigcTimeline();
        timeline.setProjectId(sb.getProjectId());
        timeline.setStoryboardId(storyboardId);
        timeline.setTitle(sb.getTitle());
        timeline = timelineRepository.save(timeline);

        // 创建视频轨
        AigcTrack videoTrack = new AigcTrack();
        videoTrack.setTimelineId(timeline.getId());
        videoTrack.setType("VIDEO");
        videoTrack.setSortOrder(0);
        videoTrack = trackRepository.save(videoTrack);

        // 按镜号顺序创建 clip，默认每镜头 5s 占位
        List<AigcShot> shots = shotRepository.findByStoryboardIdOrderByShotNo(storyboardId);
        long positionMs = 0L;
        for (AigcShot shot : shots) {
            List<AigcShotAsset> assets = shotAssetRepository.findById_ShotId(shot.getId());
            Long assetId =
                    assets.stream()
                            .filter(a -> "FINAL_VIDEO".equals(a.getRole()))
                            .map(a -> a.getId().getAssetId())
                            .findFirst()
                            .orElse(null);

            AigcClip clip = new AigcClip();
            clip.setTrackId(videoTrack.getId());
            clip.setAssetId(assetId);
            clip.setShotId(shot.getId());
            clip.setPositionMs(positionMs);
            clipRepository.save(clip);
            positionMs += 5000L;
        }

        return toTimelineVO(timeline);
    }

    private AigcTimelineVO toTimelineVO(AigcTimeline t) {
        var vo = new AigcTimelineVO();
        vo.setId(t.getId());
        vo.setProjectId(t.getProjectId());
        vo.setStoryboardId(t.getStoryboardId());
        vo.setTitle(t.getTitle());
        vo.setStatus(t.getStatus());
        vo.setDurationMs(t.getDurationMs());
        vo.setFps(t.getFps());
        vo.setResolution(t.getResolution());
        vo.setCreateTime(t.getCreateTime());
        return vo;
    }
}
