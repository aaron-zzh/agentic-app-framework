package com.xuejiai.aaf.module.system.permission.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.access.PermissionVersionService;
import com.xuejiai.aaf.framework.security.cache.PermissionCacheService;
import com.xuejiai.aaf.module.system.permission.domain.PermissionCode;
import com.xuejiai.aaf.module.system.permission.repository.PermissionCodeRepository;
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

/** 权限码管理服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionCodeRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PermissionCacheService permissionCacheService;
    private final PermissionVersionService versionService;

    /** 查询权限码树形结构：module → resource → action。 */
    public List<PermissionTreeVO> tree() {
        var all = permissionRepository.findByDeletedFalseOrderByModuleAscResourceAscActionAsc();
        return all.stream()
                .collect(
                        Collectors.groupingBy(
                                PermissionCode::getModule,
                                java.util.TreeMap::new,
                                Collectors.toList()))
                .entrySet()
                .stream()
                .map(
                        moduleEntry ->
                                new PermissionTreeVO(
                                        null,
                                        moduleEntry.getKey(),
                                        null,
                                        moduleEntry.getKey(),
                                        null,
                                        null,
                                        null,
                                        moduleEntry.getValue().stream()
                                                .collect(
                                                        Collectors.groupingBy(
                                                                PermissionCode::getResource,
                                                                java.util.TreeMap::new,
                                                                Collectors.toList()))
                                                .entrySet()
                                                .stream()
                                                .map(
                                                        resourceEntry ->
                                                                new PermissionTreeVO(
                                                                        null,
                                                                        resourceEntry.getKey(),
                                                                        null,
                                                                        moduleEntry.getKey(),
                                                                        resourceEntry.getKey(),
                                                                        null,
                                                                        null,
                                                                        resourceEntry
                                                                                .getValue()
                                                                                .stream()
                                                                                .map(this::toTreeVO)
                                                                                .toList()))
                                                .toList()))
                .toList();
    }

    /** 创建权限码。 */
    @Transactional
    public PermissionVO create(PermissionCreateDTO dto) {
        var code = normalizeCode(dto.code(), dto.module(), dto.resource(), dto.action());
        if (permissionRepository.existsByCodeAndDeletedFalse(code)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "权限编码已存在");
        }
        var entity = new PermissionCode();
        entity.setName(dto.name());
        entity.setModule(normalizeSegment(dto.module()));
        entity.setResource(normalizeSegment(dto.resource()));
        entity.setAction(normalizeSegment(dto.action()));
        entity.setCode(code);
        var vo = toVO(permissionRepository.save(entity));
        versionService.bumpPermissionVersion();
        permissionCacheService.evictAll();
        return vo;
    }

    /** 更新权限码。 */
    @Transactional
    public PermissionVO update(Long id, PermissionUpdateDTO dto) {
        var entity =
                permissionRepository
                        .findByIdAndDeletedFalse(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "权限码不存在"));
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.module() != null) entity.setModule(normalizeSegment(dto.module()));
        if (dto.resource() != null) entity.setResource(normalizeSegment(dto.resource()));
        if (dto.action() != null) entity.setAction(normalizeSegment(dto.action()));
        if (dto.code() != null) {
            var code =
                    normalizeCode(
                            dto.code(),
                            entity.getModule(),
                            entity.getResource(),
                            entity.getAction());
            if (!code.equals(entity.getCode())
                    && permissionRepository.existsByCodeAndDeletedFalse(code)) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "权限编码已存在");
            }
            entity.setCode(code);
        }
        if (dto.status() != null) entity.setStatus(dto.status());
        var vo = toVO(permissionRepository.save(entity));
        versionService.bumpPermissionVersion();
        permissionCacheService.evictAll();
        return vo;
    }

    /** 删除权限码。 */
    @Transactional
    public void delete(Long id) {
        permissionRepository.deleteById(id);
        versionService.bumpPermissionVersion();
        permissionCacheService.evictAll();
    }

    /** 为角色分配权限码。 */
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
        versionService.bumpPermissionVersion();
        permissionCacheService.evictAll();
    }

    /** 查询角色拥有的权限码。 */
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
        versionService.bumpPermissionVersion();
        permissionCacheService.evict(userId);
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
        versionService.bumpPermissionVersion();
        permissionCacheService.evict(userId);
    }

    private PermissionTreeVO toTreeVO(PermissionCode p) {
        return new PermissionTreeVO(
                p.getId(),
                p.getName(),
                p.getCode(),
                p.getModule(),
                p.getResource(),
                p.getAction(),
                p.getStatus(),
                null);
    }

    private String normalizeCode(String code, String module, String resource, String action) {
        if (code != null && !code.isBlank()) {
            return code.trim();
        }
        return "%s:%s:%s"
                .formatted(
                        normalizeSegment(module),
                        normalizeSegment(resource),
                        normalizeSegment(action));
    }

    private String normalizeSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "权限码分段不能为空");
        }
        return value.trim();
    }

    private PermissionVO toVO(PermissionCode p) {
        return new PermissionVO(
                p.getId(),
                p.getName(),
                p.getCode(),
                p.getModule(),
                p.getResource(),
                p.getAction(),
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
