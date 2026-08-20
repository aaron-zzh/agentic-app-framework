package com.xuejiai.aaf.module.ai.role;

import java.util.Set;

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

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "status", "createTime", "updateTime");

    private final AiRoleRepository roleRepository;

    @Override
    protected AiRoleRepository getRepository() {
        return roleRepository;
    }

    @Override
    protected RoleVO toVO(Role e) {
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

    @Override
    protected Role toEntity(RoleCreateDTO dto) {
        var entity = new Role();
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(RoleSkillBindingJson.write(dto.skillBindings()));
        entity.setToolWhitelist(dto.toolWhitelist());
        return entity;
    }

    @Override
    protected void updateEntity(Role entity, RoleCreateDTO dto) {
        if (!entity.getCode().equals(dto.code())) {
            throw new IllegalArgumentException("Role code 创建后不可修改");
        }
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(RoleSkillBindingJson.write(dto.skillBindings()));
        entity.setToolWhitelist(dto.toolWhitelist());
    }
}
