package com.xuejiai.aaf.module.ai.aigc.media.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAsset;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAssetVariant;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaAssetRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaAssetVariantRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaAssetCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaAssetUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaAssetVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.RegenerateRequest;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 素材库服务。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAssetService {

    private final MediaAssetRepository assetRepository;
    private final MediaAssetVariantRepository variantRepository;
    private final ImageServiceFactory imageServiceFactory;

    /**
     * 分页查询素材。
     *
     * @param userId 用户 ID
     * @param type 素材类型（可选）
     * @param categoryId 分类 ID（可选）
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<MediaAssetVO> page(
            Long userId, MediaAssetType type, Long categoryId, Pageable pageable) {
        Page<MediaAsset> page;
        if (type != null) {
            page = assetRepository.findByUserIdAndType(userId, type, pageable);
        } else if (categoryId != null) {
            page = assetRepository.findByUserIdAndCategoryId(userId, categoryId, pageable);
        } else {
            page = assetRepository.findByUserId(userId, pageable);
        }
        return page.map(this::toVO);
    }

    /**
     * 搜索素材（按名称或标签模糊匹配）。
     *
     * @param userId 用户 ID
     * @param keyword 搜索关键词
     * @return 匹配的素材列表
     */
    @Transactional(readOnly = true)
    public List<MediaAssetVO> search(Long userId, String keyword) {
        return assetRepository.searchByKeyword(userId, keyword).stream().map(this::toVO).toList();
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
     * 创建素材。
     *
     * @param userId 用户 ID
     * @param dto 创建请求
     * @return 新建的素材
     */
    @Transactional
    public MediaAssetVO create(Long userId, MediaAssetCreateDTO dto) {
        var asset = new MediaAsset();
        asset.setUserId(userId);
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
        // AI 自动打标
        autoTag(asset);
        return toVO(assetRepository.save(asset));
    }

    /**
     * 更新素材。
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
        return toVO(assetRepository.save(asset));
    }

    /**
     * 删除素材（软删除）。
     *
     * @param id 素材 ID
     */
    @Transactional
    public void delete(Long id) {
        var asset = findById(id);
        assetRepository.delete(asset);
    }

    /**
     * 从生成结果一键保存到素材库。
     *
     * @param userId 用户 ID
     * @param dto 保存请求
     * @return 保存的素材
     */
    @Transactional
    public MediaAssetVO saveFromGeneration(Long userId, SaveFromGenerationDTO dto) {
        var asset = new MediaAsset();
        asset.setUserId(userId);
        asset.setName(dto.name() != null ? dto.name() : "AI生成素材");
        asset.setType(dto.type() != null ? dto.type() : MediaAssetType.IMAGE);
        asset.setUrl(dto.url());
        asset.setThumbnailUrl(dto.thumbnailUrl());
        asset.setWidth(dto.width());
        asset.setHeight(dto.height());
        asset.setDuration(dto.duration());
        asset.setGenerationParams(dto.generationParams());
        // AI 自动打标
        autoTag(asset);
        return toVO(assetRepository.save(asset));
    }

    /**
     * 素材重新生成：调用图像生成服务，创建变体关联。
     *
     * @param userId 用户 ID
     * @param request 重新生成请求
     * @return 新生成的变体素材
     */
    @Transactional
    public MediaAssetVO regenerate(Long userId, RegenerateRequest request) {
        var original = findById(request.assetId());
        // 构建生成请求
        var prompt = request.newPrompt() != null ? request.newPrompt() : extractPrompt(original);
        var service = imageServiceFactory.getSyncService(null);
        var result =
                service.generate(
                        new ImageRequest(
                                prompt,
                                null,
                                original.getWidth() != null ? original.getWidth() : 1024,
                                original.getHeight() != null ? original.getHeight() : 1024,
                                "url"));

        // 保存为新素材
        var variant = new MediaAsset();
        variant.setUserId(userId);
        variant.setName(original.getName() + " (变体)");
        variant.setType(original.getType());
        variant.setUrl(result.url());
        variant.setWidth(original.getWidth());
        variant.setHeight(original.getHeight());
        variant.setGenerationParams("{\"prompt\":\"%s\"}".formatted(prompt.replace("\"", "\\\"")));
        autoTag(variant);
        variant = assetRepository.save(variant);

        // 创建变体关联
        var relation = new MediaAssetVariant();
        relation.setOriginalAssetId(original.getId());
        relation.setVariantAssetId(variant.getId());
        relation.setParamsDiff("{\"newPrompt\":\"%s\"}".formatted(prompt.replace("\"", "\\\"")));
        variantRepository.save(relation);

        return toVO(variant);
    }

    /**
     * 查询素材的所有变体。
     *
     * @param assetId 原始素材 ID
     * @return 变体列表
     */
    @Transactional(readOnly = true)
    public List<MediaAssetVO> getVariants(Long assetId) {
        var relations = variantRepository.findByOriginalAssetId(assetId);
        var variantIds = relations.stream().map(MediaAssetVariant::getVariantAssetId).toList();
        if (variantIds.isEmpty()) return List.of();
        return assetRepository.findAllById(variantIds).stream().map(this::toVO).toList();
    }

    /**
     * 获取变体参数对比。
     *
     * @param assetId 原始素材 ID
     * @param variantId 变体 ID
     * @return 参数差异 JSON
     */
    @Transactional(readOnly = true)
    public String getVariantDiff(Long assetId, Long variantId) {
        return variantRepository.findByOriginalAssetId(assetId).stream()
                .filter(v -> v.getVariantAssetId().equals(variantId))
                .findFirst()
                .map(MediaAssetVariant::getParamsDiff)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "变体关联不存在"));
    }

    // ========== 内部方法 ==========

    private MediaAsset findById(Long id) {
        return assetRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "素材不存在"));
    }

    /** AI 自动打标：从 generationParams 中的 prompt 提取前 5 个逗号分隔的词作为 tags。 */
    private void autoTag(MediaAsset asset) {
        if (asset.getTags() != null && !asset.getTags().isBlank()) return;
        var params = asset.getGenerationParams();
        if (params == null || params.isBlank()) return;
        // 简单提取 prompt 字段值
        var promptStart = params.indexOf("\"prompt\"");
        if (promptStart < 0) return;
        var valueStart = params.indexOf(":", promptStart);
        if (valueStart < 0) return;
        var quoteStart = params.indexOf("\"", valueStart + 1);
        if (quoteStart < 0) return;
        var quoteEnd = params.indexOf("\"", quoteStart + 1);
        if (quoteEnd < 0) return;
        var prompt = params.substring(quoteStart + 1, quoteEnd);
        // 取前 5 个逗号分隔的词
        var tags =
                Arrays.stream(prompt.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .limit(5)
                        .toList();
        if (!tags.isEmpty()) {
            asset.setTags(String.join(",", tags));
        }
    }

    /** 从素材的 generationParams 中提取 prompt。 */
    private String extractPrompt(MediaAsset asset) {
        var params = asset.getGenerationParams();
        if (params == null) return "regenerate";
        var promptStart = params.indexOf("\"prompt\"");
        if (promptStart < 0) return "regenerate";
        var valueStart = params.indexOf(":", promptStart);
        if (valueStart < 0) return "regenerate";
        var quoteStart = params.indexOf("\"", valueStart + 1);
        if (quoteStart < 0) return "regenerate";
        var quoteEnd = params.indexOf("\"", quoteStart + 1);
        if (quoteEnd < 0) return "regenerate";
        return params.substring(quoteStart + 1, quoteEnd);
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
