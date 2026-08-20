package com.xuejiai.aaf.module.ai.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
import com.xuejiai.aaf.framework.engine.skill.SkillCategory;
import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;
import com.xuejiai.aaf.framework.engine.skill.SkillModelRequirement;
import com.xuejiai.aaf.framework.engine.skill.SkillToolRequirement;
import com.xuejiai.aaf.framework.engine.skill.SkillVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.security.OperatorContext;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

/** Skill 稳定根对象与不可变版本管理服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillService
        extends BaseCrudService<
                SkillDefinition, SkillVO, SkillCreateDTO, SkillUpdateDTO, SkillPageDTO> {

    public static final String COMMAND_VERSIONED_UPDATE = "SKILL_VERSIONED_UPDATE";
    public static final String COMMAND_PUBLISH = "SKILL_PUBLISH";

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String COPYWRITING_CATEGORY = "copywriting";
    private static final String CONTENT_DRAFT_UPSERT_TOOL = "content.draft.upsert";
    private static final Set<String> VERSION_STATUSES =
            Set.of(STATUS_DRAFT, "IN_REVIEW", STATUS_APPROVED, "REJECTED", "RETIRED");
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "WORKSPACE", "PUBLIC");
    private static final Set<String> TOOL_ACCESS_MODES = Set.of("RESTRICT", "INHERIT");
    private static final Set<String> ROOT_UPDATE_FIELDS =
            Set.of(
                    "code",
                    "name",
                    "summary",
                    "locale",
                    "visibility",
                    "sourceSkillId",
                    "categoryCodes");
    private static final Set<String> VERSION_UPDATE_FIELDS =
            Set.of(
                    "content",
                    "inputSchema",
                    "outputSchema",
                    "outputContract",
                    "toolAccessMode",
                    "toolRequirements",
                    "modelRequirements",
                    "changeSummary",
                    "status");
    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "code",
                    "name",
                    "summary",
                    "locale",
                    "visibility",
                    "builtIn",
                    "currentVersionId",
                    "createTime",
                    "updateTime");
    private static final Sort DIRECTORY_SORT =
            Sort.by(Sort.Order.desc("updateTime"), Sort.Order.desc("id"));

    private final SkillDefinitionRepository repository;
    private final SkillCategoryRepository categoryRepository;
    private final SkillVersionRepository versionRepository;
    private final SkillToolRequirementRepository toolRequirementRepository;
    private final SkillModelRequirementRepository modelRequirementRepository;
    private final OperatorContext operatorContext;
    private final AssistantDefinitionPort assistantDefinitions;

    @Override
    protected SkillDefinitionRepository getRepository() {
        return repository;
    }

    @Override
    protected SkillVO toVO(SkillDefinition entity) {
        var latest =
                versionRepository.findFirstBySkillIdOrderByVersionDesc(entity.getId()).orElse(null);
        var current =
                entity.getCurrentVersionId() == null
                        ? null
                        : versionRepository.findById(entity.getCurrentVersionId()).orElse(null);
        var latestView = toVersionVO(latest);
        var currentView =
                current != null && latest != null && Objects.equals(current.getId(), latest.getId())
                        ? latestView
                        : toVersionVO(current);
        var ownerId = operatorContext.currentOwnerId().orElse(null);
        return new SkillVO(
                entity.getId(),
                entity.getVersion(),
                entity.getCode(),
                entity.getName(),
                entity.getSummary(),
                entity.getLocale(),
                entity.getVisibility(),
                entity.getBuiltIn(),
                entity.getCurrentVersionId(),
                entity.getSourceSkillId(),
                entity.getCategories().stream()
                        .map(
                                category ->
                                        new SkillVO.SkillCategoryVO(
                                                category.getCode(), category.getName()))
                        .toList(),
                currentView,
                latestView,
                entity.getOwnerId(),
                ownerId != null && Objects.equals(entity.getOwnerId(), ownerId),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    private SkillVO.SkillVersionVO toVersionVO(SkillVersion version) {
        if (version == null) {
            return null;
        }
        var tools =
                toolRequirementRepository
                        .findBySkillVersionIdOrderBySortOrderAscIdAsc(version.getId())
                        .stream()
                        .map(
                                requirement ->
                                        new SkillVO.ToolRequirementVO(
                                                requirement.getId(),
                                                requirement.getToolId(),
                                                requirement.getToolVersion(),
                                                requirement.getToolName(),
                                                requirement.getRequired(),
                                                requirement.getUsagePurpose(),
                                                requirement.getSortOrder()))
                        .toList();
        var models =
                modelRequirementRepository
                        .findBySkillVersionIdOrderByCapabilityAsc(version.getId())
                        .stream()
                        .map(
                                requirement ->
                                        new SkillVO.ModelRequirementVO(
                                                requirement.getId(),
                                                requirement.getCapability(),
                                                requirement.getRequired(),
                                                requirement.getMinimumContextTokens(),
                                                requirement.getRationale()))
                        .toList();
        return new SkillVO.SkillVersionVO(
                version.getId(),
                version.getVersion(),
                version.getStatus(),
                version.getContent(),
                version.getInputSchema(),
                version.getOutputSchema(),
                version.getOutputContract(),
                version.getToolAccessMode(),
                tools,
                models,
                version.getChangeSummary(),
                version.getContentHash(),
                version.getAuthoredBy(),
                version.getCreateTime());
    }

    @Override
    protected SkillDefinition toEntity(SkillCreateDTO request) {
        validateCreate(request);
        var entity = new SkillDefinition();
        entity.setCode(generatedCodeIfBlank(request.code()));
        entity.setName(requireText(request.name(), "Skill 名称不能为空"));
        entity.setSummary(requireText(request.summary(), "Skill 摘要不能为空"));
        entity.setLocale(defaultText(request.locale(), "zh-CN"));
        entity.setVisibility(defaultText(request.visibility(), "PRIVATE"));
        entity.setBuiltIn(false);
        entity.setSourceSkillId(request.sourceSkillId());
        if (request.categoryCodes() != null) {
            entity.setCategories(resolveCategories(request.categoryCodes()));
        }
        return entity;
    }

    @Override
    protected void updateEntity(SkillDefinition entity, SkillUpdateDTO request) {
        if (hasVersionChanges(request)) {
            throw badRequest("版本字段必须通过版本化更新入口修改");
        }
        applyRootUpdate(entity, request);
    }

    /** 创建根对象后在同一事务中追加首个不可变版本。 */
    @Transactional
    public SkillVO createVersioned(SkillCreateDTO request) {
        var created = super.create(request);
        var entity =
                repository
                        .findById(created.id())
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        appendInitialVersion(entity, request);
        return toVO(entity);
    }

    /** 根字段按标准授权更新；正文或需求变化时追加完整不可变版本快照。 */
    @Transactional
    public SkillVO updateVersioned(Long id, SkillUpdateDTO request) {
        var modifiedFields = modifiedFields(request);
        if (modifiedFields.isEmpty()) {
            throw badRequest("至少需要修改一个字段");
        }
        var rootChanged = modifiedFields.stream().anyMatch(ROOT_UPDATE_FIELDS::contains);
        var plan =
                new CustomUpdatePlan<SkillDefinition, SkillUpdateDTO, SkillVersion, SkillVO>(
                        COMMAND_VERSIONED_UPDATE,
                        modifiedFields,
                        (entity, command) -> {
                            enforceUserOwned(entity);
                            validateUpdate(entity, command);
                        },
                        this::applyRootUpdate,
                        (entity, command) ->
                                hasVersionChanges(command)
                                        ? appendNextVersion(entity, command)
                                        : null,
                        rootChanged,
                        (entity, command, version) -> {},
                        (entity, command, version) -> toVO(entity));
        return executeCustomUpdateCommand(id, request, plan);
    }

    /** 只有属于当前根对象的 APPROVED 版本才能成为 current。 */
    @Transactional
    public SkillVO publish(Long id, Long versionId) {
        var command = new PublishCommand(versionId);
        var plan =
                new CustomUpdatePlan<SkillDefinition, PublishCommand, Void, SkillVO>(
                        COMMAND_PUBLISH,
                        Set.of("currentVersionId"),
                        (entity, request) -> {
                            enforceUserOwned(entity);
                            validatePublish(entity, request.versionId());
                        },
                        (entity, request) -> entity.setCurrentVersionId(request.versionId()),
                        (entity, request) -> null,
                        true,
                        (entity, request, ignored) -> {},
                        (entity, request, ignored) -> toVO(entity));
        return executeCustomUpdateCommand(id, command, plan);
    }

    /** 读取当前用户拥有的完整版本历史。 */
    public List<SkillVO.SkillVersionVO> listVersions(Long id) {
        var entity = requireEntity(id, CrudOperation.GET, AccessMode.DEFAULT);
        enforceUserOwned(entity);
        return versionRepository.findBySkillIdOrderByVersionDesc(id).stream()
                .map(this::toVersionVO)
                .toList();
    }

    @Override
    protected Specification<SkillDefinition> buildSpec(SkillPageDTO query) {
        var owner =
                Boolean.TRUE.equals(query.getOwnerOnly())
                        ? ownerSpec(currentOwnerId())
                        : unrestrictedSpec();
        return Specification.allOf(
                textSpec("locale", query.getLocale()),
                categorySpec(query.getCategoryCode()),
                textSpec("visibility", query.getVisibility()),
                booleanSpec("builtIn", query.getBuiltIn()),
                publishedSpec(Boolean.TRUE.equals(query.getPublishedOnly())),
                owner);
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("code", "name", "summary");
    }

    @Override
    protected Long extractOwnerId(SkillDefinition entity) {
        return entity.getOwnerId();
    }

    @Override
    protected void beforeUpdate(SkillDefinition entity, SkillUpdateDTO request) {
        enforceUserOwned(entity);
        validateUpdate(entity, request);
    }

    @Override
    protected void beforeDelete(SkillDefinition entity) {
        enforceUserOwned(entity);
    }

    public PageResult<SkillVO> pageMine(SkillPageDTO query) {
        query.setOwnerOnly(true);
        return page(query);
    }

    /** 系统公开 Skill 与当前租户公开 Skill。 */
    @OrgIgnore
    public PageResult<SkillVO> pagePublic(SkillPageDTO query) {
        enforce(CrudOperation.PAGE, AccessMode.DEFAULT);
        return pageDirectory(
                query,
                Specification.allOf(
                        publicDirectorySpec(
                                OrgContext.getCurrentOrgId(), OrgContext.getCurrentWorkspaceId()),
                        publishedSpec(true)));
    }

    /** 当前用户可见 Skill：我的、系统公开及当前工作区共享。 */
    @OrgIgnore
    public List<SkillVO> listVisible(
            String locale, boolean publishedOnly, String categoryCode, String roleKey) {
        enforce(CrudOperation.PAGE, AccessMode.DEFAULT);
        var roleSkillKeys = resolveRoleSkillKeys(roleKey);
        var spec =
                Specification.allOf(
                        visibleDirectorySpec(
                                currentOwnerId(),
                                OrgContext.getCurrentOrgId(),
                                OrgContext.getCurrentWorkspaceId()),
                        textSpec("locale", locale),
                        categorySpec(categoryCode),
                        codeInSpec(roleSkillKeys),
                        publishedSpec(publishedOnly));
        return repository.findAll(spec, DIRECTORY_SORT).stream().map(this::toVO).toList();
    }

    /** 按 code 获取当前用户可见的已发布 Skill 执行上下文。 */
    @OrgIgnore
    public VisibleSkillContext requireVisiblePublished(String code) {
        return requireVisiblePublished(code, null);
    }

    /** 按 code 与受控分类获取当前用户可见的已发布 Skill 执行上下文。 */
    @OrgIgnore
    public VisibleSkillContext requireVisiblePublished(String code, String categoryCode) {
        enforce(CrudOperation.PAGE, AccessMode.DEFAULT);
        var normalizedCode = requireText(code, "Skill 代码不能为空");
        var spec =
                Specification.allOf(
                        visibleDirectorySpec(
                                operatorContext.currentOwnerId().orElse(null),
                                OrgContext.getCurrentOrgId(),
                                OrgContext.getCurrentWorkspaceId()),
                        textSpec("code", normalizedCode),
                        categorySpec(categoryCode),
                        publishedSpec(true));
        var skill =
                repository
                        .findOne(spec)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND,
                                                categoryCode == null || categoryCode.isBlank()
                                                        ? "Skill 不存在或不可见"
                                                        : "指定分类的 Skill 不存在或不可见"));
        var version = requireApprovedVersion(skill.getId(), skill.getCurrentVersionId());
        return new VisibleSkillContext(
                skill.getCode(), skill.getName(), version.getVersion(), version.getContent());
    }

    public record VisibleSkillContext(String code, String name, Integer version, String content) {}

    private void appendInitialVersion(SkillDefinition entity, SkillCreateDTO request) {
        var version = new SkillVersion();
        version.setSkillId(entity.getId());
        version.setVersion(1);
        version.setStatus(defaultText(request.status(), STATUS_DRAFT));
        version.setContent(requireContent(request.content()));
        version.setInputSchema(nullableJson(request.inputSchema()));
        version.setOutputSchema(nullableJson(request.outputSchema()));
        version.setOutputContract(nullableText(request.outputContract()));
        version.setToolAccessMode(defaultText(request.toolAccessMode(), "RESTRICT"));
        version.setChangeSummary(nullableText(request.changeSummary()));
        persistVersion(version, request.toolRequirements(), request.modelRequirements(), null);
    }

    private SkillVersion appendNextVersion(SkillDefinition entity, SkillUpdateDTO request) {
        var previous =
                versionRepository.findFirstBySkillIdOrderByVersionDesc(entity.getId()).orElse(null);
        if (previous == null && request.content() == null) {
            throw badRequest("首个版本必须提供 content");
        }
        var version = new SkillVersion();
        version.setSkillId(entity.getId());
        version.setVersion(previous == null ? 1 : previous.getVersion() + 1);
        version.setStatus(defaultText(request.status(), STATUS_DRAFT));
        version.setContent(
                request.content() == null
                        ? previous.getContent()
                        : requireContent(request.content()));
        version.setInputSchema(
                request.inputSchema() == null
                        ? previousValue(previous, SkillVersion::getInputSchema)
                        : nullableJson(request.inputSchema()));
        version.setOutputSchema(
                request.outputSchema() == null
                        ? previousValue(previous, SkillVersion::getOutputSchema)
                        : nullableJson(request.outputSchema()));
        version.setOutputContract(
                request.outputContract() == null
                        ? previousValue(previous, SkillVersion::getOutputContract)
                        : nullableText(request.outputContract()));
        version.setToolAccessMode(
                request.toolAccessMode() == null
                        ? previousValue(previous, SkillVersion::getToolAccessMode, "RESTRICT")
                        : request.toolAccessMode());
        version.setChangeSummary(nullableText(request.changeSummary()));
        return persistVersion(
                version,
                request.toolRequirements(),
                request.modelRequirements(),
                previous == null ? null : previous.getId());
    }

    private SkillVersion persistVersion(
            SkillVersion version,
            List<SkillCreateDTO.ToolRequirementDTO> tools,
            List<SkillCreateDTO.ModelRequirementDTO> models,
            Long inheritedVersionId) {
        validateVersion(version);
        version.setContentHash(sha256(version.getContent()));
        version.setAuthoredBy(operatorContext.currentOperatorId().orElse(null));
        version.setCreateTime(LocalDateTime.now());
        var saved = versionRepository.save(version);
        persistToolRequirements(saved.getId(), tools, inheritedVersionId);
        persistModelRequirements(saved.getId(), models, inheritedVersionId);
        return saved;
    }

    private void persistToolRequirements(
            Long versionId,
            List<SkillCreateDTO.ToolRequirementDTO> requested,
            Long inheritedVersionId) {
        if (requested == null && inheritedVersionId != null) {
            var inherited =
                    toolRequirementRepository.findBySkillVersionIdOrderBySortOrderAscIdAsc(
                            inheritedVersionId);
            toolRequirementRepository.saveAll(
                    inherited.stream().map(item -> copyToolRequirement(versionId, item)).toList());
            return;
        }
        if (requested == null || requested.isEmpty()) {
            return;
        }
        toolRequirementRepository.saveAll(
                requested.stream().map(item -> toToolRequirement(versionId, item)).toList());
    }

    private void persistModelRequirements(
            Long versionId,
            List<SkillCreateDTO.ModelRequirementDTO> requested,
            Long inheritedVersionId) {
        if (requested == null && inheritedVersionId != null) {
            var inherited =
                    modelRequirementRepository.findBySkillVersionIdOrderByCapabilityAsc(
                            inheritedVersionId);
            modelRequirementRepository.saveAll(
                    inherited.stream().map(item -> copyModelRequirement(versionId, item)).toList());
            return;
        }
        if (requested == null || requested.isEmpty()) {
            return;
        }
        modelRequirementRepository.saveAll(
                requested.stream().map(item -> toModelRequirement(versionId, item)).toList());
    }

    private SkillToolRequirement toToolRequirement(
            Long versionId, SkillCreateDTO.ToolRequirementDTO request) {
        var requirement = new SkillToolRequirement();
        requirement.setSkillVersionId(versionId);
        requirement.setToolId(requireText(request.toolId(), "toolId 不能为空"));
        requirement.setToolVersion(request.toolVersion());
        requirement.setToolName(requireText(request.toolName(), "toolName 不能为空"));
        requirement.setRequired(Boolean.TRUE.equals(request.required()));
        requirement.setUsagePurpose(requireText(request.usagePurpose(), "usagePurpose 不能为空"));
        requirement.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        return requirement;
    }

    private SkillToolRequirement copyToolRequirement(Long versionId, SkillToolRequirement source) {
        var requirement = new SkillToolRequirement();
        requirement.setSkillVersionId(versionId);
        requirement.setToolId(source.getToolId());
        requirement.setToolVersion(source.getToolVersion());
        requirement.setToolName(source.getToolName());
        requirement.setRequired(source.getRequired());
        requirement.setUsagePurpose(source.getUsagePurpose());
        requirement.setSortOrder(source.getSortOrder());
        return requirement;
    }

    private SkillModelRequirement toModelRequirement(
            Long versionId, SkillCreateDTO.ModelRequirementDTO request) {
        var requirement = new SkillModelRequirement();
        requirement.setSkillVersionId(versionId);
        requirement.setCapability(requireText(request.capability(), "capability 不能为空"));
        requirement.setRequired(request.required() == null || request.required());
        requirement.setMinimumContextTokens(request.minimumContextTokens());
        requirement.setRationale(request.rationale() == null ? "" : request.rationale().trim());
        return requirement;
    }

    private SkillModelRequirement copyModelRequirement(
            Long versionId, SkillModelRequirement source) {
        var requirement = new SkillModelRequirement();
        requirement.setSkillVersionId(versionId);
        requirement.setCapability(source.getCapability());
        requirement.setRequired(source.getRequired());
        requirement.setMinimumContextTokens(source.getMinimumContextTokens());
        requirement.setRationale(source.getRationale());
        return requirement;
    }

    private PageResult<SkillVO> pageDirectory(
            SkillPageDTO query, Specification<SkillDefinition> directorySpec) {
        var spec =
                Specification.allOf(
                        directorySpec,
                        textSpec("locale", query.getLocale()),
                        categorySpec(query.getCategoryCode()),
                        textSpec("visibility", query.getVisibility()),
                        booleanSpec("builtIn", query.getBuiltIn()),
                        publishedSpec(Boolean.TRUE.equals(query.getPublishedOnly())),
                        buildSearchSpec(query.getSearch()));
        var page = repository.findAll(spec, query.toPageable(DIRECTORY_SORT, SORTABLE_FIELDS));
        var list = page.getContent().stream().map(this::toVO).toList();
        return new PageResult<>(
                list,
                page.getTotalElements(),
                query.getPageNo(),
                query.getPageSize(),
                list.stream().map(SkillVO::id).toList(),
                null,
                "list",
                page.hasNext());
    }

    private Specification<SkillDefinition> publicDirectorySpec(Long orgId, Long workspaceId) {
        return (root, query, criteriaBuilder) -> {
            var systemPublic =
                    criteriaBuilder.and(
                            criteriaBuilder.isNull(root.get("ownerId")),
                            criteriaBuilder.equal(root.get("visibility"), "PUBLIC"));
            var tenantPublic = criteriaBuilder.disjunction();
            if (orgId != null) {
                tenantPublic =
                        criteriaBuilder.and(
                                criteriaBuilder.isNotNull(root.get("ownerId")),
                                criteriaBuilder.equal(root.get("visibility"), "PUBLIC"),
                                currentTenantPredicate(root, criteriaBuilder, orgId, workspaceId));
            }
            return criteriaBuilder.or(systemPublic, tenantPublic);
        };
    }

    private Specification<SkillDefinition> visibleDirectorySpec(
            Long ownerId, Long orgId, Long workspaceId) {
        var publicSpec = publicDirectorySpec(orgId, workspaceId);
        return (root, query, criteriaBuilder) -> {
            var visible = publicSpec.toPredicate(root, query, criteriaBuilder);
            if (orgId != null) {
                var workspaceShared =
                        criteriaBuilder.and(
                                criteriaBuilder.equal(root.get("visibility"), "WORKSPACE"),
                                currentTenantPredicate(root, criteriaBuilder, orgId, workspaceId));
                visible = criteriaBuilder.or(visible, workspaceShared);
            }
            if (ownerId == null) {
                return visible;
            }
            var mine =
                    criteriaBuilder.and(
                            criteriaBuilder.equal(root.get("ownerId"), ownerId),
                            currentTenantPredicate(root, criteriaBuilder, orgId, workspaceId));
            return criteriaBuilder.or(mine, visible);
        };
    }

    private Predicate currentTenantPredicate(
            Root<SkillDefinition> root,
            CriteriaBuilder criteriaBuilder,
            Long orgId,
            Long workspaceId) {
        if (orgId == null) {
            return criteriaBuilder.and(
                    criteriaBuilder.isNull(root.get("orgId")),
                    criteriaBuilder.isNull(root.get("workspaceId")));
        }
        var organization = criteriaBuilder.equal(root.get("orgId"), orgId);
        var workspace =
                workspaceId == null
                        ? criteriaBuilder.isNull(root.get("workspaceId"))
                        : criteriaBuilder.or(
                                criteriaBuilder.isNull(root.get("workspaceId")),
                                criteriaBuilder.equal(root.get("workspaceId"), workspaceId));
        return criteriaBuilder.and(organization, workspace);
    }

    private Specification<SkillDefinition> textSpec(String property, String value) {
        if (value == null || value.isBlank()) {
            return unrestrictedSpec();
        }
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get(property), value.trim());
    }

    private Specification<SkillDefinition> booleanSpec(String property, Boolean value) {
        if (value == null) {
            return unrestrictedSpec();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(property), value);
    }

    private Set<String> resolveRoleSkillKeys(String roleKey) {
        if (roleKey == null || roleKey.isBlank()) {
            return null;
        }
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "请求缺少组织上下文");
        }
        var ownerId = currentOwnerId();
        var definition =
                assistantDefinitions
                        .findDefaultForUser(
                                new TenantId(orgId.toString()), new UserId(ownerId.toString()))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND,
                                                "当前认证用户没有可用的默认 Assistant"));
        if (definition.lifecycle() != AssistantDefinition.Lifecycle.PUBLISHED) {
            throw badRequest("默认 Assistant 定义不可执行");
        }
        try {
            return definition.requireRole(requireText(roleKey, "Role 不能为空")).skillKeys();
        } catch (IllegalArgumentException exception) {
            throw badRequest("请求 Role 不属于当前 Assistant");
        }
    }

    private Specification<SkillDefinition> codeInSpec(Set<String> skillCodes) {
        if (skillCodes == null) {
            return unrestrictedSpec();
        }
        if (skillCodes.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.disjunction();
        }
        return (root, query, criteriaBuilder) -> root.get("code").in(skillCodes);
    }

    private Specification<SkillDefinition> categorySpec(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return unrestrictedSpec();
        }
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.join("categories").get("code"), categoryCode.trim());
    }

    private Specification<SkillDefinition> publishedSpec(boolean publishedOnly) {
        if (!publishedOnly) {
            return unrestrictedSpec();
        }
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.isNotNull(root.get("currentVersionId"));
    }

    private Specification<SkillDefinition> ownerSpec(Long ownerId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("ownerId"), ownerId);
    }

    private Specification<SkillDefinition> unrestrictedSpec() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
    }

    private void validateCreate(SkillCreateDTO request) {
        requireVisibility(defaultText(request.visibility(), "PRIVATE"));
        requireVersionStatus(defaultText(request.status(), STATUS_DRAFT));
        requireToolAccessMode(defaultText(request.toolAccessMode(), "RESTRICT"));
        requireVisibleSource(request.sourceSkillId(), null);
    }

    private void validateUpdate(SkillDefinition entity, SkillUpdateDTO request) {
        if (request.visibility() != null) {
            requireVisibility(request.visibility());
        }
        if (request.status() != null) {
            requireVersionStatus(request.status());
        }
        if (request.toolAccessMode() != null) {
            requireToolAccessMode(request.toolAccessMode());
        }
        if (request.categoryCodes() != null
                && normalizeCategoryCodes(request.categoryCodes()).contains(COPYWRITING_CATEGORY)
                && entity.getCurrentVersionId() != null) {
            requireDraftTool(entity.getCurrentVersionId());
        }
        requireVisibleSource(request.sourceSkillId(), entity.getId());
    }

    private void validateVersion(SkillVersion version) {
        requireVersionStatus(version.getStatus());
        requireToolAccessMode(version.getToolAccessMode());
        requireContent(version.getContent());
    }

    private void requireVisibleSource(Long sourceSkillId, Long currentSkillId) {
        if (sourceSkillId == null) {
            return;
        }
        if (Objects.equals(sourceSkillId, currentSkillId)) {
            throw badRequest("来源 Skill 不能指向自身");
        }
        var visible =
                visibleDirectorySpec(
                        operatorContext.currentOwnerId().orElse(null),
                        OrgContext.getCurrentOrgId(),
                        OrgContext.getCurrentWorkspaceId());
        if (repository.findOne(Specification.allOf(textIdSpec(sourceSkillId), visible)).isEmpty()) {
            throw badRequest("来源 Skill 不存在或不可见");
        }
    }

    private Specification<SkillDefinition> textIdSpec(Long id) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), id);
    }

    private void validatePublish(SkillDefinition skill, Long versionId) {
        requireApprovedVersion(skill.getId(), versionId);
        validateCopywritingArtifactPolicy(
                skill.getCategories().stream()
                        .map(SkillCategory::getCode)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                toolNames(versionId));
    }

    private void requireDraftTool(Long versionId) {
        validateCopywritingArtifactPolicy(Set.of(COPYWRITING_CATEGORY), toolNames(versionId));
    }

    private Set<String> toolNames(Long versionId) {
        return toolRequirementRepository.findBySkillVersionId(versionId).stream()
                .map(SkillToolRequirement::getToolName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static void validateCopywritingArtifactPolicy(
            Set<String> categoryCodes, Set<String> toolNames) {
        if (categoryCodes.contains(COPYWRITING_CATEGORY)
                && !toolNames.contains(CONTENT_DRAFT_UPSERT_TOOL)) {
            throw badRequest("文案生成分类的 Skill 必须声明 content.draft.upsert 工具");
        }
    }

    private SkillVersion requireApprovedVersion(Long skillId, Long versionId) {
        if (versionId == null) {
            throw badRequest("Skill 尚无可发布版本");
        }
        return versionRepository
                .findByIdAndSkillIdAndStatus(versionId, skillId, STATUS_APPROVED)
                .orElseThrow(() -> badRequest("只能发布属于当前 Skill 的 APPROVED 版本"));
    }

    private void applyRootUpdate(SkillDefinition entity, SkillUpdateDTO request) {
        if (request.code() != null) {
            entity.setCode(requireText(request.code(), "Skill 代码不能为空"));
        }
        if (request.name() != null) {
            entity.setName(requireText(request.name(), "Skill 名称不能为空"));
        }
        if (request.summary() != null) {
            entity.setSummary(requireText(request.summary(), "Skill 摘要不能为空"));
        }
        if (request.locale() != null) {
            entity.setLocale(requireText(request.locale(), "locale 不能为空"));
        }
        if (request.visibility() != null) {
            entity.setVisibility(request.visibility());
        }
        if (request.sourceSkillId() != null) {
            entity.setSourceSkillId(request.sourceSkillId());
        }
        if (request.categoryCodes() != null) {
            entity.setCategories(resolveCategories(request.categoryCodes()));
        }
    }

    private Set<String> modifiedFields(SkillUpdateDTO request) {
        var fields = new java.util.LinkedHashSet<String>();
        if (request.code() != null) fields.add("code");
        if (request.name() != null) fields.add("name");
        if (request.summary() != null) fields.add("summary");
        if (request.locale() != null) fields.add("locale");
        if (request.visibility() != null) fields.add("visibility");
        if (request.sourceSkillId() != null) fields.add("sourceSkillId");
        if (request.categoryCodes() != null) fields.add("categoryCodes");
        if (request.content() != null) fields.add("content");
        if (request.inputSchema() != null) fields.add("inputSchema");
        if (request.outputSchema() != null) fields.add("outputSchema");
        if (request.outputContract() != null) fields.add("outputContract");
        if (request.toolAccessMode() != null) fields.add("toolAccessMode");
        if (request.toolRequirements() != null) fields.add("toolRequirements");
        if (request.modelRequirements() != null) fields.add("modelRequirements");
        if (request.changeSummary() != null) fields.add("changeSummary");
        if (request.status() != null) fields.add("status");
        return Set.copyOf(fields);
    }

    static Set<String> normalizeCategoryCodes(List<String> categoryCodes) {
        var normalizedCodes = new java.util.LinkedHashSet<String>();
        for (var categoryCode : categoryCodes) {
            var normalizedCode = requireText(categoryCode, "分类代码不能为空");
            if (!normalizedCodes.add(normalizedCode)) {
                throw badRequest("分类代码不能重复");
            }
        }
        return normalizedCodes;
    }

    private Set<SkillCategory> resolveCategories(List<String> categoryCodes) {
        var normalizedCodes = normalizeCategoryCodes(categoryCodes);
        var categories = categoryRepository.findByCodeIn(normalizedCodes);
        if (categories.size() != normalizedCodes.size()) {
            var foundCodes =
                    categories.stream()
                            .map(SkillCategory::getCode)
                            .collect(java.util.stream.Collectors.toSet());
            var missingCodes =
                    normalizedCodes.stream()
                            .filter(code -> !foundCodes.contains(code))
                            .collect(java.util.stream.Collectors.joining(", "));
            throw badRequest("分类不存在: " + missingCodes);
        }
        var categoriesByCode = new java.util.HashMap<String, SkillCategory>();
        categories.forEach(category -> categoriesByCode.put(category.getCode(), category));
        var resolved = new java.util.LinkedHashSet<SkillCategory>();
        normalizedCodes.forEach(code -> resolved.add(categoriesByCode.get(code)));
        return resolved;
    }

    private boolean hasVersionChanges(SkillUpdateDTO request) {
        return modifiedFields(request).stream().anyMatch(VERSION_UPDATE_FIELDS::contains);
    }

    private void enforceUserOwned(SkillDefinition entity) {
        if (entity.getOwnerId() == null || Boolean.TRUE.equals(entity.getBuiltIn())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "Skill 不存在");
        }
        enforceOwnership(entity);
    }

    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private void requireVisibility(String value) {
        if (!VISIBILITIES.contains(value)) {
            throw badRequest("visibility 仅支持 PRIVATE/WORKSPACE/PUBLIC");
        }
    }

    private void requireVersionStatus(String value) {
        if (!VERSION_STATUSES.contains(value)) {
            throw badRequest("版本状态非法");
        }
    }

    private void requireToolAccessMode(String value) {
        if (!TOOL_ACCESS_MODES.contains(value)) {
            throw badRequest("toolAccessMode 仅支持 RESTRICT/INHERIT");
        }
    }

    private String generatedCodeIfBlank(String code) {
        return code == null || code.isBlank() ? "skill-" + UUID.randomUUID() : code.trim();
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private String requireContent(String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("Skill content 不能为空");
        }
        return value;
    }

    private String nullableText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String nullableJson(String value) {
        return nullableText(value);
    }

    private <T> T previousValue(
            SkillVersion previous, java.util.function.Function<SkillVersion, T> getter) {
        return previous == null ? null : getter.apply(previous);
    }

    private <T> T previousValue(
            SkillVersion previous,
            java.util.function.Function<SkillVersion, T> getter,
            T defaultValue) {
        var value = previousValue(previous, getter);
        return value == null ? defaultValue : value;
    }

    private String sha256(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }

    private record PublishCommand(Long versionId) {}
}
