package com.xuejiai.aaf.module.ai.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;

import lombok.RequiredArgsConstructor;

/**
 * AI Role CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiRoleCrudService
        extends BaseCrudService<Role, RoleVO, RoleCreateDTO, RoleCreateDTO, PageParam> {

    private final AiRoleRepository roleRepository;

    @Override
    protected JpaRepository<Role, Long> getRepository() {
        return roleRepository;
    }

    @Override
    protected JpaSpecificationExecutor<Role> getSpecExecutor() {
        return roleRepository;
    }

    @Override
    protected RoleVO toVO(Role e) {
        return new RoleVO(
                e.getId(),
                e.getRoleId(),
                e.getName(),
                e.getDescription(),
                e.getSkillIds(),
                e.getToolWhitelist(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected Role toEntity(RoleCreateDTO dto) {
        var entity = new Role();
        entity.setRoleId(dto.roleId());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(dto.skillIds());
        entity.setToolWhitelist(dto.toolWhitelist());
        return entity;
    }

    @Override
    protected void updateEntity(Role entity, RoleCreateDTO dto) {
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(dto.skillIds());
        entity.setToolWhitelist(dto.toolWhitelist());
    }

    @Override
    protected String entityName() {
        return "AI Role";
    }
}
