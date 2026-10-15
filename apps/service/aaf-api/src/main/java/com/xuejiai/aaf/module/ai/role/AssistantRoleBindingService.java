package com.xuejiai.aaf.module.ai.role;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRole;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleVisibility;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.AssistantEntity;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.AssistantRepository;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 用户自维护助理角色绑定：角色库、绑定、解绑、排序、启停与切换默认。
 *
 * <p>规则：
 *
 * <ul>
 *   <li>只能维护自己的助理；系统受管助理（{@code user_id=0}，如客服）一律拒绝
 *   <li>只能绑定对自己可见且处于可用状态的角色；他人私有角色返回"不存在"，不泄露存在性
 *   <li>绑定总数不超过系统参数 {@code ai.assistant_max_roles}
 *   <li>助理始终有且仅有一个默认角色：默认角色不可解绑、不可停用，切换默认在一个事务内完成
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssistantRoleBindingService {

    private static final int DEFAULT_MAX_ROLES = 10;
    private static final int SORT_STEP = 10;
    private static final String ACTIVE = "active";
    private static final String ROLE_NOT_FOUND = "Role 不存在";

    private final AiRoleRepository roleRepository;
    private final AiAssistantRoleRepository bindingRepository;
    private final AssistantRepository assistantRepository;
    private final SystemConfigService configService;
    private final OperatorContext operatorContext;

    /**
     * 角色库：当前用户可绑定的角色，标注是否已绑定到指定助理。
     *
     * @param assistantId 目标助理；为空时取当前用户的默认助理
     */
    public RoleLibraryVO library(Long assistantId) {
        var userId = currentUserId();
        var assistant =
                assistantId == null
                        ? assistantRepository
                                .findFirstByUserIdAndIsDefaultTrueAndStatusOrderByIdAsc(
                                        userId, ACTIVE)
                        : Optional.of(requireOwnedAssistant(assistantId, userId));
        var bindings =
                assistant
                        .map(
                                item ->
                                        bindingRepository.findByAssistantIdOrderBySortOrderAsc(
                                                item.getId()))
                        .orElse(List.of());
        var byRole =
                bindings.stream()
                        .collect(Collectors.toMap(AiAssistantRole::getRoleId, Function.identity()));
        var items =
                roleRepository.findLibrary(userId).stream()
                        .map(
                                role -> {
                                    var binding = byRole.get(role.getId());
                                    return new RoleLibraryVO.Item(
                                            RoleVO.from(role),
                                            binding != null,
                                            binding != null
                                                    && Boolean.TRUE.equals(binding.getEnabled()),
                                            binding != null
                                                    && Boolean.TRUE.equals(binding.getIsDefault()));
                                })
                        .toList();
        return new RoleLibraryVO(
                assistant.map(AssistantEntity::getId).orElse(null),
                bindings.size(),
                maxRoles(),
                items);
    }

    /** 助理当前的全部绑定，含已下架、已不可见的角色（此时 usable 为 false）。 */
    public List<AssistantRoleBindingVO> list(Long assistantId) {
        var userId = currentUserId();
        var assistant = requireOwnedAssistant(assistantId, userId);
        var bindings = bindingRepository.findByAssistantIdOrderBySortOrderAsc(assistant.getId());
        var visibleRoles =
                roleRepository
                        .findVisibleByIds(
                                bindings.stream().map(AiAssistantRole::getRoleId).toList(), userId)
                        .stream()
                        .collect(Collectors.toMap(Role::getId, Function.identity()));
        return bindings.stream()
                .map(binding -> toVO(binding, visibleRoles.get(binding.getRoleId())))
                .toList();
    }

    /** 绑定角色：校验可见性、可用状态、重复绑定与数量上限。 */
    @Transactional
    public AssistantRoleBindingVO bind(Long assistantId, BindRoleDTO request) {
        var userId = currentUserId();
        var assistant = requireOwnedAssistant(assistantId, userId);
        var role = requireBindableRole(request.roleId(), userId);
        var bindings = bindingRepository.findByAssistantIdOrderBySortOrderAsc(assistant.getId());
        if (bindings.stream().anyMatch(item -> item.getRoleId().equals(role.getId()))) {
            throw new BusinessException(409, "该角色已绑定到当前助理");
        }
        var maxRoles = maxRoles();
        if (bindings.size() >= maxRoles) {
            throw new BusinessException(409, "助理最多绑定 %d 个角色，请先解绑不需要的角色".formatted(maxRoles));
        }
        var binding = new AiAssistantRole();
        binding.setAssistantId(assistant.getId());
        binding.setRoleId(role.getId());
        binding.setIsDefault(false);
        binding.setEnabled(true);
        binding.setSortOrder(
                bindings.stream()
                                .map(AiAssistantRole::getSortOrder)
                                .max(Comparator.naturalOrder())
                                .orElse(0)
                        + SORT_STEP);
        binding.setOwnerId(userId);
        return toVO(bindingRepository.save(binding), role);
    }

    /** 解绑角色：默认角色拒绝，需先切换默认。 */
    @Transactional
    public void unbind(Long assistantId, Long roleId) {
        var userId = currentUserId();
        var assistant = requireOwnedAssistant(assistantId, userId);
        var binding = requireBinding(assistant.getId(), roleId);
        if (Boolean.TRUE.equals(binding.getIsDefault())) {
            throw new BusinessException(409, "默认角色不能解绑，请先切换默认角色");
        }
        bindingRepository.delete(binding);
    }

    /** 调整排序、启停与默认角色。设默认在同一事务内先取消旧默认再设置新默认，保证恰有一个默认。 */
    @Transactional
    public AssistantRoleBindingVO update(
            Long assistantId, Long roleId, UpdateRoleBindingDTO request) {
        var userId = currentUserId();
        var assistant = requireOwnedAssistant(assistantId, userId);
        var binding = requireBinding(assistant.getId(), roleId);
        var currentDefault = Boolean.TRUE.equals(binding.getIsDefault());
        if (currentDefault && Boolean.FALSE.equals(request.defaultRole())) {
            throw new BusinessException(409, "不能直接取消默认角色，请把另一个角色设为默认");
        }
        var willDefault = currentDefault || Boolean.TRUE.equals(request.defaultRole());
        var willEnabled =
                request.enabled() != null
                        ? request.enabled()
                        : Boolean.TRUE.equals(binding.getEnabled());
        if (willDefault && !willEnabled) {
            throw new BusinessException(409, "默认角色不能停用，请先切换默认角色");
        }
        var role = roleRepository.findVisibleById(roleId, userId).orElse(null);
        if (willDefault && !currentDefault) {
            if (role == null || !RoleVisibility.canBind(userId, role)) {
                throw new BusinessException(409, "该角色当前不可用，不能设为默认角色");
            }
            switchDefault(assistant.getId(), binding);
        }
        binding.setEnabled(willEnabled);
        if (request.sortOrder() != null) {
            binding.setSortOrder(request.sortOrder());
        }
        return toVO(bindingRepository.save(binding), role);
    }

    /** 先取消旧默认并立即落库，再设置新默认；库里有"每个助理仅一个默认"的唯一索引。 */
    private void switchDefault(Long assistantId, AiAssistantRole target) {
        for (var item : bindingRepository.findByAssistantIdOrderBySortOrderAsc(assistantId)) {
            if (Boolean.TRUE.equals(item.getIsDefault()) && !item.getId().equals(target.getId())) {
                item.setIsDefault(false);
                bindingRepository.saveAndFlush(item);
            }
        }
        target.setIsDefault(true);
    }

    private AssistantRoleBindingVO toVO(AiAssistantRole binding, Role role) {
        var usable = role != null && RoleVisibility.canBind(currentUserId(), role);
        return new AssistantRoleBindingVO(
                binding.getRoleId(),
                role == null ? null : RoleVO.from(role),
                Boolean.TRUE.equals(binding.getEnabled()),
                Boolean.TRUE.equals(binding.getIsDefault()),
                binding.getSortOrder(),
                usable);
    }

    private AiAssistantRole requireBinding(Long assistantId, Long roleId) {
        return bindingRepository
                .findByAssistantIdAndRoleId(assistantId, roleId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "该角色未绑定到当前助理"));
    }

    /** 只能绑定对用户可见且处于可用状态的角色；不可见等同不存在。 */
    private Role requireBindableRole(Long roleId, Long userId) {
        var role =
                roleRepository
                        .findVisibleById(roleId, userId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, ROLE_NOT_FOUND));
        if (!RoleVisibility.canBind(userId, role)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该角色已下架，不能绑定");
        }
        return role;
    }

    /** 只能维护自己的助理；系统受管助理拒绝，他人的助理按不存在处理。 */
    private AssistantEntity requireOwnedAssistant(Long assistantId, Long userId) {
        var assistant =
                assistantRepository
                        .findById(assistantId)
                        .filter(item -> ACTIVE.equals(item.getStatus()))
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "助理不存在"));
        if (Long.valueOf(0L).equals(assistant.getUserId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "系统受管助理的角色只能由管理员维护");
        }
        if (!userId.equals(assistant.getUserId())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "助理不存在");
        }
        return assistant;
    }

    private int maxRoles() {
        return Math.max(
                1,
                configService.getInteger(SysConfigKeys.Ai.ASSISTANT_MAX_ROLES, DEFAULT_MAX_ROLES));
    }

    private Long currentUserId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }
}
