package com.xuejiai.aaf.module.ai.aigc.template.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.resource.service.UserProjectResourceService;
import com.xuejiai.aaf.module.ai.aigc.project.resource.vo.UserProjectResourceLinkDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectVO;
import com.xuejiai.aaf.module.ai.aigc.template.domain.UserProjectTemplate;
import com.xuejiai.aaf.module.ai.aigc.template.repository.UserProjectTemplateRepository;
import com.xuejiai.aaf.module.ai.aigc.template.vo.UserProjectTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.template.vo.UserProjectTemplateForkDTO;
import com.xuejiai.aaf.module.ai.aigc.template.vo.UserProjectTemplatePageDTO;
import com.xuejiai.aaf.module.ai.aigc.template.vo.UserProjectTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.template.vo.UserProjectTemplateVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 项目模板服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProjectTemplateService
        extends BaseCrudService<
                UserProjectTemplate,
                UserProjectTemplateVO,
                UserProjectTemplateCreateDTO,
                UserProjectTemplateUpdateDTO,
                UserProjectTemplatePageDTO> {

    private final UserProjectTemplateRepository templateRepository;
    private final AigcProjectRepository projectRepository;
    private final UserProjectResourceService resourceService;
    private final OperatorContext operatorContext;

    @Override
    protected JpaRepository<UserProjectTemplate, Long> getRepository() {
        return templateRepository;
    }

    @Override
    protected JpaSpecificationExecutor<UserProjectTemplate> getSpecExecutor() {
        return templateRepository;
    }

    @Override
    protected String entityName() {
        return "项目模板";
    }

    @Override
    protected UserProjectTemplateVO toVO(UserProjectTemplate e) {
        var vo = new UserProjectTemplateVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setCoverUrl(e.getCoverUrl());
        vo.setCategory(e.getCategory());
        vo.setProjectType(e.getProjectType());
        vo.setTemplateConfig(e.getTemplateConfig());
        vo.setIsOfficial(e.getIsOfficial());
        vo.setUsageCount(e.getUsageCount());
        vo.setSortOrder(e.getSortOrder());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    @Override
    protected UserProjectTemplate toEntity(UserProjectTemplateCreateDTO dto) {
        var e = new UserProjectTemplate();
        e.setCode(dto.code());
        e.setName(dto.name());
        e.setDescription(dto.description());
        e.setCoverUrl(dto.coverUrl());
        e.setCategory(dto.category());
        e.setProjectType(dto.projectType());
        if (dto.isOfficial() != null) e.setIsOfficial(dto.isOfficial());
        if (dto.sortOrder() != null) e.setSortOrder(dto.sortOrder());
        return e;
    }

    @Override
    protected void updateEntity(UserProjectTemplate e, UserProjectTemplateUpdateDTO dto) {
        if (dto.name() != null) e.setName(dto.name());
        if (dto.description() != null) e.setDescription(dto.description());
        if (dto.coverUrl() != null) e.setCoverUrl(dto.coverUrl());
        if (dto.category() != null) e.setCategory(dto.category());
        if (dto.projectType() != null) e.setProjectType(dto.projectType());
        if (dto.isOfficial() != null) e.setIsOfficial(dto.isOfficial());
        if (dto.sortOrder() != null) e.setSortOrder(dto.sortOrder());
    }

    @Override
    protected Specification<UserProjectTemplate> buildSpec(UserProjectTemplatePageDTO p) {
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

    /**
     * Fork 模板创建新项目。
     *
     * <p>同步事务：usage_count++，创建项目，写资源关联。
     */
    @Transactional
    public AigcProjectVO fork(Long templateId, UserProjectTemplateForkDTO dto) {
        var template =
                templateRepository
                        .findById(templateId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "模板不存在"));

        // 异步原子更新 usage_count（同步事务内直接+1 保持简单）
        template.incrementUsage();
        templateRepository.save(template);

        Long userId = operatorContext.currentUserId().orElseThrow();
        var config = template.getTemplateConfig();

        // 创建项目
        var project = new AigcProject();
        project.setUserId(userId);
        project.setType(template.getProjectType());
        project.setStatus("DRAFT");
        project.setName(dto != null && dto.name() != null ? dto.name() : template.getName());
        if (dto != null && dto.description() != null) {
            project.setDescription(dto.description());
        } else {
            project.setDescription(template.getDescription());
        }
        if (config != null && config.containsKey("prompt")) {
            project.setPrompt(String.valueOf(config.get("prompt")));
        }
        var savedProject = projectRepository.save(project);

        // 写资源关联（按 templateConfig 中 defaultPersonaId/defaultKbIds）
        if (config != null) {
            var links = buildResourceLinks(config);
            if (!links.isEmpty()) {
                resourceService.linkBatch(savedProject.getId(), links);
            }
        }

        var vo = new AigcProjectVO();
        vo.setId(savedProject.getId());
        vo.setName(savedProject.getName());
        vo.setType(savedProject.getType());
        vo.setStatus(savedProject.getStatus());
        vo.setUserId(savedProject.getUserId());
        vo.setPrompt(savedProject.getPrompt());
        vo.setCreateTime(savedProject.getCreateTime());
        vo.setUpdateTime(savedProject.getUpdateTime());
        return vo;
    }

    @SuppressWarnings("unchecked")
    private List<UserProjectResourceLinkDTO> buildResourceLinks(Map<String, Object> config) {
        var links = new ArrayList<UserProjectResourceLinkDTO>();
        // defaultPersonaId → ASSISTANT 关联
        if (config.get("defaultPersonaId") instanceof Number personaId) {
            links.add(
                    new UserProjectResourceLinkDTO(
                            "ASSISTANT", personaId.longValue(), "DEFAULT_ASSISTANT", 0));
        }
        // defaultKbIds → KNOWLEDGE_BASE 关联
        if (config.get("defaultKbIds") instanceof List<?> kbIds) {
            int order = 0;
            for (var kbId : kbIds) {
                if (kbId instanceof Number num) {
                    links.add(
                            new UserProjectResourceLinkDTO(
                                    "KNOWLEDGE_BASE", num.longValue(), "REF", order++));
                }
            }
        }
        return links;
    }
}
