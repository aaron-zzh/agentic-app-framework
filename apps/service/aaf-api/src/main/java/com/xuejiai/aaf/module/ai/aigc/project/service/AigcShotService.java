package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcShot;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcShotAsset;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcShotAssetId;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcShotAssetRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcShotRepository;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcShotAssetVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcShotCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcShotPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcShotUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcShotVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 分镜服务。 */
@Service
@RequiredArgsConstructor
public class AigcShotService
        extends BaseCrudService<
                AigcShot, AigcShotVO, AigcShotCreateDTO, AigcShotUpdateDTO, AigcShotPageDTO> {

    private final AigcShotRepository repository;
    private final AigcShotAssetRepository shotAssetRepository;

    @Override
    protected JpaRepository<AigcShot, Long> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<AigcShot> getSpecExecutor() {
        return repository;
    }

    @Override
    protected String entityName() {
        return "分镜";
    }

    @Override
    protected String entitySlug() {
        return "aigc-shot";
    }

    @Override
    protected AigcShotVO toVO(AigcShot e) {
        var vo = new AigcShotVO();
        vo.setId(e.getId());
        vo.setStoryboardId(e.getStoryboardId());
        vo.setShotNo(e.getShotNo());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setDialogue(e.getDialogue());
        vo.setProperties(e.getProperties());
        return vo;
    }

    @Override
    protected AigcShot toEntity(AigcShotCreateDTO dto) {
        var e = new AigcShot();
        e.setStoryboardId(dto.storyboardId());
        e.setShotNo(dto.shotNo());
        e.setName(dto.name());
        e.setDescription(dto.description());
        e.setDialogue(dto.dialogue());
        e.setProperties(dto.properties());
        return e;
    }

    @Override
    protected void updateEntity(AigcShot e, AigcShotUpdateDTO dto) {
        if (dto.shotNo() != null) e.setShotNo(dto.shotNo());
        if (dto.name() != null) e.setName(dto.name());
        if (dto.description() != null) e.setDescription(dto.description());
        if (dto.dialogue() != null) e.setDialogue(dto.dialogue());
        if (dto.properties() != null) e.setProperties(dto.properties());
    }

    @Override
    protected Specification<AigcShot> buildSpec(AigcShotPageDTO p) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (p.getStoryboardId() != null)
                predicates.add(cb.equal(root.get("storyboardId"), p.getStoryboardId()));
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 批量重排镜号（前端拖拽排序后调用）。 */
    @Transactional
    public void reorderShots(List<Long> orderedIds) {
        List<AigcShot> shots = repository.findAllById(orderedIds);
        for (int i = 0; i < orderedIds.size(); i++) {
            final int index = i;
            shots.stream()
                    .filter(s -> s.getId().equals(orderedIds.get(index)))
                    .findFirst()
                    .ifPresent(s -> s.setShotNo(index + 1));
        }
        repository.saveAll(shots);
    }

    /** 为分镜添加素材关联。 */
    @Transactional
    public AigcShotAssetVO addAsset(Long shotId, Long assetId, String role) {
        var asset = new AigcShotAsset();
        asset.setId(new AigcShotAssetId(shotId, assetId));
        asset.setRole(role);
        shotAssetRepository.save(asset);
        return toAssetVO(asset);
    }

    /** 移除分镜素材关联。 */
    @Transactional
    public void removeAsset(Long shotId, Long assetId) {
        shotAssetRepository.deleteById(new AigcShotAssetId(shotId, assetId));
    }

    /** 查询分镜的素材列表。 */
    public List<AigcShotAssetVO> listAssets(Long shotId) {
        return shotAssetRepository.findById_ShotId(shotId).stream().map(this::toAssetVO).toList();
    }

    private AigcShotAssetVO toAssetVO(AigcShotAsset e) {
        var vo = new AigcShotAssetVO();
        vo.setShotId(e.getId().getShotId());
        vo.setAssetId(e.getId().getAssetId());
        vo.setRole(e.getRole());
        return vo;
    }
}
