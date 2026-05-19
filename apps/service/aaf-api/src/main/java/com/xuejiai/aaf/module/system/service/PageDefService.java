package com.xuejiai.aaf.module.system.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.domain.PageDef;
import com.xuejiai.aaf.module.system.repository.PageDefRepository;
import com.xuejiai.aaf.module.system.vo.PageDefCreateDTO;
import com.xuejiai.aaf.module.system.vo.PageDefVO;

import lombok.RequiredArgsConstructor;

/** 页面定义业务逻辑（CRUD + 发布/回滚）。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PageDefService {

    private final PageDefRepository pageDefRepository;

    /** 查询全量页面定义 */
    public List<PageDefVO> listAll() {
        return pageDefRepository.findAllByDeletedFalse().stream().map(this::toVO).toList();
    }

    /** 查询单个页面定义 */
    public PageDefVO getById(Long id) {
        return toVO(findById(id));
    }

    /** 根据 slug 获取已发布的页面定义 */
    public PageDefVO getPublishedBySlug(String slug) {
        return pageDefRepository
                .findBySlugAndStatusAndDeletedFalse(slug, "published")
                .map(this::toVO)
                .orElseThrow(
                        () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "页面未找到: " + slug));
    }

    /** 创建页面定义 */
    @Transactional
    public PageDefVO create(PageDefCreateDTO dto) {
        if (pageDefRepository.existsBySlugAndDeletedFalse(dto.slug())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "slug 已存在: " + dto.slug());
        }
        var entity = new PageDef();
        entity.setSlug(dto.slug());
        entity.setTitle(dto.title());
        entity.setConfig(dto.config());
        entity.setStatus("draft");
        pageDefRepository.save(entity);
        return toVO(entity);
    }

    /** 更新页面定义 */
    @Transactional
    public PageDefVO update(Long id, PageDefCreateDTO dto) {
        var entity = findById(id);
        entity.setSlug(dto.slug());
        entity.setTitle(dto.title());
        entity.setConfig(dto.config());
        pageDefRepository.save(entity);
        return toVO(entity);
    }

    /** 发布页面定义 */
    @Transactional
    public PageDefVO publish(Long id) {
        var entity = findById(id);
        entity.setStatus("published");
        entity.setPublishedAt(LocalDateTime.now());
        pageDefRepository.save(entity);
        return toVO(entity);
    }

    /** 回滚（取消发布，恢复为草稿） */
    @Transactional
    public PageDefVO rollback(Long id) {
        var entity = findById(id);
        entity.setStatus("draft");
        pageDefRepository.save(entity);
        return toVO(entity);
    }

    /** 删除页面定义 */
    @Transactional
    public void delete(Long id) {
        var entity = findById(id);
        pageDefRepository.delete(entity);
    }

    private PageDef findById(Long id) {
        return pageDefRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(
                        () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "页面定义不存在: " + id));
    }

    private PageDefVO toVO(PageDef entity) {
        return new PageDefVO(
                entity.getId(),
                entity.getSlug(),
                entity.getTitle(),
                entity.getConfig(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getPublishedAt(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
