package com.xuejiai.aaf.module.ai.aigc.media.service;

import java.util.ArrayList;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.domain.Asset;
import com.xuejiai.aaf.module.ai.aigc.media.domain.Media;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AssetRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AssetPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AssetSaveDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AssetUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AssetVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** AIGC 资产管理服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService
        extends BaseCrudService<Asset, AssetVO, Void, AssetUpdateDTO, AssetPageDTO> {

    private final AssetRepository assetRepository;
    private final MediaRepository mediaRepository;
    private final MediaService mediaService;
    private final OperatorContext operatorContext;

    @Override
    protected AssetRepository getRepository() {
        return assetRepository;
    }

    @Override
    protected AssetVO toVO(Asset asset) {
        var media = mediaService.requireOwnedMedia(asset.getMediaId(), asset.getUserId());
        return new AssetVO(
                asset.getId(),
                asset.getMediaId(),
                asset.getCategoryId(),
                asset.getScope(),
                asset.getCopyrightInfo(),
                asset.getStatus(),
                asset.getUsageCount(),
                mediaService.toVO(media),
                asset.getCreateTime());
    }

    @Override
    protected Asset toEntity(Void ignored) {
        throw new UnsupportedOperationException("Asset 不支持标准创建，请使用 saveFromMedia");
    }

    @Override
    protected void updateEntity(Asset asset, AssetUpdateDTO request) {
        if (request.categoryId() != null) {
            requireAccessibleCategory(
                    request.categoryId(), asset.getUserId(), asset.getWorkspaceId());
            asset.setCategoryId(request.categoryId());
        }
        if (request.scope() != null && !request.scope().isBlank()) {
            asset.setScope(request.scope());
        }
        if (request.copyrightInfo() != null) {
            asset.setCopyrightInfo(request.copyrightInfo());
        }
    }

    @Override
    protected Specification<Asset> buildSpec(AssetPageDTO request) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (request.getCategoryId() != null) {
                predicates.add(builder.equal(root.get("categoryId"), request.getCategoryId()));
            }
            if (request.getMediaType() != null
                    || (request.getKeyword() != null && !request.getKeyword().isBlank())) {
                var mediaIds = query.subquery(Long.class);
                var media = mediaIds.from(Media.class);
                var mediaPredicates = new ArrayList<Predicate>();
                mediaPredicates.add(builder.equal(media.get("id"), root.get("mediaId")));
                mediaPredicates.add(builder.equal(media.get("userId"), root.get("userId")));
                if (request.getMediaType() != null) {
                    mediaPredicates.add(
                            builder.equal(media.get("mediaType"), request.getMediaType()));
                }
                if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                    mediaPredicates.add(
                            builder.like(
                                    builder.lower(media.get("name")),
                                    "%" + request.getKeyword().toLowerCase() + "%"));
                }
                mediaIds.select(media.get("id"));
                mediaIds.where(mediaPredicates.toArray(Predicate[]::new));
                predicates.add(builder.exists(mediaIds));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** 将 Media 幂等登记为可跨项目复用的 Asset，不复制文件。 */
    @Transactional
    public AssetVO saveFromMedia(Long mediaId, AssetSaveDTO request) {
        var userId = requireCurrentUserId();
        var media =
                mediaRepository
                        .findLockedByIdAndUserId(mediaId, userId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND,
                                                "媒体不存在或无权访问"));
        if (request != null && request.categoryId() != null) {
            requireAccessibleCategory(request.categoryId(), userId, media.getWorkspaceId());
        }
        var existing = assetRepository.findByMediaId(mediaId);
        if (existing.isPresent()) {
            return toVO(existing.get());
        }

        var asset = new Asset();
        asset.setMediaId(mediaId);
        asset.setCategoryId(request != null ? request.categoryId() : null);
        asset.setScope(resolveScope(request));
        asset.setCopyrightInfo(request != null ? request.copyrightInfo() : null);
        asset.setStatus("ACTIVE");
        asset.setUsageCount(0);
        asset.setUserId(userId);
        asset.setOwnerId(userId);
        asset.setOrgId(media.getOrgId());
        asset.setWorkspaceId(media.getWorkspaceId());
        return toVO(assetRepository.save(asset));
    }

    private void requireAccessibleCategory(Long categoryId, Long userId, Long workspaceId) {
        if (assetRepository.countAccessibleCategory(categoryId, userId, workspaceId) == 0) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "资产分类不存在或无权访问");
        }
    }

    private String resolveScope(AssetSaveDTO request) {
        if (request == null || request.scope() == null || request.scope().isBlank()) {
            return "WORKSPACE";
        }
        return request.scope();
    }

    private Long requireCurrentUserId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }
}
