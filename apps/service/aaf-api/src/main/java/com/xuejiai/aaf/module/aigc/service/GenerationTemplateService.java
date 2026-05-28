package com.xuejiai.aaf.module.aigc.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.aigc.domain.GenerationTemplate;
import com.xuejiai.aaf.module.aigc.repository.GenerationTemplateRepository;
import com.xuejiai.aaf.module.aigc.vo.GenerationTemplateCreateDTO;
import com.xuejiai.aaf.module.aigc.vo.GenerationTemplateVO;

import lombok.RequiredArgsConstructor;

/**
 * 参数模板服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class GenerationTemplateService {

    private final GenerationTemplateRepository templateRepository;

    /**
     * 分页查询用户模板。
     *
     * @param userId 用户 ID
     * @param page 页码
     * @param size 每页数量
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<GenerationTemplateVO> listByUser(Long userId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return templateRepository.findByUserId(userId, pageable).map(this::toVO);
    }

    /**
     * 按分类查询公开模板。
     *
     * @param category 分类名称（可选）
     * @param page 页码
     * @param size 每页数量
     * @return 公开模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<GenerationTemplateVO> listPublic(String category, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "usageCount"));
        if (category != null) {
            return templateRepository.findByCategory(category, pageable).map(this::toVO);
        }
        return templateRepository.findByIsPublicTrue(pageable).map(this::toVO);
    }

    /**
     * 创建模板。
     *
     * @param userId 用户 ID
     * @param dto 创建请求
     * @return 新建的模板
     */
    @Transactional
    public GenerationTemplateVO create(Long userId, GenerationTemplateCreateDTO dto) {
        var template = new GenerationTemplate();
        template.setUserId(userId);
        template.setName(dto.name());
        template.setCategory(dto.category());
        template.setPrompt(dto.prompt());
        template.setNegativePrompt(dto.negativePrompt());
        template.setModel(dto.model());
        template.setWidth(dto.width());
        template.setHeight(dto.height());
        template.setSteps(dto.steps());
        template.setSeed(dto.seed());
        template.setIsPublic(dto.isPublic() != null && dto.isPublic());
        return toVO(templateRepository.save(template));
    }

    /**
     * 使用模板（增加使用计数并返回参数）。
     *
     * @param templateId 模板 ID
     * @return 模板详情
     */
    @Transactional
    public GenerationTemplateVO use(Long templateId) {
        var template = findById(templateId);
        template.incrementUsage();
        return toVO(templateRepository.save(template));
    }

    /**
     * 删除模板。
     *
     * @param templateId 模板 ID
     */
    @Transactional
    public void delete(Long templateId) {
        templateRepository.deleteById(templateId);
    }

    private GenerationTemplate findById(Long id) {
        return templateRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "模板不存在"));
    }

    private GenerationTemplateVO toVO(GenerationTemplate t) {
        return new GenerationTemplateVO(
                t.getId(),
                t.getName(),
                t.getCategory(),
                t.getPrompt(),
                t.getNegativePrompt(),
                t.getModel(),
                t.getWidth(),
                t.getHeight(),
                t.getSteps(),
                t.getSeed(),
                t.getIsPublic(),
                t.getUsageCount());
    }
}
