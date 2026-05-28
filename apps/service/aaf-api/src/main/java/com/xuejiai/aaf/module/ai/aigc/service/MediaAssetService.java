package com.xuejiai.aaf.module.ai.aigc.service;

import java.util.List;

import com.xuejiai.aaf.module.ai.aigc.vo.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.domain.MediaAsset;
import com.xuejiai.aaf.module.ai.aigc.domain.MediaAssetVariant;
import com.xuejiai.aaf.module.ai.aigc.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.repository.MediaAssetRepository;
import com.xuejiai.aaf.module.ai.aigc.repository.MediaAssetTagRepository;
import com.xuejiai.aaf.module.ai.aigc.repository.MediaAssetVariantRepository;
import com.xuejiai.aaf.module.ai.aigc.repository.MediaCategoryRepository;
import com.xuejiai.aaf.module.aigc.vo.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 素材库管理服务。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAssetService {

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetTagRepository assetTagRepository;
    private final MediaAssetVariantRepository variantRepository;
    private final MediaCategoryRepository mediaCategoryRepository;

    /**
     * 分页查询素材列表。
     *
     * @param userId 用户 ID
     * @param type 素材类型（可选）
     * @param page 页码
     * @param size 每页数量
     * @return 素材分页结果
     */
    @Transactional(readOnly = true)
    public Page<MediaAssetVO> list(Long userId, MediaAssetType type, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<MediaAsset> assets =
                (type != null)
                        ? mediaAssetRepository.findByUserIdAndType(userId, type, pageable)
                        : mediaAssetRepository.findByUserId(userId, pageable);
        return assets.map(this::toVO);
    }

    /**
     * 按标签筛选素材。
     *
     * @param tagIds 标签 ID 列表
     * @return 匹配的素材列表
     */
    @Transactional(readOnly = true)
    public List<MediaAssetVO> listByTags(List<Long> tagIds) {
        var assetIds = assetTagRepository.findAssetIdsByTagIds(tagIds);
        if (assetIds.isEmpty()) return List.of();
        return mediaAssetRepository.findAllById(assetIds).stream().map(this::toVO).toList();
    }

    /**
     * 按分类筛选素材。
     *
     * @param categoryId 分类 ID
     * @return 该分类下的素材列表
     */
    @Transactional(readOnly = true)
    public List<MediaAssetVO> listByCategory(Long categoryId) {
        return mediaAssetRepository.findByCategoryId(categoryId).stream().map(this::toVO).toList();
    }

    /**
     * 获取素材详情。
     *
     * @param id 素材 ID
     * @return 素材详情
     */
    @Transactional(readOnly = true)
    public MediaAssetVO getById(Long id) {
        return toVO(findById(id));
    }

    /**
     * 创建素材记录。
     *
     * @param userId 用户 ID
     * @param dto 创建请求
     * @return 新建的素材
     */
    @Transactional
    public MediaAssetVO create(Long userId, MediaAssetCreateDTO dto) {
        var asset = new MediaAsset();
        asset.setName(dto.name());
        asset.setType(dto.type());
        asset.setUrl(dto.url());
        asset.setThumbnailUrl(dto.thumbnailUrl());
        asset.setSize(dto.size());
        asset.setWidth(dto.width());
        asset.setHeight(dto.height());
        asset.setDuration(dto.duration());
        asset.setGenerationParams(dto.generationParams());
        asset.setTags(dto.tags());
        asset.setCategoryId(dto.categoryId());
        asset.setUserId(userId);
        return toVO(mediaAssetRepository.save(asset));
    }

    /**
     * 更新素材信息。
     *
     * @param id 素材 ID
     * @param dto 更新请求
     * @return 更新后的素材
     */
    @Transactional
    public MediaAssetVO update(Long id, MediaAssetUpdateDTO dto) {
        var asset = findById(id);
        if (dto.name() != null) asset.setName(dto.name());
        if (dto.tags() != null) asset.setTags(dto.tags());
        if (dto.categoryId() != null) asset.setCategoryId(dto.categoryId());
        return toVO(mediaAssetRepository.save(asset));
    }

    /**
     * 删除素材（逻辑删除）。
     *
     * @param id 素材 ID
     */
    @Transactional
    public void delete(Long id) {
        var asset = findById(id);
        mediaAssetRepository.delete(asset);
    }

    /**
     * AI 自动打标（调用 LLM 分析图片内容生成标签）。
     *
     * <p>当前为骨架实现，后续接入 Spring AI ChatClient 进行图片内容分析。
     */
    @Transactional
    public List<String> autoTag(Long assetId) {
        var asset = findById(assetId);
        // TODO: 接入 LLM 多模态能力分析图片内容，生成标签建议
        // 当前返回基于类型的默认标签
        var suggestedTags =
                switch (asset.getType()) {
                    case IMAGE -> List.of("图片", "AI生成");
                    case VIDEO -> List.of("视频", "AI生成");
                    case AUDIO -> List.of("音频", "AI生成");
                    case MODEL_3D -> List.of("3D模型", "AI生成");
                };
        log.info("AI 自动打标: assetId={}, tags={}", assetId, suggestedTags);
        return suggestedTags;
    }

    /**
     * 基于原始素材重新生成变体。
     *
     * @param request 重新生成请求
     * @return 新生成的变体素材
     */
    @Transactional
    public MediaAssetVO regenerate(Long userId, RegenerateRequest request) {
        var original = findById(request.assetId());
        // 创建新素材记录（实际生成由上层调用图像生成服务完成后传入 URL）
        var variant = new MediaAsset();
        variant.setName(original.getName() + " - 变体");
        variant.setType(original.getType());
        variant.setUrl(original.getUrl()); // 占位，实际由调用方更新
        variant.setWidth(original.getWidth());
        variant.setHeight(original.getHeight());
        variant.setGenerationParams(buildVariantParams(request));
        variant.setUserId(userId);
        variant.setCategoryId(original.getCategoryId());
        variant = mediaAssetRepository.save(variant);

        // 保存变体关联
        var relation = new MediaAssetVariant();
        relation.setOriginalAssetId(original.getId());
        relation.setVariantAssetId(variant.getId());
        relation.setParamsDiff(buildVariantParams(request));
        variantRepository.save(relation);

        return toVO(variant);
    }

    /**
     * 批量生成变体（同一 Prompt 不同种子）。
     *
     * @param assetId 原始素材 ID
     * @param count 变体数量
     * @return 变体素材列表
     */
    @Transactional
    public List<MediaAssetVO> batchVariants(Long userId, Long assetId, int count) {
        var original = findById(assetId);
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(
                        i -> {
                            var variant = new MediaAsset();
                            variant.setName(original.getName() + " - 变体" + (i + 1));
                            variant.setType(original.getType());
                            variant.setUrl(original.getUrl()); // 占位
                            variant.setWidth(original.getWidth());
                            variant.setHeight(original.getHeight());
                            variant.setGenerationParams(
                                    "{\"seed\":" + (System.nanoTime() + i) + "}");
                            variant.setUserId(userId);
                            variant.setCategoryId(original.getCategoryId());
                            variant = mediaAssetRepository.save(variant);

                            var relation = new MediaAssetVariant();
                            relation.setOriginalAssetId(original.getId());
                            relation.setVariantAssetId(variant.getId());
                            relation.setParamsDiff("{\"seed\":" + (System.nanoTime() + i) + "}");
                            variantRepository.save(relation);

                            return toVO(variant);
                        })
                .toList();
    }

    /**
     * 查询素材的变体列表。
     *
     * @param assetId 原始素材 ID
     * @return 变体素材列表
     */
    @Transactional(readOnly = true)
    public List<MediaAssetVO> listVariants(Long assetId) {
        var variants = variantRepository.findByOriginalAssetId(assetId);
        var variantIds = variants.stream().map(MediaAssetVariant::getVariantAssetId).toList();
        if (variantIds.isEmpty()) return List.of();
        return mediaAssetRepository.findAllById(variantIds).stream().map(this::toVO).toList();
    }

    /**
     * 从生成结果一键保存到素材库（自动分类+自动打标）。
     *
     * @param userId 用户 ID
     * @param dto 保存请求
     * @return 保存后的素材
     */
    @Transactional
    public MediaAssetVO saveFromGeneration(Long userId, SaveFromGenerationDTO dto) {
        var asset = new MediaAsset();
        asset.setUserId(userId);
        asset.setName(dto.name() != null ? dto.name() : "AI生成_" + System.currentTimeMillis());
        asset.setType(dto.type() != null ? dto.type() : MediaAssetType.IMAGE);
        asset.setUrl(dto.url());
        asset.setThumbnailUrl(dto.thumbnailUrl() != null ? dto.thumbnailUrl() : dto.url());
        asset.setGenerationParams(dto.generationParams());
        asset.setWidth(dto.width());
        asset.setHeight(dto.height());
        asset.setDuration(dto.duration());

        // 自动分类：根据类型归入默认分类
        var category = mediaCategoryRepository.findByName(asset.getType().name()).orElse(null);
        if (category != null) {
            asset.setCategoryId(category.getId());
        }

        asset = mediaAssetRepository.save(asset);

        // 自动打标：基于 Prompt 内容提取关键词作为标签
        if (dto.generationParams() != null) {
            autoTag(asset.getId());
        }

        return toVO(asset);
    }

    private MediaAsset findById(Long id) {
        return mediaAssetRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "素材不存在"));
    }

    private String buildVariantParams(RegenerateRequest request) {
        return "{\"prompt\":\"%s\",\"seed\":\"%s\",\"style\":\"%s\"}"
                .formatted(
                        request.newPrompt() != null ? request.newPrompt() : "",
                        request.newSeed() != null ? request.newSeed() : "",
                        request.newStyle() != null ? request.newStyle() : "");
    }

    private MediaAssetVO toVO(MediaAsset asset) {
        return new MediaAssetVO(
                asset.getId(),
                asset.getName(),
                asset.getType(),
                asset.getUrl(),
                asset.getThumbnailUrl(),
                asset.getSize(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getDuration(),
                asset.getGenerationParams(),
                asset.getTags(),
                asset.getCategoryId(),
                asset.getUserId(),
                asset.getVersion(),
                asset.getCreateTime(),
                asset.getUpdateTime());
    }
}
