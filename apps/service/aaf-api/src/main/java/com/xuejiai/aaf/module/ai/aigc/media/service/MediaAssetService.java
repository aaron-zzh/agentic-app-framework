package com.xuejiai.aaf.module.ai.aigc.media.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAsset;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAssetGroup;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAssetVariant;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaAssetGroupRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaAssetRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaAssetVariantRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaAssetCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaAssetUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaAssetVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.RegenerateRequest;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;
import com.xuejiai.aaf.module.system.file.service.FileRecordService;

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
    private final MediaAssetGroupRepository groupRepository;
    private final AiServiceRegistry aiServiceRegistry;
    private final CapabilityRouter capabilityRouter;
    private final FileService fileService;
    private final FileRecordService fileRecordService;

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
            Long userId, MediaAssetType type, Long categoryId, Long projectId, Pageable pageable) {
        Page<MediaAsset> page;
        if (projectId != null) {
            page = assetRepository.findByUserIdAndProjectId(userId, projectId, pageable);
        } else if (type != null) {
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
        asset.setGroupId(dto.groupId());
        asset.setAiGenerated(Boolean.TRUE.equals(dto.aiGenerated()));
        asset.setModelName(dto.modelName());
        asset.setProviderCode(dto.providerCode());
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

    /** 移动素材到指定分组。 */
    @Transactional
    public void moveToGroup(Long assetId, Long groupId) {
        var asset = findById(assetId);
        asset.setGroupId(groupId);
        assetRepository.save(asset);
    }

    /**
     * 删除素材组及组内所有素材和文件。
     *
     * @param groupId 素材组 ID
     */
    @Transactional
    public void deleteGroup(Long groupId) {
        var assets = assetRepository.findByGroupId(groupId);
        for (var asset : assets) {
            deleteFileQuietly(asset.getUrl());
            deleteFileQuietly(asset.getThumbnailUrl());
        }
        assetRepository.deleteAll(assets);
        groupRepository.deleteById(groupId);
    }

    /**
     * 删除素材及其关联文件。
     *
     * @param id 素材 ID
     */
    @Transactional
    public void delete(Long id) {
        var asset = findById(id);
        // 删除 OSS/本地文件（从 URL 提取 key，忽略外部 URL）
        deleteFileQuietly(asset.getUrl());
        deleteFileQuietly(asset.getThumbnailUrl());
        assetRepository.delete(asset);
    }

    /** 静默删除文件（物理存储 + sys_file 记录），失败不影响主流程 */
    private void deleteFileQuietly(String url) {
        if (url == null || url.isBlank()) return;
        try {
            var key = url.replaceFirst("^https?://[^/]+/", "");
            fileService.delete(key);
            fileRecordService.deleteByKey(key);
        } catch (Exception e) {
            log.warn("删除素材文件失败，忽略: url={}, err={}", url, e.getMessage());
        }
    }

    /**
     * 从生成结果一键保存到素材库。
     *
     * @param userId 用户 ID
     * @param dto 保存请求
     * @return 保存的素材
     */
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            noRollbackFor = Exception.class)
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

        // groupId 优先使用传入值；若无则按 groupName 自动建组，保证 group+asset 在同一事务
        Long resolvedGroupId = dto.groupId();
        if (resolvedGroupId == null && dto.groupName() != null && !dto.groupName().isBlank()) {
            var group = new MediaAssetGroup();
            group.setName(dto.groupName());
            group.setCoverUrl(dto.url());
            group.setAssetCount(1);
            group.setUserId(userId);
            group = groupRepository.save(group);
            resolvedGroupId = group.getId();
        }
        if (resolvedGroupId != null) asset.setGroupId(resolvedGroupId);

        asset.setAiGenerated(Boolean.TRUE.equals(dto.aiGenerated()));
        asset.setModelName(dto.modelName());
        asset.setProviderCode(dto.providerCode());
        if (dto.projectId() != null) asset.setProjectId(dto.projectId());
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
        var prompt = request.newPrompt() != null ? request.newPrompt() : extractPrompt(original);
        // 走模型决策链：优先用请求中指定的 modelId，否则沿用原素材的 modelName（通常即 modelId）
        var explicitModelId =
                request.modelId() != null ? request.modelId() : original.getModelName();
        var model =
                capabilityRouter.resolve(
                        CapabilityRoutingContext.of(
                                userId, CapabilityRoutingContext.CAP_IMAGE_GEN, explicitModelId));
        var result =
                aiServiceRegistry
                        .get(ImageGenerationService.class, model)
                        .generate(
                                model,
                                new ImageRequest(
                                        prompt,
                                        model.getModelId(),
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
        variant = assetRepository.save(variant);

        // 创建变体关联
        var relation = new MediaAssetVariant();
        relation.setOriginalAssetId(original.getId());
        relation.setVariantAssetId(variant.getId());
        relation.setParamsDiff(JsonUtils.toJsonString(java.util.Map.of("newPrompt", prompt)));
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
        String groupName = null;
        if (asset.getGroupId() != null) {
            groupName =
                    groupRepository.findById(asset.getGroupId()).map(g -> g.getName()).orElse(null);
        }
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
                asset.getGroupId(),
                groupName,
                asset.isAiGenerated(),
                asset.getModelName(),
                asset.getProviderCode(),
                asset.getUserId(),
                asset.getVersion(),
                asset.getCreateTime(),
                asset.getUpdateTime());
    }
}
