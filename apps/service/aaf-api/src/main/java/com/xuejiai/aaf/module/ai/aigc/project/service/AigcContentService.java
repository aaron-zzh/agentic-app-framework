package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcContent;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcContentAsset;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcContentAssetId;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcContentAssetRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcContentRepository;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcContentAssetVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcContentCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcContentPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcContentUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcContentVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 内容产出服务。 */
@Service
@RequiredArgsConstructor
public class AigcContentService
        extends BaseCrudService<
                AigcContent,
                AigcContentVO,
                AigcContentCreateDTO,
                AigcContentUpdateDTO,
                AigcContentPageDTO> {

    public static final String COMMAND_PUBLISH = "AIGC_CONTENT_PUBLISH";
    public static final String COMMAND_ADD_ASSET = "AIGC_CONTENT_ADD_ASSET";
    public static final String COMMAND_REMOVE_ASSET = "AIGC_CONTENT_REMOVE_ASSET";

    private static final String FIELD_ASSETS = "assets";

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "type",
                    "title",
                    "platform",
                    "publishStatus",
                    "publishTime",
                    "createTime",
                    "updateTime");

    private final AigcContentRepository repository;
    private final AigcContentAssetRepository contentAssetRepository;

    @Autowired private OperatorContext operatorContext;

    @Override
    protected AigcContentRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcContentVO toVO(AigcContent e) {
        var vo = new AigcContentVO();
        vo.setId(e.getId());
        vo.setProjectId(e.getProjectId());
        vo.setType(e.getType());
        vo.setTitle(e.getTitle());
        vo.setDocId(e.getDocId());
        vo.setAssetIds(e.getAssetIds());
        vo.setPlatform(e.getPlatform());
        vo.setPublishStatus(e.getPublishStatus());
        vo.setPublishTime(e.getPublishTime());
        vo.setUserId(e.getUserId());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    @Override
    protected AigcContent toEntity(AigcContentCreateDTO dto) {
        var e = new AigcContent();
        e.setProjectId(dto.projectId());
        e.setType(dto.type());
        e.setTitle(dto.title());
        e.setDocId(dto.docId());
        e.setPlatform(dto.platform());
        e.setUserId(operatorContext.currentOwnerId().orElseThrow());
        return e;
    }

    @Override
    protected void updateEntity(AigcContent e, AigcContentUpdateDTO dto) {
        if (dto.title() != null) e.setTitle(dto.title());
        if (dto.docId() != null) e.setDocId(dto.docId());
        if (dto.assetIds() != null) e.setAssetIds(dto.assetIds());
        if (dto.platform() != null) e.setPlatform(dto.platform());
        if (dto.publishTime() != null) e.setPublishTime(dto.publishTime());
    }

    @Override
    protected Specification<AigcContent> buildSpec(AigcContentPageDTO p) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (p.getProjectId() != null)
                predicates.add(cb.equal(root.get("projectId"), p.getProjectId()));
            if (p.getType() != null) predicates.add(cb.equal(root.get("type"), p.getType()));
            if (p.getPublishStatus() != null)
                predicates.add(cb.equal(root.get("publishStatus"), p.getPublishStatus()));
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 发布内容（设置 publishStatus=PUBLISHED，记录发布时间）。 */
    // TODO(security): 改为独立 publish 领域命令，经统一 PDP 绑定内容 ID、命令摘要和 CURRENT/PROPOSED，禁止继续使用 GET 授权执行写入。
    @Transactional
    public AigcContentVO publish(Long id) {
        AigcContent content = requireEntity(id);
        content.setPublishStatus("PUBLISHED");
        content.setPublishTime(LocalDateTime.now());
        repository.save(content);
        return toVO(content);
    }

    /** 为内容产出添加素材关联。 */
    @Transactional
    public AigcContentAssetVO addAsset(Long contentId, Long assetId, String role) {
        var asset = new AigcContentAsset();
        asset.setId(new AigcContentAssetId(contentId, assetId));
        asset.setRole(role);
        contentAssetRepository.save(asset);
        return toAssetVO(asset);
    }

    /** 移除内容产出素材关联。 */
    @Transactional
    public void removeAsset(Long contentId, Long assetId) {
        contentAssetRepository.deleteById(new AigcContentAssetId(contentId, assetId));
    }

    /** 查询内容产出的素材列表。 */
    public List<AigcContentAssetVO> listAssets(Long contentId) {
        return contentAssetRepository.findById_ContentId(contentId).stream()
                .map(this::toAssetVO)
                .toList();
    }

    private AigcContentAssetVO toAssetVO(AigcContentAsset e) {
        var vo = new AigcContentAssetVO();
        vo.setContentId(e.getId().getContentId());
        vo.setAssetId(e.getId().getAssetId());
        vo.setRole(e.getRole());
        return vo;
    }
}
