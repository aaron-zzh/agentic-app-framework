package com.xuejiai.aaf.module.ai.role;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.filter.CrudFilter;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterOperator;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

/**
 * 角色分类 CRUD 服务。
 *
 * <p>默认访问（用户）：只看到系统分类与自己的私有分类；创建的分类恒为私有，只能修改、删除自己的私有分类。
 *
 * <p>治理访问（管理员，{@link AccessMode#ADMIN_MAINTENANCE}）：可查看全部分类；创建的分类恒为系统分类，只能修改、删除系统分类，
 * 私有分类只读。写入钩子拿不到访问模式，治理入口在调用期间绑定 {@link #WRITE_MODE}，未绑定即默认访问。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleCategoryService
        extends BaseCrudService<
                AiRoleCategory,
                RoleCategoryVO,
                RoleCategoryCreateDTO,
                RoleCategoryUpdateDTO,
                PageParam> {

    private static final String NOT_FOUND_MESSAGE = "角色分类不存在";
    private static final int DEFAULT_SORT_ORDER = 100;
    private static final String VISIBILITY_FIELD = "visibility";

    /** 当前写入的访问模式；只由本类治理入口绑定。 */
    private static final ScopedValue<AccessMode> WRITE_MODE = ScopedValue.newInstance();

    private final RoleCategoryRepository repository;
    private final AiRoleRepository roleRepository;
    private final OperatorContext operatorContext;

    @Override
    protected RoleCategoryRepository getRepository() {
        return repository;
    }

    @Override
    protected RoleCategoryVO toVO(AiRoleCategory entity) {
        return RoleCategoryVO.from(entity);
    }

    /** 可见性由访问模式决定：默认访问创建本人私有分类，治理访问创建系统分类。 */
    @Override
    protected AiRoleCategory toEntity(RoleCategoryCreateDTO dto) {
        var name = dto.name().trim();
        var entity = new AiRoleCategory();
        if (governanceWrite()) {
            if (dto.code() == null || dto.code().isBlank()) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "系统分类必须填写 code");
            }
            var code = dto.code().trim();
            if (repository.existsByCode(code)) {
                throw new BusinessException(409, "角色分类 code 已存在: " + code);
            }
            requireUniqueName(AiRoleCategory.VISIBILITY_SYSTEM, null, name, null);
            entity.setCode(code);
            entity.setVisibility(AiRoleCategory.VISIBILITY_SYSTEM);
        } else {
            if (dto.code() != null && !dto.code().isBlank()) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "私有分类不能指定 code");
            }
            var ownerId = currentUserId();
            requireUniqueName(AiRoleCategory.VISIBILITY_PRIVATE, ownerId, name, null);
            entity.setVisibility(AiRoleCategory.VISIBILITY_PRIVATE);
            entity.setOwnerId(ownerId);
        }
        entity.setName(name);
        entity.setSortOrder(sortOrderOrDefault(dto.sortOrder()));
        return entity;
    }

    @Override
    protected void beforeUpdate(AiRoleCategory entity, RoleCategoryUpdateDTO dto) {
        requireWritable(entity);
    }

    @Override
    protected void updateEntity(AiRoleCategory entity, RoleCategoryUpdateDTO dto) {
        var name = dto.name().trim();
        requireUniqueName(entity.getVisibility(), entity.getOwnerId(), name, entity.getId());
        entity.setName(name);
        entity.setSortOrder(sortOrderOrDefault(dto.sortOrder()));
    }

    /** 私有分类删除前清空所有者私有角色上的该分类；系统分类被任何角色引用时拒绝。 */
    @Override
    protected void beforeDelete(AiRoleCategory entity) {
        requireWritable(entity);
        if (entity.systemCategory()) {
            var referenced = roleRepository.countByCategoryId(entity.getId());
            if (referenced > 0) {
                throw new BusinessException(
                        409, "该分类已被 %d 个角色引用，需先调整这些角色的分类后再删除".formatted(referenced));
            }
            return;
        }
        roleRepository.clearPrivateCategory(entity.getId(), entity.getOwnerId());
    }

    /**
     * 默认访问只返回系统分类与本人私有分类。
     *
     * <p>记录规则已限定同样范围，这里再加一次：super_admin 绕过记录规则，不加时会混入他人私有分类；规则缺失时也能兜底。治理访问只叠加客户端筛选。
     */
    @Override
    protected Specification<AiRoleCategory> buildSpec(PageParam page, AccessMode accessMode) {
        if (accessMode == AccessMode.DEFAULT) {
            return visibleSpec(currentUserId());
        }
        return (root, query, cb) -> null;
    }

    /** 选择器只走默认访问，与列表同口径。 */
    @Override
    protected Specification<AiRoleCategory> buildOptionSpec(String keyword) {
        return Specification.allOf(super.buildOptionSpec(keyword), visibleSpec(currentUserId()));
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("name");
    }

    // ==================== 治理访问入口 ====================

    public PageResult<RoleCategoryVO> pageGovernance(PageParam request) {
        return pageWithAccess(request, AccessMode.ADMIN_MAINTENANCE);
    }

    /** 治理列表未指定可见性筛选时默认只看系统分类，避免管理员默认看到全部用户的私有分类。 */
    public PageResult<RoleCategoryVO> queryWindowGovernance(
            PageParam request, String fieldSet, List<CrudFilter> filters) {
        return queryWindowWithAccess(
                request, fieldSet, withDefaultVisibility(filters), AccessMode.ADMIN_MAINTENANCE);
    }

    private static List<CrudFilter> withDefaultVisibility(List<CrudFilter> filters) {
        var current = filters == null ? List.<CrudFilter>of() : filters;
        if (current.stream().anyMatch(filter -> VISIBILITY_FIELD.equals(filter.field()))) {
            return current;
        }
        var result = new ArrayList<CrudFilter>(current.size() + 1);
        result.add(
                new CrudFilter(
                        VISIBILITY_FIELD,
                        CrudFilterOperator.EQ,
                        List.of(AiRoleCategory.VISIBILITY_SYSTEM)));
        result.addAll(current);
        return result;
    }

    public RoleCategoryVO getGovernance(Long id, String queryToken, String fieldSet) {
        return getByIdWithAccess(
                id, queryToken, fieldSet, CrudOperation.GET, AccessMode.ADMIN_MAINTENANCE);
    }

    /** 管理员创建系统分类。 */
    @Transactional
    public RoleCategoryVO createGovernance(RoleCategoryCreateDTO request) {
        return governance(() -> createWithAccess(request, AccessMode.ADMIN_MAINTENANCE));
    }

    /** 管理员修改系统分类；私有分类拒绝。 */
    @Transactional
    public RoleCategoryVO updateGovernance(Long id, RoleCategoryUpdateDTO request) {
        return governance(() -> updateWithAccess(id, request, AccessMode.ADMIN_MAINTENANCE));
    }

    /** 管理员删除系统分类；私有分类拒绝。 */
    @Transactional
    public void deleteGovernance(Long id) {
        governance(
                () -> {
                    deleteWithAccess(id, AccessMode.ADMIN_MAINTENANCE);
                    return null;
                });
    }

    private static <T> T governance(Supplier<T> action) {
        return ScopedValue.where(WRITE_MODE, AccessMode.ADMIN_MAINTENANCE).call(action::get);
    }

    private static boolean governanceWrite() {
        return WRITE_MODE.isBound() && WRITE_MODE.get() == AccessMode.ADMIN_MAINTENANCE;
    }

    /** 默认访问只能改本人私有分类，系统分类返回无权限；治理访问只能改系统分类。 */
    private void requireWritable(AiRoleCategory entity) {
        if (governanceWrite()) {
            if (!entity.systemCategory()) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN, "用户的私有分类只读，治理模式只能维护系统分类");
            }
            return;
        }
        if (entity.systemCategory()) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权修改系统分类");
        }
        if (!entity.privateOf(currentUserId())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE);
        }
    }

    /** 系统分类之间、同一所有者的私有分类之间名称唯一；唯一索引兜底，这里先给出明确提示。 */
    private void requireUniqueName(String visibility, Long ownerId, String name, Long selfId) {
        boolean taken;
        if (AiRoleCategory.VISIBILITY_SYSTEM.equals(visibility)) {
            taken =
                    selfId == null
                            ? repository.existsByVisibilityAndName(visibility, name)
                            : repository.existsByVisibilityAndNameAndIdNot(
                                    visibility, name, selfId);
        } else {
            taken =
                    selfId == null
                            ? repository.existsByVisibilityAndOwnerIdAndName(
                                    visibility, ownerId, name)
                            : repository.existsByVisibilityAndOwnerIdAndNameAndIdNot(
                                    visibility, ownerId, name, selfId);
        }
        if (taken) {
            throw new BusinessException(409, "角色分类名称已存在: " + name);
        }
    }

    private static Specification<AiRoleCategory> visibleSpec(Long userId) {
        return (root, query, cb) ->
                cb.or(
                        cb.equal(root.get("visibility"), AiRoleCategory.VISIBILITY_SYSTEM),
                        cb.and(
                                cb.equal(root.get("visibility"), AiRoleCategory.VISIBILITY_PRIVATE),
                                cb.equal(root.get("ownerId"), userId)));
    }

    private static int sortOrderOrDefault(Integer sortOrder) {
        return sortOrder == null ? DEFAULT_SORT_ORDER : sortOrder;
    }

    private Long currentUserId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }
}
