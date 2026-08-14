package com.xuejiai.aaf.module.ai.prompt.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementDecision;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplate;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplateCompiler;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplateRepository;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateCopyDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplatePageDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUseDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUseVO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateVO;

import lombok.RequiredArgsConstructor;

/** 统一提示词资产服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromptTemplateAssetService
        extends BaseCrudService<
                PromptTemplate,
                PromptTemplateVO,
                PromptTemplateCreateDTO,
                PromptTemplateUpdateDTO,
                PromptTemplatePageDTO> {

    private static final int DIRECTORY_MAX_PAGE_SIZE = 100;

    private final PromptTemplateRepository repository;
    private final PromptTemplateCompiler compiler;
    private final OperatorContext operatorContext;

    @Override
    protected PromptTemplateRepository getRepository() {
        return repository;
    }

    @Override
    protected PromptTemplateVO toVO(PromptTemplate template) {
        return new PromptTemplateVO(
                template.getId(),
                template.getVersion(),
                template.getType(),
                template.getName(),
                template.getCategory(),
                template.getContent(),
                template.getNegativePrompt(),
                template.getModel(),
                template.getWidth(),
                template.getHeight(),
                template.getSteps(),
                template.getSeed(),
                Objects.equals(template.getVisibility(), PromptTemplate.VISIBILITY_PUBLIC),
                template.getVisibility(),
                template.getUsageCount(),
                template.getScope(),
                template.getDescription(),
                compiler.readDeclarations(
                        template.getContent(),
                        template.getNegativePrompt(),
                        template.getVariables()),
                template.getOwnerId(),
                template.getCreateTime(),
                template.getUpdateTime());
    }

    @Override
    protected PromptTemplate toEntity(PromptTemplateCreateDTO request) {
        var template = new PromptTemplate();
        template.setName(requireText(request.name(), "模板名称不能为空"));
        template.setType(defaultText(request.type(), "IMAGE_GEN"));
        template.setCategory(request.category());
        template.setContent(requireText(request.prompt(), "提示词不能为空"));
        template.setNegativePrompt(request.negativePrompt());
        template.setModel(request.model());
        template.setWidth(request.width());
        template.setHeight(request.height());
        template.setSteps(request.steps());
        template.setSeed(request.seed());
        template.setVisibility(
                Boolean.TRUE.equals(request.isPublic())
                        ? PromptTemplate.VISIBILITY_PUBLIC
                        : PromptTemplate.VISIBILITY_PRIVATE);
        template.setUsageCount(0);
        template.setScope(defaultText(request.scope(), "GENERATION"));
        template.setDescription(request.description());
        template.setTemplateVersion(1);
        template.setActive(true);
        template.setVariables(
                serializeDeclarations(
                        template.getContent(), template.getNegativePrompt(), request.variables()));
        return template;
    }

    @Override
    protected void updateEntity(PromptTemplate template, PromptTemplateUpdateDTO request) {
        if (request.name() != null) {
            template.setName(requireText(request.name(), "模板名称不能为空"));
        }
        if (request.type() != null) {
            template.setType(requireText(request.type(), "模板类型不能为空"));
        }
        if (request.category() != null) {
            template.setCategory(request.category());
        }
        if (request.prompt() != null) {
            template.setContent(requireText(request.prompt(), "提示词不能为空"));
        }
        if (request.negativePrompt() != null) {
            template.setNegativePrompt(request.negativePrompt());
        }
        if (request.model() != null) {
            template.setModel(request.model());
        }
        if (request.width() != null) {
            template.setWidth(request.width());
        }
        if (request.height() != null) {
            template.setHeight(request.height());
        }
        if (request.steps() != null) {
            template.setSteps(request.steps());
        }
        if (request.seed() != null) {
            template.setSeed(request.seed());
        }
        if (request.isPublic() != null) {
            template.setVisibility(
                    request.isPublic()
                            ? PromptTemplate.VISIBILITY_PUBLIC
                            : PromptTemplate.VISIBILITY_PRIVATE);
        }
        if (request.scope() != null) {
            template.setScope(requireText(request.scope(), "使用场景不能为空"));
        }
        if (request.description() != null) {
            template.setDescription(request.description());
        }
        if (request.prompt() != null
                || request.negativePrompt() != null
                || request.variables() != null) {
            template.setVariables(
                    serializeDeclarations(
                            template.getContent(),
                            template.getNegativePrompt(),
                            request.variables()));
        }
    }

    @Override
    protected Map<String, Object> authorizationAttributes(PromptTemplate template) {
        var attributes = new LinkedHashMap<>(super.authorizationAttributes(template));
        attributes.put("visibility", template.getVisibility());
        return Map.copyOf(attributes);
    }

    @Override
    protected org.springframework.data.jpa.domain.Specification<PromptTemplate> buildSpec(
            PromptTemplatePageDTO query) {
        var builder =
                SpecificationBuilder.<PromptTemplate>builder()
                        .eqIfPresent("type", query.getType())
                        .eqIfPresent("scope", query.getScope())
                        .eqIfPresent("category", query.getCategory());
        if (query.getIsPublic() != null) {
            builder.eqIfPresent(
                    "visibility",
                    query.getIsPublic()
                            ? PromptTemplate.VISIBILITY_PUBLIC
                            : PromptTemplate.VISIBILITY_PRIVATE);
        }
        if (Boolean.TRUE.equals(query.getOwnerOnly())) {
            builder.eqIfPresent("ownerId", currentOwnerId());
        }
        return builder.build();
    }

    /** 当前组织/工作区内公开模板。 */
    public PageResult<PromptTemplateVO> pagePublic(PromptTemplatePageDTO query) {
        query.setIsPublic(true);
        query.setOwnerOnly(false);
        return page(query);
    }

    /** 当前创建者的私有或公开模板。 */
    public PageResult<PromptTemplateVO> pageMine(PromptTemplatePageDTO query) {
        query.setIsPublic(null);
        query.setOwnerOnly(true);
        return page(query);
    }

    /** Studio 明确标记为 SYSTEM 的全局系统模板；ENGINE 内部 Prompt 永不进入该目录。 */
    public PageResult<PromptTemplateVO> pageSystem(PromptTemplatePageDTO query) {
        enforce(CrudOperation.PAGE, AccessMode.DEFAULT);
        var spec =
                SpecificationBuilder.<PromptTemplate>builder()
                        .eqIfPresent("visibility", PromptTemplate.VISIBILITY_SYSTEM)
                        .eqIfPresent("type", query.getType())
                        .eqIfPresent("scope", query.getScope())
                        .eqIfPresent("category", query.getCategory())
                        .build();
        var pageNo = Math.max(query.getPageNo(), 1);
        var pageSize = Math.max(1, Math.min(query.getPageSize(), DIRECTORY_MAX_PAGE_SIZE));
        var result =
                repository.findAll(
                        spec,
                        PageRequest.of(pageNo - 1, pageSize, Sort.by("usageCount").descending()));
        return new PageResult<>(
                result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(),
                pageNo,
                pageSize,
                List.of(),
                null,
                null,
                result.hasNext());
    }

    /** 服务端安全编译可见模板，并在 UPDATE 权限和行锁保护下原子记录一次使用。 */
    @Transactional
    public PromptTemplateUseVO use(Long id, PromptTemplateUseDTO request) {
        var decision = enforce(CrudOperation.UPDATE, AccessMode.DEFAULT);
        var template = requireStudioVisible(id, decision, true);
        var variables = request == null ? Map.<String, String>of() : request.variables();
        try {
            var prompt =
                    compiler.compile(
                            template.getContent(),
                            template.getNegativePrompt(),
                            template.getVariables(),
                            variables,
                            false);
            var negativePrompt =
                    compiler.compile(
                            template.getContent(),
                            template.getNegativePrompt(),
                            template.getVariables(),
                            variables,
                            true);
            var usageCount = Math.addExact(template.getUsageCount(), 1);
            if (repository.incrementUsageCount(template.getId()) != 1) {
                throw notFound();
            }
            return new PromptTemplateUseVO(template.getId(), prompt, negativePrompt, usageCount);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
    }

    /** 将可见模板复制为当前创建者在当前组织/工作区内的私有资产。 */
    @Transactional
    public PromptTemplateVO copy(Long id, PromptTemplateCopyDTO request) {
        var decision = enforce(CrudOperation.GET, AccessMode.DEFAULT);
        var source = requireStudioVisible(id, decision, false);
        var name =
                request == null || request.name() == null || request.name().isBlank()
                        ? source.getName() + " 副本"
                        : request.name();
        var createRequest =
                new PromptTemplateCreateDTO(
                        name,
                        source.getType(),
                        source.getCategory(),
                        source.getContent(),
                        source.getNegativePrompt(),
                        source.getModel(),
                        source.getWidth(),
                        source.getHeight(),
                        source.getSteps(),
                        source.getSeed(),
                        false,
                        source.getScope(),
                        source.getDescription(),
                        compiler.readDeclarations(
                                source.getContent(),
                                source.getNegativePrompt(),
                                source.getVariables()));
        return create(createRequest);
    }

    @Override
    protected Long extractOwnerId(PromptTemplate entity) {
        return entity.getOwnerId();
    }

    @Override
    protected void beforeUpdate(PromptTemplate entity, PromptTemplateUpdateDTO request) {
        enforceUserOwned(entity);
    }

    @Override
    protected void beforeDelete(PromptTemplate entity) {
        enforceUserOwned(entity);
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null) {
            return;
        }
        ids.stream().filter(Objects::nonNull).distinct().sorted().forEach(this::delete);
    }

    @Override
    @Transactional
    public void archive(List<Long> ids) {
        deleteBatch(ids);
    }

    private PromptTemplate requireStudioVisible(
            Long id, CrudEnforcementDecision<PromptTemplate> decision, boolean lock) {
        var template =
                (lock ? repository.findByIdForUpdate(id) : repository.findById(id))
                        .filter(candidate -> !Boolean.TRUE.equals(candidate.getDeleted()))
                        .filter(candidate -> isStudioVisible(candidate, decision))
                        .orElseThrow(this::notFound);
        if (Objects.equals(template.getVisibility(), PromptTemplate.VISIBILITY_ENGINE)) {
            throw notFound();
        }
        return template;
    }

    private boolean isStudioVisible(
            PromptTemplate template, CrudEnforcementDecision<PromptTemplate> decision) {
        return switch (template.getVisibility()) {
            case PromptTemplate.VISIBILITY_ENGINE -> false;
            case PromptTemplate.VISIBILITY_SYSTEM -> true;
            case PromptTemplate.VISIBILITY_PRIVATE ->
                    Objects.equals(template.getOwnerId(), decision.subjectId())
                            && isCurrentTenant(template, decision);
            case PromptTemplate.VISIBILITY_PUBLIC -> isCurrentTenant(template, decision);
            default -> false;
        };
    }

    private boolean isCurrentTenant(
            PromptTemplate template, CrudEnforcementDecision<PromptTemplate> decision) {
        return Objects.equals(template.getOrgId(), decision.orgId())
                && (template.getWorkspaceId() == null
                        || Objects.equals(template.getWorkspaceId(), decision.workspaceId()));
    }

    private void enforceUserOwned(PromptTemplate template) {
        if (Objects.equals(template.getVisibility(), PromptTemplate.VISIBILITY_ENGINE)
                || Objects.equals(template.getVisibility(), PromptTemplate.VISIBILITY_SYSTEM)
                || template.getOwnerId() == null) {
            throw notFound();
        }
        enforceOwnership(template);
    }

    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private String serializeDeclarations(
            String content, String negativePrompt, List<String> variables) {
        try {
            return compiler.serializeDeclarations(content, negativePrompt, variables);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private BusinessException notFound() {
        return new BusinessException(GlobalErrorCode.NOT_FOUND, "提示词资产不存在或无权访问");
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }
}
