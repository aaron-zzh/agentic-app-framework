/**
 * AI 角色管理 Service（Actor + Role 统一管理）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.role;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.assistant.actor.Actor;
import com.xuejiai.aaf.framework.intelligent.assistant.actor.ActorRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiRoleService {

    private final ActorRepository actorRepository;
    private final AiRoleRepository roleRepository;

    // ─── Actor ───

    /**
     * 查询所有 Actor
     *
     * @return Actor 列表
     */
    public List<ActorVO> listActors() {
        return actorRepository.findAll().stream().map(this::toActorVO).toList();
    }

    /**
     * 获取 Actor 详情
     *
     * @param id 编号
     * @return Actor 信息
     */
    public ActorVO getActorById(Long id) {
        return toActorVO(getActorEntity(id));
    }

    /**
     * 创建 Actor
     *
     * @param dto 创建请求
     * @return Actor 信息
     */
    @Transactional
    public ActorVO createActor(ActorCreateDTO dto) {
        var entity = new Actor();
        entity.setActorId(dto.actorId());
        entity.setName(dto.name());
        entity.setPersona(dto.persona());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setAvatarUrl(dto.avatarUrl());
        return toActorVO(actorRepository.save(entity));
    }

    /**
     * 更新 Actor
     *
     * @param id 编号
     * @param dto 更新请求
     * @return 更新后的 Actor 信息
     */
    @Transactional
    public ActorVO updateActor(Long id, ActorCreateDTO dto) {
        var entity = getActorEntity(id);
        entity.setName(dto.name());
        entity.setPersona(dto.persona());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setAvatarUrl(dto.avatarUrl());
        return toActorVO(actorRepository.save(entity));
    }

    /**
     * 删除 Actor
     *
     * @param id 编号
     */
    @Transactional
    public void deleteActor(Long id) {
        actorRepository.deleteById(id);
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
        entity.setRoleId(dto.roleId());
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

    private Actor getActorEntity(Long id) {
        return actorRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Actor 不存在"));
    }

    private Role getRoleEntity(Long id) {
        return roleRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Role 不存在"));
    }

    private ActorVO toActorVO(Actor e) {
        return new ActorVO(
                e.getId(),
                e.getActorId(),
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
                e.getRoleId(),
                e.getName(),
                e.getDescription(),
                e.getSkillIds(),
                e.getToolWhitelist(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime());
    }
}
