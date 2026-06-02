package com.xuejiai.aaf.module.ai.aigc.video.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.video.domain.VideoTemplate;
import com.xuejiai.aaf.module.ai.aigc.video.repository.VideoTemplateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频模板服务。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTemplateService {

    private final VideoTemplateRepository templateRepository;

    /**
     * 分页查询视频模板。
     *
     * @param type 模板类型（可选）
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<VideoTemplate> page(String type, Pageable pageable) {
        if (type != null) {
            return templateRepository.findByType(type, pageable);
        }
        return templateRepository.findAll(pageable);
    }

    /**
     * 获取模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     */
    @Transactional(readOnly = true)
    public VideoTemplate getById(Long id) {
        return templateRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "视频模板不存在"));
    }

    /**
     * 创建视频模板。
     *
     * @param template 模板实体
     * @return 保存后的模板
     */
    @Transactional
    public VideoTemplate create(VideoTemplate template) {
        return templateRepository.save(template);
    }

    /**
     * 删除视频模板（软删除）。
     *
     * @param id 模板 ID
     */
    @Transactional
    public void delete(Long id) {
        var template = getById(id);
        templateRepository.delete(template);
    }
}
