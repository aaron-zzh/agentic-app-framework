/**
 * AI Role 管理 Service；Persona CRUD 由 PersonaCrudService 唯一承载。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.role;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiRoleService {

    private final AiRoleRepository roleRepository;

    // ─── Role ───

    /**
     * 查询所有 Role
     *
     * @return Role 列表
     */
    public List<RoleVO> listRoles() {
        return roleRepository.findAll().stream().map(this::toRoleVO).toList();
    }

    /**
     * 获取 Role 详情
     *
     * @param id 编号
     * @return Role 信息
     */
    public RoleVO getRoleById(Long id) {
        return toRoleVO(getRoleEntity(id));
    }

    /**
     * 创建 Role
     *
     * @param dto 创建请求
     * @return Role 信息
     */
    @Transactional
    public RoleVO createRole(RoleCreateDTO dto) {
        var entity = new Role();
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(RoleSkillBindingJson.write(dto.skillBindings()));
        entity.setToolWhitelist(dto.toolWhitelist());
        return toRoleVO(roleRepository.save(entity));
    }

    /**
     * 更新 Role
     *
     * @param id 编号
     * @param dto 更新请求
     * @return 更新后的 Role 信息
     */
    @Transactional
    public RoleVO updateRole(Long id, RoleCreateDTO dto) {
        var entity = getRoleEntity(id);
        if (!entity.getCode().equals(dto.code())) {
            throw new IllegalArgumentException("Role code 创建后不可修改");
        }
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(RoleSkillBindingJson.write(dto.skillBindings()));
        entity.setToolWhitelist(dto.toolWhitelist());
        return toRoleVO(roleRepository.save(entity));
    }

    /**
     * 删除 Role
     *
     * @param id 编号
     */
    @Transactional
    public void deleteRole(Long id) {
        roleRepository.deleteById(id);
    }

    // ─── 内部方法 ───

    private Role getRoleEntity(Long id) {
        return roleRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Role 不存在"));
    }

    private RoleVO toRoleVO(Role e) {
        return new RoleVO(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getDescription(),
                RoleSkillBindingJson.read(e.getSkillIds()),
                e.getToolWhitelist(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime());
    }
}
