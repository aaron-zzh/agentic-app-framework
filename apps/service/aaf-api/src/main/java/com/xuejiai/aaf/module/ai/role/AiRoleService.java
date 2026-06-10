/**
 * AI 角色管理 Service（Persona + Role 统一管理）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.role;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.Persona;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.PersonaRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiRoleService {

    private final PersonaRepository personaRepository;
    private final AiRoleRepository roleRepository;

    // ─── Persona ───

    /**
     * 查询所有 Persona
     *
     * @return Persona 列表
     */
    public List<PersonaVO> listActors() {
        return personaRepository.findAll().stream().map(this::toPersonaVO).toList();
    }

    /**
     * 获取 Persona 详情
     *
     * @param id 编号
     * @return Persona 信息
     */
    public PersonaVO getActorById(Long id) {
        return toPersonaVO(getActorEntity(id));
    }

    /**
     * 创建 Persona
     *
     * @param dto 创建请求
     * @return Persona 信息
     */
    @Transactional
    public PersonaVO createActor(PersonaCreateDTO dto) {
        var entity = new Persona();
        entity.setName(dto.name());
        entity.setPersona(dto.persona());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setAvatarUrl(dto.avatarUrl());
        return toPersonaVO(personaRepository.save(entity));
    }

    /**
     * 更新 Persona
     *
     * @param id 编号
     * @param dto 更新请求
     * @return 更新后的 Persona 信息
     */
    @Transactional
    public PersonaVO updateActor(Long id, PersonaCreateDTO dto) {
        var entity = getActorEntity(id);
        entity.setName(dto.name());
        entity.setPersona(dto.persona());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setAvatarUrl(dto.avatarUrl());
        return toPersonaVO(personaRepository.save(entity));
    }

    /**
     * 删除 Persona
     *
     * @param id 编号
     */
    @Transactional
    public void deleteActor(Long id) {
        personaRepository.deleteById(id);
    }

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
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(dto.skillIds());
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
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(dto.skillIds());
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

    private Persona getActorEntity(Long id) {
        return personaRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Persona 不存在"));
    }

    private Role getRoleEntity(Long id) {
        return roleRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Role 不存在"));
    }

    private PersonaVO toPersonaVO(Persona e) {
        return new PersonaVO(
                e.getId(),
                e.getName(),
                e.getPersona(),
                e.getSystemPrompt(),
                e.getAvatarUrl(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    private RoleVO toRoleVO(Role e) {
        return new RoleVO(
                e.getId(),
                e.getName(),
                e.getDescription(),
                e.getSkillIds(),
                e.getToolWhitelist(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime());
    }
}
