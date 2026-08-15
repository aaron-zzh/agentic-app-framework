package com.xuejiai.aaf.module.ai.skill;

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
import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.security.OperatorContext;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

/**
 * 技能管理服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillService
        extends BaseCrudService<
                SkillDefinition, SkillVO, SkillCreateDTO, SkillUpdateDTO, SkillPageDTO> {

    private static final String STATUS_ACTIVE = "active";
    private static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_ACTIVE, "inactive");
    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "code",
                    "name",
                    "category",
                    "priority",
                    "builtIn",
                    "isGlobal",
                    "isPublic",
                    "status",
                    "createTime",
                    "updateTime");
    private static final Sort DIRECTORY_SORT =
            Sort.by(Sort.Order.desc("priority"), Sort.Order.desc("id"));

    private final SkillDefinitionRepository repository;
    private final OperatorContext operatorContext;

    @Override
    protected SkillDefinitionRepository getRepository() {
        return repository;
    }

    @Override
    protected SkillVO toVO(SkillDefinition entity) {
        var currentOwnerId = operatorContext.currentOwnerId().orElse(null);
        return new SkillVO(
                entity.getId(),
                entity.getVersion(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getAgentId(),
                entity.getTriggerIntent(),
                entity.getSystemPrompt(),
                entity.getPriority(),
                entity.getBuiltIn(),
                entity.getIsGlobal(),
                entity.getIsPublic(),
                entity.getStatus(),
                entity.getOwnerId(),
                currentOwnerId != null && Objects.equals(entity.getOwnerId(), currentOwnerId),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected SkillDefinition toEntity(SkillCreateDTO request) {
        var entity = new SkillDefinition();
        entity.setCode(generatedCodeIfBlank(request.code()));
        entity.setName(requireText(request.name(), "技能名称不能为空"));
        entity.setDescription(request.description());
        entity.setCategory(request.category());
        entity.setAgentId(request.agentId());
        entity.setTriggerIntent(request.triggerIntent());
        entity.setSystemPrompt(request.systemPrompt());
        entity.setPriority(request.priority() != null ? request.priority() : 0);
        entity.setBuiltIn(false);
        entity.setIsGlobal(false);
        entity.setIsPublic(Boolean.TRUE.equals(request.isPublic()));
        entity.setStatus(STATUS_ACTIVE);
        return entity;
    }

    @Override
    protected void updateEntity(SkillDefinition entity, SkillUpdateDTO request) {
        if (request.code() != null) {
            entity.setCode(requireText(request.code(), "技能代码不能为空"));
        }
        if (request.name() != null) {
            entity.setName(requireText(request.name(), "技能名称不能为空"));
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.category() != null) {
            entity.setCategory(request.category());
        }
        if (request.agentId() != null) {
            entity.setAgentId(request.agentId());
        }
        if (request.triggerIntent() != null) {
            entity.setTriggerIntent(request.triggerIntent());
        }
        if (request.systemPrompt() != null) {
            entity.setSystemPrompt(request.systemPrompt());
        }
        if (request.priority() != null) {
            entity.setPriority(request.priority());
        }
        if (request.isPublic() != null) {
            entity.setIsPublic(request.isPublic());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
    }

    @Override
    protected Specification<SkillDefinition> buildSpec(SkillPageDTO query) {
        var categorySpec = categorySpec(query.getCategory());
        var activeSpec = activeSpec(Boolean.TRUE.equals(query.getActiveOnly()));
        var ownerSpec =
                Boolean.TRUE.equals(query.getOwnerOnly())
                        ? ownerSpec(currentOwnerId())
                        : unrestrictedSpec();
        return Specification.allOf(categorySpec, activeSpec, ownerSpec);
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("code", "name", "description");
    }

    @Override
    protected Long extractOwnerId(SkillDefinition entity) {
        return entity.getOwnerId();
    }

    @Override
    protected void beforeUpdate(SkillDefinition entity, SkillUpdateDTO request) {
        enforceUserOwned(entity);
        if (request.status() != null && !ALLOWED_STATUSES.contains(request.status())) {
            throw badRequest("技能状态仅支持 active/inactive");
        }
    }

    @Override
    protected void beforeDelete(SkillDefinition entity) {
        enforceUserOwned(entity);
    }

    /** 当前用户在当前组织/工作区创建的全部技能。 */
    public PageResult<SkillVO> pageMine(SkillPageDTO query) {
        query.setOwnerOnly(true);
        return page(query);
    }

    /** 系统技能与当前组织/工作区公开用户技能。 */
    @OrgIgnore
    public PageResult<SkillVO> pagePublic(SkillPageDTO query) {
        enforce(CrudOperation.PAGE, AccessMode.DEFAULT);
        return pageDirectory(
                query,
                publicDirectorySpec(
                        OrgContext.getCurrentOrgId(), OrgContext.getCurrentWorkspaceId()));
    }

    /** 当前用户可见技能合集：我的技能 + 公共技能。 */
    @OrgIgnore
    public List<SkillVO> listVisible(String category, boolean activeOnly) {
        enforce(CrudOperation.PAGE, AccessMode.DEFAULT);
        var spec =
                Specification.allOf(
                        visibleDirectorySpec(
                                currentOwnerId(),
                                OrgContext.getCurrentOrgId(),
                                OrgContext.getCurrentWorkspaceId()),
                        categorySpec(category),
                        activeSpec(activeOnly));
        return repository.findAll(spec, DIRECTORY_SORT).stream().map(this::toVO).toList();
    }

    /** 按 code 查询当前用户可见的激活技能系统提示词，未找到返回 null。 */
    @OrgIgnore
    public String getSystemPromptByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        var spec =
                Specification.allOf(
                        visibleDirectorySpec(
                                operatorContext.currentOwnerId().orElse(null),
                                OrgContext.getCurrentOrgId(),
                                OrgContext.getCurrentWorkspaceId()),
                        (root, query, criteriaBuilder) ->
                                criteriaBuilder.equal(root.get("code"), code),
                        activeSpec(true));
        return repository.findOne(spec).map(SkillDefinition::getSystemPrompt).orElse(null);
    }

    private PageResult<SkillVO> pageDirectory(
            SkillPageDTO query, Specification<SkillDefinition> directorySpec) {
        var spec =
                Specification.allOf(
                        directorySpec,
                        categorySpec(query.getCategory()),
                        activeSpec(Boolean.TRUE.equals(query.getActiveOnly())),
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
            var system =
                    criteriaBuilder.and(
                            criteriaBuilder.isNull(root.get("ownerId")),
                            criteriaBuilder.isNull(root.get("orgId")),
                            criteriaBuilder.isNull(root.get("workspaceId")));
            var tenantPublic = criteriaBuilder.disjunction();
            if (orgId != null) {
                tenantPublic =
                        criteriaBuilder.and(
                                criteriaBuilder.isNotNull(root.get("ownerId")),
                                criteriaBuilder.isTrue(root.get("isPublic")),
                                currentTenantPredicate(root, criteriaBuilder, orgId, workspaceId));
            }
            return criteriaBuilder.or(system, tenantPublic);
        };
    }

    private Specification<SkillDefinition> visibleDirectorySpec(
            Long ownerId, Long orgId, Long workspaceId) {
        var publicSpec = publicDirectorySpec(orgId, workspaceId);
        return (root, query, criteriaBuilder) -> {
            var publicPredicate = publicSpec.toPredicate(root, query, criteriaBuilder);
            if (ownerId == null) {
                return publicPredicate;
            }
            var mine =
                    criteriaBuilder.and(
                            criteriaBuilder.equal(root.get("ownerId"), ownerId),
                            currentTenantPredicate(root, criteriaBuilder, orgId, workspaceId));
            return criteriaBuilder.or(mine, publicPredicate);
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

    private Specification<SkillDefinition> categorySpec(String category) {
        if (category == null || category.isBlank()) {
            return unrestrictedSpec();
        }
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("category"), category);
    }

    private Specification<SkillDefinition> activeSpec(boolean activeOnly) {
        if (!activeOnly) {
            return unrestrictedSpec();
        }
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("status"), STATUS_ACTIVE);
    }

    private Specification<SkillDefinition> ownerSpec(Long ownerId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("ownerId"), ownerId);
    }

    private Specification<SkillDefinition> unrestrictedSpec() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
    }

    private void enforceUserOwned(SkillDefinition entity) {
        if (entity.getOwnerId() == null || Boolean.TRUE.equals(entity.getBuiltIn())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "技能不存在");
        }
        enforceOwnership(entity);
    }

    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private String generatedCodeIfBlank(String code) {
        return code == null || code.isBlank() ? "skill-" + UUID.randomUUID() : code.trim();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }
}
