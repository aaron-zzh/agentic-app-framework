package com.xuejiai.aaf.module.system.permission.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.permission.domain.Permission;
import com.xuejiai.aaf.module.system.permission.repository.MenuPermissionRepository;
import com.xuejiai.aaf.module.system.permission.vo.PermissionCreateDTO;
import com.xuejiai.aaf.module.system.permission.vo.PermissionTreeVO;
import com.xuejiai.aaf.module.system.permission.vo.PermissionUpdateDTO;
import com.xuejiai.aaf.module.system.permission.vo.PermissionVO;
import com.xuejiai.aaf.module.system.role.domain.RolePermission;
import com.xuejiai.aaf.module.system.role.domain.UserRole;
import com.xuejiai.aaf.module.system.role.repository.RolePermissionRepository;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;
import com.xuejiai.aaf.module.system.role.vo.RoleVO;

import lombok.RequiredArgsConstructor;

/**
 * 权限点管理服务。
 *
 * @author AaronZZH & Kiro
 */
@Service("menuPermissionService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final MenuPermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    /**
     * 查询权限点树形结构。
     *
     * @return 树形权限列表
     */
    public List<PermissionTreeVO> tree() {
        var all = permissionRepository.findByDeletedFalseOrderBySortOrder();
        return buildTree(all, 0L);
    }

    /**
     * 创建权限点。
     *
     * @param dto 创建请求
     * @return 创建后的权限点
     */
    @Transactional
    public PermissionVO create(PermissionCreateDTO dto) {
        if (permissionRepository.existsByCodeAndDeletedFalse(dto.code())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "权限编码已存在");
        }
        var entity = new Permission();
        entity.setName(dto.name());
        entity.setCode(dto.code());
        entity.setType(dto.type());
        entity.setParentId(dto.parentId() != null ? dto.parentId() : 0L);
        entity.setPath(dto.path());
        entity.setIcon(dto.icon());
        entity.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0);
        return toVO(permissionRepository.save(entity));
    }

    /**
     * 更新权限点。
     *
     * @param id 权限点 ID
     * @param dto 更新请求
     * @return 更新后的权限点
     */
    @Transactional
    public PermissionVO update(Long id, PermissionUpdateDTO dto) {
        var entity =
                permissionRepository
                        .findByIdAndDeletedFalse(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "权限点不存在"));
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.type() != null) entity.setType(dto.type());
        if (dto.parentId() != null) entity.setParentId(dto.parentId());
        if (dto.path() != null) entity.setPath(dto.path());
        if (dto.icon() != null) entity.setIcon(dto.icon());
        if (dto.sortOrder() != null) entity.setSortOrder(dto.sortOrder());
        if (dto.status() != null) entity.setStatus(dto.status());
        return toVO(permissionRepository.save(entity));
    }

    /**
     * 删除权限点（校验子节点）。
     *
     * @param id 权限点 ID
     */
    @Transactional
    public void delete(Long id) {
        if (permissionRepository.existsByParentIdAndDeletedFalse(id)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "存在子节点，无法删除");
        }
        permissionRepository.deleteById(id);
    }

    /**
     * 为角色分配权限点。
     *
     * @param roleId 角色 ID
     * @param permissionIds 权限点 ID 列表
     */
    @Transactional
    public void assignPermissionsToRole(Long roleId, List<Long> permissionIds) {
        // 先删除旧关联
        rolePermissionRepository.deleteByRoleId(roleId);
        // 新增关联
        var entities =
                permissionIds.stream()
                        .map(
                                pid -> {
                                    var rp = new RolePermission();
                                    rp.setRoleId(roleId);
                                    rp.setPermissionId(pid);
                                    return rp;
                                })
                        .toList();
        rolePermissionRepository.saveAll(entities);
    }

    /**
     * 查询角色拥有的权限点。
     *
     * @param roleId 角色 ID
     * @return 权限点列表
     */
    public List<PermissionVO> getPermissionsByRoleId(Long roleId) {
        var rolePermissions = rolePermissionRepository.findByRoleIdAndDeletedFalse(roleId);
        var ids = rolePermissions.stream().map(RolePermission::getPermissionId).toList();
        if (ids.isEmpty()) return List.of();
        return permissionRepository.findByIdInAndDeletedFalse(ids).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 为用户分配角色。
     *
     * @param userId 用户 ID
     * @param roleIds 角色 ID 列表
     */
    @Transactional
    public void assignRolesToUser(Long userId, List<Long> roleIds) {
        var existing = userRoleRepository.findByUserIdAndDeletedFalse(userId);
        var existingRoleIds =
                existing.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        var toAdd =
                roleIds.stream()
                        .filter(rid -> !existingRoleIds.contains(rid))
                        .map(
                                rid -> {
                                    var ur = new UserRole();
                                    ur.setUserId(userId);
                                    ur.setRoleId(rid);
                                    return ur;
                                })
                        .toList();
        userRoleRepository.saveAll(toAdd);
    }

    /**
     * 查询用户拥有的角色。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    public List<RoleVO> getRolesByUserId(Long userId) {
        var userRoles = userRoleRepository.findByUserIdAndDeletedFalse(userId);
        var roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        if (roleIds.isEmpty()) return List.of();
        return roleRepository.findAllById(roleIds).stream().map(this::toRoleVO).toList();
    }

    /**
     * 移除用户的某个角色。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    @Transactional
    public void removeRoleFromUser(Long userId, Long roleId) {
        var userRoles = userRoleRepository.findByUserIdAndDeletedFalse(userId);
        userRoles.stream()
                .filter(ur -> ur.getRoleId().equals(roleId))
                .findFirst()
                .ifPresent(ur -> userRoleRepository.deleteById(ur.getId()));
    }

    private List<PermissionTreeVO> buildTree(List<Permission> all, Long parentId) {
        Map<Long, List<Permission>> grouped =
                all.stream().collect(Collectors.groupingBy(Permission::getParentId));
        return buildChildren(grouped, parentId);
    }

    private List<PermissionTreeVO> buildChildren(
            Map<Long, List<Permission>> grouped, Long parentId) {
        var children = grouped.getOrDefault(parentId, List.of());
        var result = new ArrayList<PermissionTreeVO>();
        for (var p : children) {
            result.add(
                    new PermissionTreeVO(
                            p.getId(),
                            p.getName(),
                            p.getCode(),
                            p.getType(),
                            p.getParentId(),
                            p.getPath(),
                            p.getIcon(),
                            p.getSortOrder(),
                            p.getStatus(),
                            buildChildren(grouped, p.getId())));
        }
        return result;
    }

    private PermissionVO toVO(Permission p) {
        return new PermissionVO(
                p.getId(),
                p.getName(),
                p.getCode(),
                p.getType(),
                p.getParentId(),
                p.getPath(),
                p.getIcon(),
                p.getSortOrder(),
                p.getStatus(),
                p.getCreateTime());
    }

    private RoleVO toRoleVO(com.xuejiai.aaf.module.system.role.domain.Role role) {
        return new RoleVO(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getStatus(),
                role.getCreateTime());
    }
}
