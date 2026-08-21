package com.xuejiai.aaf.module.ai.prompt.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementDecision;
import com.xuejiai.aaf.framework.crud.filter.CrudFilter;
import com.xuejiai.aaf.framework.engine.prompt.PromptCategoryRepository;
import com.xuejiai.aaf.framework.engine.prompt.PromptKind;
import com.xuejiai.aaf.framework.engine.prompt.PromptPublicationService;
import com.xuejiai.aaf.framework.engine.prompt.PromptScope;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplate;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplateCompiler;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplateRepository;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplateVersion;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplateVersionRepository;
import com.xuejiai.aaf.framework.engine.prompt.PromptType;
import com.xuejiai.aaf.framework.engine.prompt.PromptVersionStatus;
import com.xuejiai.aaf.framework.engine.prompt.PromptVisibility;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptDraftDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateCopyDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplatePageDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUseDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUseVO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateVO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptVersionVO;

import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;

/** Studio Prompt 资产服务：根元数据可编辑，内容和生成参数只以新版本发布。 */
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

    public static final String COMMAND_STUDIO_VERSIONED_UPDATE = "PROMPT_STUDIO_VERSIONED_UPDATE";
    public static final String COMMAND_GOVERNANCE_DRAFT_UPDATE = "PROMPT_GOVERNANCE_DRAFT_UPDATE";
    public static final String COMMAND_GOVERNANCE_DRAFT_CREATE = "PROMPT_GOVERNANCE_DRAFT_CREATE";
    public static final String COMMAND_GOVERNANCE_PUBLISH = "PROMPT_GOVERNANCE_PUBLISH";

    private static final int DIRECTORY_MAX_PAGE_SIZE = 100;
    private static final Set<String> ROOT_UPDATE_FIELDS =
            Set.of("name", "type", "categories", "coverUrl", "isPublic", "scope", "description");
    private static final Set<String> VERSION_UPDATE_FIELDS =
            Set.of(
                    "prompt",
                    "negativePrompt",
                    "model",
                    "width",
                    "height",
                    "steps",
                    "seed",
                    "variables",
                    "changeSummary");

    private final PromptTemplateRepository repository;
    private final PromptTemplateVersionRepository versionRepository;
    private final PromptCategoryRepository categoryRepository;
    private final PromptTemplateCompiler compiler;
    private final PromptPublicationService publicationService;
    private final OperatorContext operatorContext;

    @Override
    protected PromptTemplateRepository getRepository() {
        return repository;
    }

    @Override
    protected PromptTemplateVO toVO(PromptTemplate template) {
        if (template.getKind() != PromptKind.TEMPLATE) {
            throw notFound();
        }
        var current = requireCurrentVersion(template);
        return new PromptTemplateVO(
                template.getId(),
                template.getCode(),
                current.getTemplateVersion(),
                template.getKind().name(),
                template.getType().name(),
                template.getName(),
                template.getCategories().stream()
                        .map(category -> category.getCode())
                        .sorted()
                        .toList(),
                template.getCoverUrl(),
                current.getContent(),
                current.getNegativePrompt(),
                current.getModel(),
                current.getWidth(),
                current.getHeight(),
                current.getSteps(),
                current.getSeed(),
                template.getVisibility() == PromptVisibility.PUBLIC,
                template.getVisibility().name(),
                template.getUsageCount(),
                template.getScope().name(),
                template.getDescription(),
                compiler.readDeclarations(
                        current.getContent(), current.getNegativePrompt(), current.getVariables()),
                null,
                null,
                null,
                template.getOwnerId(),
                template.getCreateTime(),
                template.getUpdateTime());
    }

    @Override
    protected PromptTemplate toEntity(PromptTemplateCreateDTO request) {
        var template = new PromptTemplate();
        template.setCode("studio:" + UUID.randomUUID());
        template.setName(requireText(request.name(), "模板名称不能为空"));
        template.setKind(PromptKind.TEMPLATE);
        template.setType(parseType(request.type(), PromptType.IMAGE_GEN));
        template.setCategories(resolveCategories(request.categories()));
        template.setCoverUrl(request.coverUrl());
        template.setVisibility(
                Boolean.TRUE.equals(request.isPublic())
                        ? PromptVisibility.PUBLIC
                        : PromptVisibility.PRIVATE);
        template.setUsageCount(0);
        template.setScope(parseScope(request.scope(), PromptScope.GENERATION));
        template.setDescription(request.description());
        return template;
    }

    @Override
    protected void afterCreate(PromptTemplate template, PromptTemplateCreateDTO request) {
        var draft =
                newVersion(
                        template,
                        1,
                        requireText(request.prompt(), "提示词不能为空"),
                        request.negativePrompt(),
                        request.model(),
                        request.width(),
                        request.height(),
                        request.steps(),
                        request.seed(),
                        request.variables(),
                        "创建初始版本");
        versionRepository.save(draft);
        publicationService.publishDraft(template.getCode(), draft.getTemplateVersion());
    }

    @Override
    protected void updateEntity(PromptTemplate template, PromptTemplateUpdateDTO request) {
        if (hasVersionChange(request)) {
            throw badRequest("Prompt 内容必须通过版本化更新入口修改");
        }
        applyRootUpdate(template, request);
    }

    private void applyRootUpdate(PromptTemplate template, PromptTemplateUpdateDTO request) {
        if (request.name() != null) {
            template.setName(requireText(request.name(), "模板名称不能为空"));
        }
        if (request.type() != null) {
            template.setType(parseType(request.type(), null));
        }
        if (request.categories() != null) {
            template.setCategories(resolveCategories(request.categories()));
        }
        if (!request.coverUrl().isAbsent()) {
            template.setCoverUrl(request.coverUrl().valueOrNull());
        }
        if (request.isPublic() != null) {
            template.setVisibility(
                    request.isPublic() ? PromptVisibility.PUBLIC : PromptVisibility.PRIVATE);
        }
        if (request.scope() != null) {
            template.setScope(parseScope(request.scope(), null));
        }
        if (request.description() != null) {
            template.setDescription(request.description());
        }
    }

    @Override
    protected Map<String, Object> authorizationAttributes(PromptTemplate template) {
        var attributes = new LinkedHashMap<>(super.authorizationAttributes(template));
        attributes.put("visibility", template.getVisibility().name());
        return Map.copyOf(attributes);
    }

    @Override
    protected Specification<PromptTemplate> buildSpec(PromptTemplatePageDTO query) {
        return promptSpec(query, true);
    }

    @Override
    protected Specification<PromptTemplate> buildSpec(
            PromptTemplatePageDTO query, AccessMode accessMode) {
        return promptSpec(query, accessMode != AccessMode.ADMIN_MAINTENANCE);
    }

    private Specification<PromptTemplate> promptSpec(
            PromptTemplatePageDTO query, boolean templateOnly) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (templateOnly) {
                predicates.add(criteriaBuilder.equal(root.get("kind"), PromptKind.TEMPLATE));
            }
            if (query.getType() != null) {
                predicates.add(
                        criteriaBuilder.equal(root.get("type"), parseType(query.getType(), null)));
            }
            if (query.getScope() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("scope"), parseScope(query.getScope(), null)));
            }
            if (query.getCategory() != null && !query.getCategory().isBlank()) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.join("categories", JoinType.INNER).get("code"),
                                query.getCategory().trim()));
            }
            if (query.getIsPublic() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("visibility"),
                                query.getIsPublic()
                                        ? PromptVisibility.PUBLIC
                                        : PromptVisibility.PRIVATE));
            }
            if (templateOnly && Boolean.TRUE.equals(query.getOwnerOnly())) {
                predicates.add(criteriaBuilder.equal(root.get("ownerId"), currentOwnerId()));
            }
            return predicates.isEmpty()
                    ? null
                    : criteriaBuilder.and(
                            predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    @Override
    protected Specification<PromptTemplate> buildOptionSpec(String keyword) {
        return Specification.allOf(
                (root, criteriaQuery, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("kind"), PromptKind.TEMPLATE),
                super.buildOptionSpec(keyword));
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("name", "code");
    }

    /** 平台内置与当前组织/工作区公开模板的统一目录。 */
    public PageResult<PromptTemplateVO> pagePublic(PromptTemplatePageDTO query) {
        var decision = enforce(CrudOperation.PAGE, AccessMode.DEFAULT);
        var directorySpec =
                (Specification<PromptTemplate>)
                        (root, criteriaQuery, criteriaBuilder) -> {
                            var system =
                                    criteriaBuilder.equal(
                                            root.get("visibility"), PromptVisibility.SYSTEM);
                            var tenantPublic = criteriaBuilder.disjunction();
                            if (decision.orgId() != null) {
                                var organization =
                                        criteriaBuilder.equal(root.get("orgId"), decision.orgId());
                                var workspace =
                                        decision.workspaceId() == null
                                                ? criteriaBuilder.isNull(root.get("workspaceId"))
                                                : criteriaBuilder.or(
                                                        criteriaBuilder.isNull(
                                                                root.get("workspaceId")),
                                                        criteriaBuilder.equal(
                                                                root.get("workspaceId"),
                                                                decision.workspaceId()));
                                tenantPublic =
                                        criteriaBuilder.and(
                                                criteriaBuilder.equal(
                                                        root.get("visibility"),
                                                        PromptVisibility.PUBLIC),
                                                organization,
                                                workspace);
                            }
                            return criteriaBuilder.or(system, tenantPublic);
                        };
        return pageDirectory(
                query,
                Specification.allOf(
                        directorySpec, buildSpec(query), buildSearchSpec(query.getSearch())));
    }

    public PageResult<PromptTemplateVO> pageMine(PromptTemplatePageDTO query) {
        query.setIsPublic(null);
        query.setOwnerOnly(true);
        return page(query);
    }

    /** Studio SYSTEM 目录不包含 ENGINE 或 PROCESSING 内部 Prompt。 */
    public PageResult<PromptTemplateVO> pageSystem(PromptTemplatePageDTO query) {
        enforce(CrudOperation.PAGE, AccessMode.DEFAULT);
        return pageDirectory(
                query,
                Specification.allOf(
                        (root, criteriaQuery, criteriaBuilder) ->
                                criteriaBuilder.equal(
                                        root.get("visibility"), PromptVisibility.SYSTEM),
                        buildSpec(query),
                        buildSearchSpec(query.getSearch())));
    }

    private PageResult<PromptTemplateVO> pageDirectory(
            PromptTemplatePageDTO query, Specification<PromptTemplate> specification) {
        var pageNo = Math.max(query.getPageNo(), 1);
        var pageSize = Math.max(1, Math.min(query.getPageSize(), DIRECTORY_MAX_PAGE_SIZE));
        var result =
                repository.findAll(
                        specification,
                        PageRequest.of(
                                pageNo - 1,
                                pageSize,
                                Sort.by(Sort.Order.desc("usageCount"), Sort.Order.desc("id"))));
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

    /** 编译当前发布版本并在同一行锁中记录使用。 */
    @Transactional
    public PromptTemplateUseVO use(Long id, PromptTemplateUseDTO request) {
        var decision = enforce(CrudOperation.UPDATE, AccessMode.DEFAULT);
        var template = requireStudioVisible(id, decision, true);
        var current = requireCurrentVersion(template);
        var variables = request == null ? Map.<String, String>of() : request.variables();
        try {
            var prompt =
                    compiler.compile(
                            current.getContent(),
                            current.getNegativePrompt(),
                            current.getVariables(),
                            variables,
                            false);
            var negativePrompt =
                    compiler.compile(
                            current.getContent(),
                            current.getNegativePrompt(),
                            current.getVariables(),
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

    @Transactional
    public PromptTemplateVO copy(Long id, PromptTemplateCopyDTO request) {
        var decision = enforce(CrudOperation.GET, AccessMode.DEFAULT);
        var source = requireStudioVisible(id, decision, false);
        var current = requireCurrentVersion(source);
        return create(
                new PromptTemplateCreateDTO(
                        request == null || request.name() == null || request.name().isBlank()
                                ? source.getName() + " 副本"
                                : request.name(),
                        source.getType().name(),
                        source.getCategories().stream()
                                .map(category -> category.getCode())
                                .toList(),
                        source.getCoverUrl(),
                        current.getContent(),
                        current.getNegativePrompt(),
                        current.getModel(),
                        current.getWidth(),
                        current.getHeight(),
                        current.getSteps(),
                        current.getSeed(),
                        false,
                        source.getScope().name(),
                        source.getDescription(),
                        compiler.readDeclarations(
                                current.getContent(),
                                current.getNegativePrompt(),
                                current.getVariables())));
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

    private PromptTemplateVersion newVersion(
            PromptTemplate template,
            int version,
            String content,
            String negativePrompt,
            String model,
            Integer width,
            Integer height,
            Integer steps,
            Long seed,
            List<String> variables,
            String changeSummary) {
        var promptVersion = new PromptTemplateVersion();
        promptVersion.setTemplate(template);
        promptVersion.setTemplateVersion(version);
        promptVersion.setStatus(PromptVersionStatus.DRAFT);
        promptVersion.setContent(content);
        promptVersion.setNegativePrompt(negativePrompt);
        promptVersion.setModel(model);
        promptVersion.setWidth(width);
        promptVersion.setHeight(height);
        promptVersion.setSteps(steps);
        promptVersion.setSeed(seed);
        promptVersion.setVariables(serializeDeclarations(content, negativePrompt, variables));
        promptVersion.setContentHash(sha256(content));
        promptVersion.setChangeSummary(changeSummary);
        return promptVersion;
    }

    private boolean hasVersionChange(PromptTemplateUpdateDTO request) {
        return request.prompt() != null
                || request.negativePrompt() != null
                || request.model() != null
                || request.width() != null
                || request.height() != null
                || request.steps() != null
                || request.seed() != null
                || request.variables() != null
                || request.changeSummary() != null;
    }

    private PromptTemplateVersion requireCurrentVersion(PromptTemplate template) {
        var version = template.getCurrentVersion();
        if (version == null
                || version.getStatus() != PromptVersionStatus.PUBLISHED
                || Boolean.TRUE.equals(version.getDeleted())) {
            throw new IllegalStateException("Prompt 缺少当前已发布版本: " + template.getCode());
        }
        return version;
    }

    private java.util.Set<com.xuejiai.aaf.framework.engine.prompt.PromptCategory> resolveCategories(
            List<String> categoryCodes) {
        if (categoryCodes == null || categoryCodes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        var codes =
                categoryCodes.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(code -> !code.isEmpty())
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var categories = categoryRepository.findAllByCodeInAndEnabledTrueAndDeletedFalse(codes);
        if (categories.size() != codes.size()) {
            throw badRequest("包含不存在或已停用的 Prompt 分类");
        }
        return new LinkedHashSet<>(categories);
    }

    private PromptTemplate requireStudioVisible(
            Long id, CrudEnforcementDecision<PromptTemplate> decision, boolean lock) {
        var template =
                (lock ? repository.findByIdForUpdate(id) : repository.findById(id))
                        .filter(candidate -> !Boolean.TRUE.equals(candidate.getDeleted()))
                        .filter(candidate -> isStudioVisible(candidate, decision))
                        .orElseThrow(this::notFound);
        if (template.getVisibility() == PromptVisibility.ENGINE) {
            throw notFound();
        }
        return template;
    }

    protected java.util.Set<PromptKind> allowedKinds() {
        return java.util.Set.of(PromptKind.TEMPLATE);
    }

    protected boolean allowsKind(PromptKind kind) {
        return allowedKinds().contains(kind);
    }

    private boolean isStudioVisible(
            PromptTemplate template, CrudEnforcementDecision<PromptTemplate> decision) {
        if (template.getKind() != PromptKind.TEMPLATE) {
            return false;
        }
        return switch (template.getVisibility()) {
            case ENGINE -> false;
            case SYSTEM -> true;
            case PRIVATE ->
                    Objects.equals(template.getOwnerId(), decision.subjectId())
                            && isCurrentTenant(template, decision);
            case PUBLIC -> isCurrentTenant(template, decision);
        };
    }

    private boolean isCurrentTenant(
            PromptTemplate template, CrudEnforcementDecision<PromptTemplate> decision) {
        return Objects.equals(template.getOrgId(), decision.orgId())
                && (template.getWorkspaceId() == null
                        || Objects.equals(template.getWorkspaceId(), decision.workspaceId()));
    }

    private void enforceUserOwned(PromptTemplate template) {
        if (template.getVisibility() == PromptVisibility.ENGINE
                || template.getVisibility() == PromptVisibility.SYSTEM
                || template.getOwnerId() == null
                || !Objects.equals(template.getOwnerId(), currentOwnerId())) {
            throw notFound();
        }
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

    private PromptType parseType(String value, PromptType defaultValue) {
        if (value == null || value.isBlank()) {
            if (defaultValue != null) return defaultValue;
            throw badRequest("模板类型不能为空");
        }
        try {
            return PromptType.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw badRequest("不支持的模板类型: " + value);
        }
    }

    private PromptScope parseScope(String value, PromptScope defaultValue) {
        if (value == null || value.isBlank()) {
            if (defaultValue != null) return defaultValue;
            throw badRequest("使用场景不能为空");
        }
        try {
            return PromptScope.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw badRequest("不支持的使用场景: " + value);
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw badRequest(message);
        return value.trim();
    }

    private String sha256(String content) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private BusinessException notFound() {
        return new BusinessException(GlobalErrorCode.NOT_FOUND, "提示词资产不存在或无权访问");
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }

    /** Studio 更新保持 DEFAULT 权限；内容变化先创建 Draft，再委托唯一发布服务。 */
    @Transactional
    public PromptTemplateVO updateStudio(Long id, PromptTemplateUpdateDTO request) {
        var fields = modifiedFields(request);
        if (fields.isEmpty()) {
            throw badRequest("至少需要修改一个字段");
        }
        var plan =
                new CustomUpdatePlan<
                        PromptTemplate,
                        PromptTemplateUpdateDTO,
                        PromptTemplateVersion,
                        PromptTemplateVO>(
                        COMMAND_STUDIO_VERSIONED_UPDATE,
                        fields,
                        (template, command) -> {
                            enforceUserOwned(template);
                            if (template.getKind() != PromptKind.TEMPLATE) {
                                throw notFound();
                            }
                        },
                        this::applyRootUpdate,
                        (template, command) -> {
                            if (!hasVersionChange(command)) {
                                return null;
                            }
                            var draft = createDraftVersion(template, toDraftDTO(command));
                            versionRepository.save(draft);
                            return publicationService.publishDraft(
                                    template.getCode(), draft.getTemplateVersion());
                        },
                        fields.stream().anyMatch(ROOT_UPDATE_FIELDS::contains),
                        (template, command, version) -> {},
                        (template, command, version) -> toVO(template));
        return executeCustomUpdateCommand(id, request, plan);
    }

    @Transactional(readOnly = true)
    public PageResult<PromptTemplateVO> pageGovernance(PromptTemplatePageDTO query) {
        return pageWithAccess(query, AccessMode.ADMIN_MAINTENANCE);
    }

    @Transactional(readOnly = true)
    public PageResult<PromptTemplateVO> queryWindowGovernance(
            PromptTemplatePageDTO query, String fieldSet, List<CrudFilter> filters) {
        return queryWindowWithAccess(query, fieldSet, filters, AccessMode.ADMIN_MAINTENANCE);
    }

    @Transactional(readOnly = true)
    public PromptTemplateVO getGovernance(Long id, String queryToken, String fieldSet) {
        return getByIdWithAccess(
                id, queryToken, fieldSet, CrudOperation.GET, AccessMode.ADMIN_MAINTENANCE);
    }

    /** 治理表单更新根元数据和已有 Draft，但绝不自动发布。 */
    @Transactional
    public PromptTemplateVO updateGovernance(Long id, PromptTemplateUpdateDTO request) {
        var fields = modifiedFields(request);
        if (fields.isEmpty()) {
            throw badRequest("至少需要修改一个字段");
        }
        var plan =
                new CustomUpdatePlan<
                        PromptTemplate,
                        PromptTemplateUpdateDTO,
                        PromptTemplateVersion,
                        PromptTemplateVO>(
                        COMMAND_GOVERNANCE_DRAFT_UPDATE,
                        fields,
                        (template, command) -> {
                            if (hasVersionChange(command)) {
                                requireLatestDraft(template.getId());
                            }
                        },
                        this::applyRootUpdate,
                        (template, command) -> {
                            if (!hasVersionChange(command)) {
                                return null;
                            }
                            var draft = requireLatestDraft(template.getId());
                            applyDraftUpdate(draft, toDraftDTO(command));
                            return versionRepository.save(draft);
                        },
                        fields.stream().anyMatch(ROOT_UPDATE_FIELDS::contains),
                        (template, command, version) -> {},
                        (template, command, version) -> toGovernanceVO(template));
        return executeCustomUpdateCommand(id, request, plan, AccessMode.ADMIN_MAINTENANCE);
    }

    @Transactional(readOnly = true)
    public List<PromptVersionVO> listVersionsGovernance(Long id) {
        requireEntity(id, CrudOperation.GET, AccessMode.ADMIN_MAINTENANCE);
        return versionRepository
                .findByTemplateIdAndDeletedFalseOrderByTemplateVersionDesc(id)
                .stream()
                .map(this::toVersionVO)
                .toList();
    }

    @Transactional
    public PromptVersionVO createDraftGovernance(Long id, PromptDraftDTO request) {
        var command = request == null ? emptyDraftRequest() : request;
        var fields = draftModifiedFields(command);
        if (fields.isEmpty()) {
            fields = Set.of("prompt");
        }
        var plan =
                new CustomUpdatePlan<
                        PromptTemplate, PromptDraftDTO, PromptTemplateVersion, PromptVersionVO>(
                        COMMAND_GOVERNANCE_DRAFT_CREATE,
                        fields,
                        (template, ignored) -> {
                            if (versionRepository
                                    .findTopByTemplateIdAndStatusAndDeletedFalseOrderByTemplateVersionDesc(
                                            template.getId(), PromptVersionStatus.DRAFT)
                                    .isPresent()) {
                                throw badRequest("请先处理现有 Draft");
                            }
                        },
                        (template, ignored) -> {},
                        (template, draftRequest) ->
                                versionRepository.save(createDraftVersion(template, draftRequest)),
                        false,
                        (template, ignored, version) -> {},
                        (template, ignored, version) -> toVersionVO(version));
        return executeCustomUpdateCommand(id, command, plan, AccessMode.ADMIN_MAINTENANCE);
    }

    @Transactional
    public PromptVersionVO updateDraftGovernance(Long id, int version, PromptDraftDTO request) {
        var command = request == null ? emptyDraftRequest() : request;
        var fields = draftModifiedFields(command);
        if (fields.isEmpty()) {
            throw badRequest("至少需要修改一个 Draft 字段");
        }
        var plan =
                new CustomUpdatePlan<
                        PromptTemplate, PromptDraftDTO, PromptTemplateVersion, PromptVersionVO>(
                        COMMAND_GOVERNANCE_DRAFT_UPDATE,
                        fields,
                        (template, ignored) -> requireDraft(template.getId(), version),
                        (template, ignored) -> {},
                        (template, draftRequest) -> {
                            var draft = requireDraft(template.getId(), version);
                            applyDraftUpdate(draft, draftRequest);
                            return versionRepository.save(draft);
                        },
                        false,
                        (template, ignored, draft) -> {},
                        (template, ignored, draft) -> toVersionVO(draft));
        return executeCustomUpdateCommand(id, command, plan, AccessMode.ADMIN_MAINTENANCE);
    }

    @Transactional
    public PromptTemplateVO publishDraftGovernance(Long id, int version) {
        var command = new PublishDraftCommand(version);
        var plan =
                new CustomUpdatePlan<
                        PromptTemplate,
                        PublishDraftCommand,
                        PromptTemplateVersion,
                        PromptTemplateVO>(
                        COMMAND_GOVERNANCE_PUBLISH,
                        Set.of("prompt"),
                        (template, publish) -> requireDraft(template.getId(), publish.version()),
                        (template, publish) -> {},
                        (template, publish) ->
                                publicationService.publishDraft(
                                        template.getCode(), publish.version()),
                        false,
                        (template, publish, draft) -> {},
                        (template, publish, draft) -> toGovernanceVO(template));
        return executeCustomUpdateCommand(id, command, plan, AccessMode.ADMIN_MAINTENANCE);
    }

    @Transactional
    public PromptTemplateVO publishLatestDraftGovernance(Long id) {
        var template = requireEntity(id, CrudOperation.UPDATE, AccessMode.ADMIN_MAINTENANCE);
        var draft = requireLatestDraft(template.getId());
        return publishDraftGovernance(id, draft.getTemplateVersion());
    }

    @Override
    protected PromptTemplateVO toVO(
            PromptTemplate template, String fieldSet, AccessMode accessMode) {
        return accessMode == AccessMode.ADMIN_MAINTENANCE
                ? toGovernanceVO(template)
                : toVO(template);
    }

    private PromptTemplateVO toGovernanceVO(PromptTemplate template) {
        var current = requireCurrentVersion(template);
        var draft =
                versionRepository
                        .findTopByTemplateIdAndStatusAndDeletedFalseOrderByTemplateVersionDesc(
                                template.getId(), PromptVersionStatus.DRAFT)
                        .orElse(null);
        var display = draft == null ? current : draft;
        return new PromptTemplateVO(
                template.getId(),
                template.getCode(),
                current.getTemplateVersion(),
                template.getKind().name(),
                template.getType().name(),
                template.getName(),
                template.getCategories().stream()
                        .map(category -> category.getCode())
                        .sorted()
                        .toList(),
                template.getCoverUrl(),
                display.getContent(),
                display.getNegativePrompt(),
                display.getModel(),
                display.getWidth(),
                display.getHeight(),
                display.getSteps(),
                display.getSeed(),
                template.getVisibility() == PromptVisibility.PUBLIC,
                template.getVisibility().name(),
                template.getUsageCount(),
                template.getScope().name(),
                template.getDescription(),
                compiler.readDeclarations(
                        display.getContent(), display.getNegativePrompt(), display.getVariables()),
                draft == null ? null : draft.getTemplateVersion(),
                draft == null ? null : draft.getStatus().name(),
                draft == null ? null : draft.getChangeSummary(),
                template.getOwnerId(),
                template.getCreateTime(),
                template.getUpdateTime());
    }

    private PromptVersionVO toVersionVO(PromptTemplateVersion version) {
        return new PromptVersionVO(
                version.getId(),
                version.getTemplateVersion(),
                version.getStatus().name(),
                version.getContent(),
                version.getNegativePrompt(),
                version.getModel(),
                version.getWidth(),
                version.getHeight(),
                version.getSteps(),
                version.getSeed(),
                compiler.readDeclarations(
                        version.getContent(), version.getNegativePrompt(), version.getVariables()),
                version.getContentHash(),
                version.getChangeSummary(),
                version.getCreateTime(),
                version.getUpdateTime());
    }

    private PromptTemplateVersion createDraftVersion(
            PromptTemplate template, PromptDraftDTO request) {
        var source = requireCurrentVersion(template);
        var latest =
                versionRepository
                        .findTopByTemplateCodeAndDeletedFalseOrderByTemplateVersionDesc(
                                template.getCode())
                        .orElse(source);
        var draft = new PromptTemplateVersion();
        draft.setTemplate(template);
        draft.setTemplateVersion(latest.getTemplateVersion() + 1);
        draft.setStatus(PromptVersionStatus.DRAFT);
        draft.setContent(
                request.prompt() == null
                        ? source.getContent()
                        : requireText(request.prompt(), "提示词不能为空"));
        draft.setNegativePrompt(
                request.negativePrompt() == null
                        ? source.getNegativePrompt()
                        : request.negativePrompt());
        draft.setModel(request.model() == null ? source.getModel() : request.model());
        draft.setWidth(request.width() == null ? source.getWidth() : request.width());
        draft.setHeight(request.height() == null ? source.getHeight() : request.height());
        draft.setSteps(request.steps() == null ? source.getSteps() : request.steps());
        draft.setSeed(request.seed() == null ? source.getSeed() : request.seed());
        var variables =
                request.variables() == null
                        ? compiler.readDeclarations(
                                source.getContent(),
                                source.getNegativePrompt(),
                                source.getVariables())
                        : request.variables();
        draft.setVariables(
                serializeDeclarations(draft.getContent(), draft.getNegativePrompt(), variables));
        draft.setContentHash(sha256(draft.getContent()));
        draft.setChangeSummary(
                request.changeSummary() == null ? "创建 Draft" : request.changeSummary());
        return draft;
    }

    private void applyDraftUpdate(PromptTemplateVersion draft, PromptDraftDTO request) {
        var contentChanged = request.prompt() != null || request.negativePrompt() != null;
        if (request.prompt() != null) {
            draft.setContent(requireText(request.prompt(), "提示词不能为空"));
        }
        if (request.negativePrompt() != null) draft.setNegativePrompt(request.negativePrompt());
        if (request.model() != null) draft.setModel(request.model());
        if (request.width() != null) draft.setWidth(request.width());
        if (request.height() != null) draft.setHeight(request.height());
        if (request.steps() != null) draft.setSteps(request.steps());
        if (request.seed() != null) draft.setSeed(request.seed());
        if (contentChanged || request.variables() != null) {
            draft.setVariables(
                    serializeDeclarations(
                            draft.getContent(), draft.getNegativePrompt(), request.variables()));
        }
        if (request.changeSummary() != null) {
            draft.setChangeSummary(request.changeSummary());
        }
        draft.setContentHash(sha256(draft.getContent()));
    }

    private PromptTemplateVersion requireLatestDraft(Long templateId) {
        return versionRepository
                .findTopByTemplateIdAndStatusAndDeletedFalseOrderByTemplateVersionDesc(
                        templateId, PromptVersionStatus.DRAFT)
                .orElseThrow(() -> badRequest("Prompt 不存在可编辑 Draft"));
    }

    private PromptTemplateVersion requireDraft(Long templateId, int version) {
        return versionRepository
                .findByTemplateIdAndTemplateVersionAndStatusAndDeletedFalse(
                        templateId, version, PromptVersionStatus.DRAFT)
                .orElseThrow(() -> badRequest("Prompt Draft 版本不存在"));
    }

    private PromptDraftDTO toDraftDTO(PromptTemplateUpdateDTO request) {
        return new PromptDraftDTO(
                request.prompt(),
                request.negativePrompt(),
                request.model(),
                request.width(),
                request.height(),
                request.steps(),
                request.seed(),
                request.variables(),
                request.changeSummary());
    }

    private PromptDraftDTO emptyDraftRequest() {
        return new PromptDraftDTO(null, null, null, null, null, null, null, null, null);
    }

    private Set<String> modifiedFields(PromptTemplateUpdateDTO request) {
        var fields = new LinkedHashSet<String>();
        if (request.name() != null) fields.add("name");
        if (request.type() != null) fields.add("type");
        if (request.categories() != null) fields.add("categories");
        if (!request.coverUrl().isAbsent()) fields.add("coverUrl");
        if (request.isPublic() != null) {
            fields.add("isPublic");
            fields.add("visibility");
        }
        if (request.scope() != null) fields.add("scope");
        if (request.description() != null) fields.add("description");
        fields.addAll(draftModifiedFields(toDraftDTO(request)));
        return Set.copyOf(fields);
    }

    private Set<String> draftModifiedFields(PromptDraftDTO request) {
        var fields = new LinkedHashSet<String>();
        if (request.prompt() != null) fields.add("prompt");
        if (request.negativePrompt() != null) fields.add("negativePrompt");
        if (request.model() != null) fields.add("model");
        if (request.width() != null) fields.add("width");
        if (request.height() != null) fields.add("height");
        if (request.steps() != null) fields.add("steps");
        if (request.seed() != null) fields.add("seed");
        if (request.variables() != null) fields.add("variables");
        if (request.changeSummary() != null) fields.add("changeSummary");
        return Set.copyOf(fields);
    }

    private record PublishDraftCommand(int version) {}
}
