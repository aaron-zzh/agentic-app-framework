package com.xuejiai.aaf.module.ai.assistant.service;

import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.SkillMatchService;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.PersonaRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRole;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryStrategy;
import com.xuejiai.aaf.module.ai.assistant.vo.*;

import lombok.RequiredArgsConstructor;

/**
 * Assistant 管理服务——委托 framework 层。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class AssistantManagementService {

    private final AssistantDefinitionRepository assistantRepo;
    private final PersonaRepository personaRepo;
    private final AiRoleRepository roleRepo;
    private final AiAssistantRoleRepository assistantRoleRepo;
    private final SkillMatchService skillMatchService;

    /**
     * 创建 Assistant。
     *
     * @param dto 创建请求
     * @return Assistant 信息
     */
    @Transactional
    public AssistantVO create(AssistantCreateDTO dto) {
        var entity = new AssistantDefinition();
        entity.setUserId(dto.userId());
        entity.setPersonaId(dto.personaId());
        entity.setDefaultRoleId(dto.defaultRoleId());
        if (dto.memoryStrategy() != null) {
            entity.setMemoryStrategy(MemoryStrategy.valueOf(dto.memoryStrategy()));
        }
        entity.setKnowledgeBaseId(dto.knowledgeBaseId());
        var saved = assistantRepo.save(entity);
        // 经 ai_assistant_role 维护助理-角色关联（默认角色自动补入）
        saveRoleBindings(saved.getId(), dto.roleIds(), dto.defaultRoleId());
        return toVO(saved);
    }

    /**
     * 分页查询 Assistant 列表。
     *
     * @param userId 用户 ID 过滤（可选）
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<AssistantVO> list(Long userId, Pageable pageable) {
        Page<AssistantDefinition> page;
        if (userId != null) {
            page = assistantRepo.findByUserId(userId, pageable);
        } else {
            page = assistantRepo.findAll(pageable);
        }
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /**
     * 获取 Assistant 详情。
     *
     * @param id 数据库 ID
     * @return Assistant 信息
     */
    @Transactional(readOnly = true)
    public AssistantVO getById(Long id) {
        var entity =
                assistantRepo
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
        return toVO(entity);
    }

    /**
     * 更新 Assistant。
     *
     * @param id 数据库 ID
     * @param dto 更新请求
     * @return 更新后的 Assistant 信息
     */
    @Transactional
    public AssistantVO update(Long id, AssistantUpdateDTO dto) {
        var entity =
                assistantRepo
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
        if (dto.personaId() != null) entity.setPersonaId(dto.personaId());
        if (dto.defaultRoleId() != null) entity.setDefaultRoleId(dto.defaultRoleId());
        if (dto.memoryStrategy() != null) {
            entity.setMemoryStrategy(MemoryStrategy.valueOf(dto.memoryStrategy()));
        }
        if (dto.knowledgeBaseId() != null) entity.setKnowledgeBaseId(dto.knowledgeBaseId());
        var saved = assistantRepo.save(entity);
        // 角色列表传入则全量替换助理-角色关联（禁兼容层：直接替换，不保留旧关联）
        if (dto.roleIds() != null) {
            assistantRoleRepo.deleteByAssistantId(id);
            saveRoleBindings(id, dto.roleIds(), saved.getDefaultRoleId());
        }
        return toVO(saved);
    }

    /**
     * 删除 Assistant（逻辑删除）。
     *
     * @param id 数据库 ID
     */
    @Transactional
    public void delete(Long id) {
        var entity =
                assistantRepo
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
        entity.setStatus("inactive");
        assistantRepo.save(entity);
    }

    /**
     * 绑定技能到 Assistant。
     *
     * @param id 数据库 ID
     * @param skillIds 技能 ID 列表
     */
    @Transactional
    public void bindSkills(Long id, List<String> skillIds) {
        // TODO: 委托 framework 层 RoleStore 更新 Role 的 skillIds
        // 当前 Role 的 skillIds 管理在 RoleStore 中，需通过 roleId 更新
        var entity =
                assistantRepo
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
        // TODO: roleStore.updateSkillIds(entity.getRoleId(), skillIds);
        throw new UnsupportedOperationException("绑定技能功能待 RoleStore 接口完善后实现");
    }

    /**
     * 配置工具白名单。
     *
     * @param id 数据库 ID
     * @param toolWhitelist 工具白名单列表
     */
    @Transactional
    public void configureToolWhitelist(Long id, List<String> toolWhitelist) {
        // TODO: 委托 framework 层配置 Assistant 关联 Agent 的 allowedTools
        var entity =
                assistantRepo
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
        // TODO: 通过 Role → Agent 链路更新 allowedTools
        throw new UnsupportedOperationException("配置工具白名单功能待完善");
    }

    /** 获取当前可用助理列表（含各助理下的角色列表，供前端角色选择器分组展示）。 返回所有 active 状态的公共助理（userId=0）。 */
    public List<AssistantAvailableVO> listAvailable() {
        var assistants = assistantRepo.findByUserIdAndStatus(0L, "active");
        return assistants.stream()
                .map(
                        a -> {
                            var persona = personaRepo.findById(a.getPersonaId()).orElse(null);
                            var roles =
                                    roleRepo
                                            .findByAssistantIdAndStatus(a.getId(), "active")
                                            .stream()
                                            .map(
                                                    r ->
                                                            new AssistantAvailableVO.RoleItem(
                                                                    r.getId(),
                                                                    r.getName(),
                                                                    r.getDescription()))
                                            .toList();
                            return new AssistantAvailableVO(
                                    a.getId(),
                                    persona != null ? persona.getName() : String.valueOf(a.getId()),
                                    persona != null ? persona.getAvatarUrl() : null,
                                    a.getDefaultRoleId(),
                                    roles);
                        })
                .toList();
    }

    private AssistantVO toVO(AssistantDefinition e) {
        return new AssistantVO(
                e.getId(),
                e.getUserId(),
                e.getPersonaId(),
                e.getDefaultRoleId(),
                roleIdsOf(e.getId()),
                e.getMemoryStrategy() != null ? e.getMemoryStrategy().name() : null,
                e.getKnowledgeBaseId(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    /** 查询助理挂载的角色 ID 列表（经 ai_assistant_role，按排序值升序）。 */
    private List<Long> roleIdsOf(Long assistantId) {
        return assistantRoleRepo.findByAssistantIdOrderBySortOrderAsc(assistantId).stream()
                .map(AiAssistantRole::getRoleId)
                .toList();
    }

    /**
     * 维护助理-角色关联（ai_assistant_role）。
     *
     * <p>绑定集合 = roleIds ∪ {defaultRoleId}，确保默认角色一定在关联中；与 defaultRoleId 相同的关联标记 isDefault。
     */
    private void saveRoleBindings(Long assistantId, List<Long> roleIds, Long defaultRoleId) {
        var ids = new LinkedHashSet<Long>();
        if (defaultRoleId != null) ids.add(defaultRoleId);
        if (roleIds != null) ids.addAll(roleIds);
        int order = 0;
        for (var roleId : ids) {
            var link = new AiAssistantRole();
            link.setAssistantId(assistantId);
            link.setRoleId(roleId);
            link.setIsDefault(roleId.equals(defaultRoleId));
            link.setSortOrder(order++);
            assistantRoleRepo.save(link);
        }
    }
}
