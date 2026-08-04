package com.xuejiai.aaf.module.ai.aigc.media.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAsset;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetTag;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetTagRef;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcMedia;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcAssetRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcAssetTagRefRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcAssetTagRepository;
import com.xuejiai.aaf.module.ai.aigc.media.repository.AigcMediaRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetSaveDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagsDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** AIGC 资产管理服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcAssetService
        extends BaseCrudService<
                AigcAsset, AigcAssetVO, Void, AigcAssetUpdateDTO, AigcAssetPageDTO> {

    private final AigcAssetRepository assetRepository;
    private final AigcMediaRepository mediaRepository;
    private final AigcAssetTagRepository tagRepository;
    private final AigcAssetTagRefRepository tagRefRepository;
    private final AigcMediaService mediaService;
    private final AigcAssetTagService tagService;
    private final OperatorContext operatorContext;

    @Override
    protected AigcAssetRepository getRepository() {
        return assetRepository;
    }

    @Override
    protected AigcAssetVO toVO(AigcAsset asset) {
        var media = mediaService.requireOwnedMedia(asset.getMediaId(), asset.getUserId());
        return new AigcAssetVO(
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
    protected AigcAsset toEntity(Void ignored) {
        throw new UnsupportedOperationException("资产不支持标准创建，请使用媒体登记入口");
    }

    @Override
    protected void updateEntity(AigcAsset asset, AigcAssetUpdateDTO request) {
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
    protected Specification<AigcAsset> buildSpec(AigcAssetPageDTO request) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (request.getCategoryId() != null) {
                predicates.add(builder.equal(root.get("categoryId"), request.getCategoryId()));
            }
            if (request.getMediaType() != null
                    || (request.getKeyword() != null && !request.getKeyword().isBlank())) {
                var mediaIds = query.subquery(Long.class);
                var media = mediaIds.from(AigcMedia.class);
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

    /** 将媒体幂等登记为可跨项目复用的资产，不复制文件。 */
    @Transactional
    public AigcAssetVO saveFromMedia(Long mediaId, AigcAssetSaveDTO request) {
        var userId = requireCurrentUserId();
        var media =
                mediaRepository
                        .findLockedByIdAndUserId(mediaId, userId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "媒体不存在或无权访问"));
        if (request != null && request.categoryId() != null) {
            requireAccessibleCategory(request.categoryId(), userId, media.getWorkspaceId());
        }
        var existing = assetRepository.findByMediaId(mediaId);
        if (existing.isPresent()) {
            return toVO(existing.get());
        }

        var asset = new AigcAsset();
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

    public List<AigcAssetTagVO> tags(Long assetId) {
        var asset = requireEntity(assetId);
        var tagIds =
                tagRefRepository.findByAssetIdOrderByTagId(assetId).stream()
                        .filter(reference -> !Boolean.TRUE.equals(reference.getDeleted()))
                        .map(AigcAssetTagRef::getTagId)
                        .toList();
        return tagViews(asset, tagIds);
    }

    @Transactional
    public List<AigcAssetTagVO> replaceTags(Long assetId, AigcAssetTagsDTO command) {
        var asset = requireEntity(assetId);
        var requested = new LinkedHashSet<>(command.tagIds());
        var tags = tagRepository.findAllById(requested);
        if (tags.size() != requested.size()
                || tags.stream().anyMatch(tag -> !tagAccessible(asset, tag))) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "资产标签不存在或无权访问");
        }

        var references =
                new ArrayList<>(tagRefRepository.findByAssetIdOrderByTagId(assetId));
        var byTagId =
                references.stream()
                        .collect(java.util.stream.Collectors.toMap(AigcAssetTagRef::getTagId, value -> value));
        var touched = new LinkedHashSet<Long>();
        for (var reference : references) {
            var active = requested.contains(reference.getTagId());
            reference.setDeleted(!active);
            reference.setDeleteTime(active ? null : java.time.LocalDateTime.now());
            touched.add(reference.getTagId());
        }
        for (var tagId : requested) {
            if (byTagId.containsKey(tagId)) continue;
            var reference = new AigcAssetTagRef();
            reference.setAssetId(assetId);
            reference.setTagId(tagId);
            references.add(reference);
            touched.add(tagId);
        }
        tagRefRepository.saveAllAndFlush(references);
        refreshTagUsageCounts(touched);
        return tagViews(asset, List.copyOf(requested));
    }

    private List<AigcAssetTagVO> tagViews(AigcAsset asset, List<Long> tagIds) {
        if (tagIds.isEmpty()) return List.of();
        var byId =
                tagRepository.findAllById(tagIds).stream()
                        .filter(tag -> tagAccessible(asset, tag))
                        .collect(java.util.stream.Collectors.toMap(AigcAssetTag::getId, value -> value));
        return tagIds.stream().map(byId::get).filter(Objects::nonNull).map(tagService::toVO).toList();
    }

    private boolean tagAccessible(AigcAsset asset, AigcAssetTag tag) {
        return !Boolean.TRUE.equals(tag.getDeleted())
                && Objects.equals(asset.getOrgId(), tag.getOrgId())
                && (tag.getWorkspaceId() == null
                        || Objects.equals(asset.getWorkspaceId(), tag.getWorkspaceId()));
    }

    private void refreshTagUsageCounts(LinkedHashSet<Long> tagIds) {
        for (var tag : tagRepository.findAllById(tagIds)) {
            tag.setUsageCount(Math.toIntExact(tagRefRepository.countByTagIdAndDeletedFalse(tag.getId())));
            tagRepository.save(tag);
        }
    }

    private void requireAccessibleCategory(Long categoryId, Long userId, Long workspaceId) {
        if (assetRepository.countAccessibleCategory(categoryId, userId, workspaceId) == 0) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "资产分类不存在或无权访问");
        }
    }

    private String resolveScope(AigcAssetSaveDTO request) {
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
