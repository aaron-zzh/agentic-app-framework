package com.xuejiai.aaf.module.aigc.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.aigc.domain.MediaCategory;
import com.xuejiai.aaf.module.aigc.repository.MediaCategoryRepository;
import com.xuejiai.aaf.module.aigc.vo.MediaCategoryCreateDTO;
import com.xuejiai.aaf.module.aigc.vo.MediaCategoryVO;

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

    /**
     * 获取分类树。
     *
     * @return 树形分类列表
     */
    @Transactional(readOnly = true)
    public List<MediaCategoryVO> tree() {
        var all = categoryRepository.findAllByOrderBySortOrder();
        Map<Long, List<MediaCategory>> grouped =
                all.stream()
                        .collect(
                                Collectors.groupingBy(
                                        c -> c.getParentId() != null ? c.getParentId() : 0L));
        return buildTree(grouped, 0L);
    }

    /**
     * 创建分类。
     *
     * @param dto 创建请求
     * @return 新建的分类
     */
    @Transactional
    public MediaCategoryVO create(MediaCategoryCreateDTO dto) {
        var category = new MediaCategory();
        category.setName(dto.name());
        category.setParentId(dto.parentId());
        category.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0);
        category = categoryRepository.save(category);
        return toVO(category);
    }

    /**
     * 更新分类。
     *
     * @param id 分类 ID
     * @param dto 更新请求
     * @return 更新后的分类
     */
    @Transactional
    public MediaCategoryVO update(Long id, MediaCategoryCreateDTO dto) {
        var category = findById(id);
        category.setName(dto.name());
        if (dto.parentId() != null) category.setParentId(dto.parentId());
        if (dto.sortOrder() != null) category.setSortOrder(dto.sortOrder());
        return toVO(categoryRepository.save(category));
    }

    /**
     * 删除分类。
     *
     * @param id 分类 ID
     */
    @Transactional
    public void delete(Long id) {
        var category = findById(id);
        categoryRepository.delete(category);
    }

    private MediaCategory findById(Long id) {
        return categoryRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "分类不存在"));
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
