package com.xuejiai.aaf.module.ai.aigc.image.service;

import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.image.domain.GenerationTemplate;
import com.xuejiai.aaf.module.ai.aigc.image.repository.GenerationTemplateRepository;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplatePageDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplateVO;

import lombok.RequiredArgsConstructor;

/**
 * 参数模板 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenerationTemplateService
        extends BaseCrudService<
                GenerationTemplate,
                GenerationTemplateVO,
                GenerationTemplateCreateDTO,
                GenerationTemplateUpdateDTO,
                GenerationTemplatePageDTO> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "name",
                    "type",
                    "category",
                    "model",
                    "width",
                    "height",
                    "steps",
                    "isPublic",
                    "usageCount",
                    "scope",
                    "createTime",
                    "updateTime");

    private final GenerationTemplateRepository templateRepository;
    private final OperatorContext operatorContext;

    @Override
    protected GenerationTemplateRepository getRepository() {
        return templateRepository;
    }

    @Override
    protected GenerationTemplateVO toVO(GenerationTemplate t) {
        return new GenerationTemplateVO(
                t.getId(),
                t.getType(),
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
                t.getUsageCount(),
                t.getScope());
    }

    @Override
    protected GenerationTemplate toEntity(GenerationTemplateCreateDTO dto) {
        var entity = new GenerationTemplate();
        entity.setName(dto.name());
        entity.setCategory(dto.category());
        entity.setPrompt(dto.prompt());
        entity.setNegativePrompt(dto.negativePrompt());
        entity.setModel(dto.model());
        entity.setWidth(dto.width());
        entity.setHeight(dto.height());
        entity.setSteps(dto.steps());
        entity.setSeed(dto.seed());
        entity.setIsPublic(dto.isPublic() != null && dto.isPublic());
        entity.setUserId(operatorContext.currentUserId().orElseThrow());
        return entity;
    }

    @Override
    protected void updateEntity(GenerationTemplate entity, GenerationTemplateUpdateDTO dto) {
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.category() != null) entity.setCategory(dto.category());
        if (dto.prompt() != null) entity.setPrompt(dto.prompt());
        if (dto.negativePrompt() != null) entity.setNegativePrompt(dto.negativePrompt());
        if (dto.model() != null) entity.setModel(dto.model());
        if (dto.width() != null) entity.setWidth(dto.width());
        if (dto.height() != null) entity.setHeight(dto.height());
        if (dto.steps() != null) entity.setSteps(dto.steps());
        if (dto.seed() != null) entity.setSeed(dto.seed());
        if (dto.isPublic() != null) entity.setIsPublic(dto.isPublic());
        if (dto.scope() != null) entity.setScope(dto.scope());
    }

    @Override
    protected org.springframework.data.jpa.domain.Specification<GenerationTemplate> buildSpec(
            GenerationTemplatePageDTO query) {
        return SpecificationBuilder.<GenerationTemplate>builder()
                .eqIfPresent("type", query.getType())
                .eqIfPresent("scope", query.getScope())
                .eqIfPresent("category", query.getCategory())
                .eqIfPresent("isPublic", query.getIsPublic())
                .build();
    }

    /** 查询公开模板，始终强制 {@code isPublic=true}。 */
    public PageResult<GenerationTemplateVO> pagePublic(GenerationTemplatePageDTO query) {
        var spec =
                buildSpec(query)
                        .and((root, ignored, cb) -> cb.isTrue(root.get("isPublic")));
        var pageReq =
                org.springframework.data.domain.PageRequest.of(
                        Math.max(query.getPageNo() - 1, 0),
                        query.getPageSize() > 0 ? query.getPageSize() : 100,
                        org.springframework.data.domain.Sort.by("usageCount").descending());
        var page = templateRepository.findAll(spec, pageReq);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /** 按用户分页查询（/me 端点强制 userId 过滤）。 */
    public PageResult<GenerationTemplateVO> pageByUser(
            Long userId, GenerationTemplatePageDTO query) {
        var spec = buildSpec(query).and((root, ignored, cb) -> cb.equal(root.get("userId"), userId));
        var pageReq =
                org.springframework.data.domain.PageRequest.of(
                        Math.max(query.getPageNo() - 1, 0),
                        query.getPageSize() > 0 ? query.getPageSize() : 20,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "updateTime"));
        var page = templateRepository.findAll(spec, pageReq);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /** 增加公开模板或当前用户自有模板的使用计数。 */
    @Transactional
    public GenerationTemplateVO incrementUsage(Long id) {
        var template =
                templateRepository
                        .findById(id)
                        .orElseThrow(GenerationTemplateService::templateNotFound);
        if (!Boolean.TRUE.equals(template.getIsPublic())) {
            var currentUserId = operatorContext.currentUserId().orElse(null);
            if (!Objects.equals(template.getUserId(), currentUserId)) {
                throw templateNotFound();
            }
        }
        template.incrementUsage();
        return toVO(templateRepository.save(template));
    }

    private static BusinessException templateNotFound() {
        return new BusinessException(GlobalErrorCode.NOT_FOUND, "模板不存在");
    }
}
