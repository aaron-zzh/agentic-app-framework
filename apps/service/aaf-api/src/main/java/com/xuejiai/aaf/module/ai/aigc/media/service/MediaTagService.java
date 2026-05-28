package com.xuejiai.aaf.module.ai.aigc.media.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAssetTag;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaTag;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaAssetTagRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaTagRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaTagCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaTagVO;

import lombok.RequiredArgsConstructor;

/**
 * 素材标签服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class MediaTagService {

    private final MediaTagRepository tagRepository;
    private final MediaAssetTagRepository assetTagRepository;

    /**
     * 查询所有标签。
     *
     * @return 标签列表
     */
    @Transactional(readOnly = true)
    public List<MediaTagVO> list() {
        return tagRepository.findAll().stream().map(this::toVO).toList();
    }

    /**
     * 创建标签。
     *
     * @param dto 创建请求
     * @return 新建的标签
     */
    @Transactional
    public MediaTagVO create(MediaTagCreateDTO dto) {
        var tag = new MediaTag();
        tag.setName(dto.name());
        tag.setColor(dto.color());
        return toVO(tagRepository.save(tag));
    }

    /**
     * 更新标签。
     *
     * @param id 标签 ID
     * @param dto 更新请求
     * @return 更新后的标签
     */
    @Transactional
    public MediaTagVO update(Long id, MediaTagCreateDTO dto) {
        var tag = findById(id);
        tag.setName(dto.name());
        if (dto.color() != null) tag.setColor(dto.color());
        return toVO(tagRepository.save(tag));
    }

    /**
     * 删除标签。
     *
     * @param id 标签 ID
     */
    @Transactional
    public void delete(Long id) {
        var tag = findById(id);
        tagRepository.delete(tag);
    }

    /**
     * 为素材绑定标签。
     *
     * @param assetId 素材 ID
     * @param tagIds 标签 ID 列表
     */
    @Transactional
    public void bindTags(Long assetId, List<Long> tagIds) {
        assetTagRepository.deleteByAssetId(assetId);
        var relations =
                tagIds.stream()
                        .map(
                                tagId -> {
                                    var rel = new MediaAssetTag();
                                    rel.setAssetId(assetId);
                                    rel.setTagId(tagId);
                                    return rel;
                                })
                        .toList();
        assetTagRepository.saveAll(relations);
    }

    /**
     * 获取素材的标签列表。
     *
     * @param assetId 素材 ID
     * @return 标签列表
     */
    @Transactional(readOnly = true)
    public List<MediaTagVO> getTagsByAssetId(Long assetId) {
        var assetTags = assetTagRepository.findByAssetId(assetId);
        var tagIds = assetTags.stream().map(MediaAssetTag::getTagId).toList();
        if (tagIds.isEmpty()) return List.of();
        return tagRepository.findAllById(tagIds).stream().map(this::toVO).toList();
    }

    private MediaTag findById(Long id) {
        return tagRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "标签不存在"));
    }

    private MediaTagVO toVO(MediaTag tag) {
        return new MediaTagVO(tag.getId(), tag.getName(), tag.getColor());
    }
}
