package com.xuejiai.aaf.module.ai.aigc.workflow.service;

import java.util.ArrayList;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.module.ai.aigc.workflow.domain.UserWorkflowTemplate;
import com.xuejiai.aaf.module.ai.aigc.workflow.repository.UserWorkflowTemplateRepository;
import com.xuejiai.aaf.module.ai.aigc.workflow.vo.UserWorkflowTemplatePageDTO;
import com.xuejiai.aaf.module.ai.aigc.workflow.vo.UserWorkflowTemplateVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 用户工作流模板服务（v0.2.1 P1）——只读 + run 计数。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserWorkflowTemplateService
        extends ReadonlyCrudService<
                UserWorkflowTemplate, UserWorkflowTemplateVO, UserWorkflowTemplatePageDTO> {

    private final UserWorkflowTemplateRepository templateRepository;

    @Override
    protected JpaRepository<UserWorkflowTemplate, Long> getRepository() {
        return templateRepository;
    }

    @Override
    protected JpaSpecificationExecutor<UserWorkflowTemplate> getSpecExecutor() {
        return templateRepository;
    }

    @Override
    protected String entityName() {
        return "工作流模板";
    }

    @Override
    protected UserWorkflowTemplateVO toVO(UserWorkflowTemplate e) {
        var vo = new UserWorkflowTemplateVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setCoverUrl(e.getCoverUrl());
        vo.setCategory(e.getCategory());
        vo.setTemplateConfig(e.getTemplateConfig());
        vo.setIsOfficial(e.getIsOfficial());
        vo.setUsageCount(e.getUsageCount());
        vo.setSortOrder(e.getSortOrder());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    @Override
    protected Specification<UserWorkflowTemplate> buildSpec(UserWorkflowTemplatePageDTO p) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (p.getCategory() != null)
                predicates.add(cb.equal(root.get("category"), p.getCategory()));
            if (p.getIsOfficial() != null)
                predicates.add(cb.equal(root.get("isOfficial"), p.getIsOfficial()));
            if (p.getKeyword() != null && !p.getKeyword().isBlank())
                predicates.add(cb.like(root.get("name"), "%" + p.getKeyword() + "%"));
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 用户调用模板时增加计数（前端串行执行前调用一次）。 */
    @Transactional
    public void incrementRunCount(Long templateId) {
        var template =
                templateRepository
                        .findById(templateId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "模板不存在"));
        template.incrementUsage();
        templateRepository.save(template);
    }
}
