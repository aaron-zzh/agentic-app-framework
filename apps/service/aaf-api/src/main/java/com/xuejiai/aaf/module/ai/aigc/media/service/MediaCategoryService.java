package com.xuejiai.aaf.module.ai.aigc.media.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaCategory;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaCategoryRepository;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaCategoryCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaCategoryVO;

import lombok.RequiredArgsConstructor;

/**
 * 素材分类服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class MediaCategoryService {

    private final MediaCategoryRepository categoryRepository;
    private final OperatorContext operatorContext;

    @Transactional(readOnly = true)
    public List<MediaCategoryVO> tree() {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var all = categoryRepository.findByOwnerIdOrderBySortOrder(userId);
        Map<Long, List<MediaCategory>> grouped =
                all.stream()
                        .collect(
                                Collectors.groupingBy(
                                        c -> c.getParentId() != null ? c.getParentId() : 0L));
        return buildTree(grouped, 0L);
    }

    @Transactional
    public MediaCategoryVO create(MediaCategoryCreateDTO dto) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var category = new MediaCategory();
        category.setName(dto.name());
        category.setParentId(dto.parentId());
        category.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0);
        category.setOwnerId(userId);
        category = categoryRepository.save(category);
        return toVO(category);
    }

    @Transactional
    public MediaCategoryVO update(Long id, MediaCategoryCreateDTO dto) {
        var category = findByIdForCurrentUser(id);
        category.setName(dto.name());
        if (dto.parentId() != null) category.setParentId(dto.parentId());
        if (dto.sortOrder() != null) category.setSortOrder(dto.sortOrder());
        return toVO(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.delete(findByIdForCurrentUser(id));
    }

    private MediaCategory findByIdForCurrentUser(Long id) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var category =
                categoryRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "分类不存在"));
        if (!userId.equals(category.getOwnerId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权操作此分类");
        }
        return category;
    }

    private List<MediaCategoryVO> buildTree(Map<Long, List<MediaCategory>> grouped, Long parentId) {
        var children = grouped.getOrDefault(parentId, List.of());
        return children.stream()
                .map(
                        c ->
                                new MediaCategoryVO(
                                        c.getId(),
                                        c.getName(),
                                        c.getParentId(),
                                        c.getSortOrder(),
                                        buildTree(grouped, c.getId())))
                .toList();
    }

    private MediaCategoryVO toVO(MediaCategory c) {
        return new MediaCategoryVO(
                c.getId(), c.getName(), c.getParentId(), c.getSortOrder(), List.of());
    }
}
