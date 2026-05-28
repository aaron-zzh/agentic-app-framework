package com.xuejiai.aaf.module.ai.skill;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;

import lombok.RequiredArgsConstructor;

/**
 * 技能管理服务——CRUD 操作
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillDefinitionRepository repository;

    /**
     * 查询技能列表
     *
     * @param assistantId 助手 ID，为 null 时查全部
     * @return 技能列表
     */
    public List<SkillVO> list(String assistantId) {
        var skills =
                assistantId != null
                        ? repository.findByAssistantIdAndStatus(assistantId, "active")
                        : repository.findAll();
        return skills.stream().map(this::toVO).toList();
    }

    /**
     * 获取技能详情
     *
     * @param id 技能 ID
     * @return 技能视图对象
     */
    public SkillVO getById(Long id) {
        return toVO(requireSkill(id));
    }

    /**
     * 创建技能
     *
     * @param dto 创建请求
     * @return 创建后的技能视图对象
     */
    @Transactional
    public SkillVO create(SkillCreateDTO dto) {
        var entity = new SkillDefinition();
        entity.setSkillId(dto.skillId());
        entity.setAssistantId(dto.assistantId());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setAgentId(dto.agentId());
        entity.setTriggerIntent(dto.triggerIntent());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setTools(dto.tools());
        entity.setPriority(dto.priority() != null ? dto.priority() : 0);
        entity.setBuiltIn(false);
        entity.setStatus("active");
        repository.save(entity);
        return toVO(entity);
    }

    /**
     * 更新技能
     *
     * @param id 技能 ID
     * @param dto 更新请求
     * @return 更新后的技能视图对象
     */
    @Transactional
    public SkillVO update(Long id, SkillUpdateDTO dto) {
        var entity = requireSkill(id);
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.description() != null) entity.setDescription(dto.description());
        if (dto.agentId() != null) entity.setAgentId(dto.agentId());
        if (dto.triggerIntent() != null) entity.setTriggerIntent(dto.triggerIntent());
        if (dto.systemPrompt() != null) entity.setSystemPrompt(dto.systemPrompt());
        if (dto.tools() != null) entity.setTools(dto.tools());
        if (dto.priority() != null) entity.setPriority(dto.priority());
        repository.save(entity);
        return toVO(entity);
    }

    /**
     * 删除技能（内置技能不可删除）
     *
     * @param id 技能 ID
     */
    @Transactional
    public void delete(Long id) {
        var entity = requireSkill(id);
        if (Boolean.TRUE.equals(entity.getBuiltIn())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "内置技能不可删除");
        }
        repository.delete(entity);
    }

    /**
     * 启用/禁用技能
     *
     * @param id 技能 ID
     * @param status 目标状态
     */
    @Transactional
    public void updateStatus(Long id, String status) {
        var entity = requireSkill(id);
        entity.setStatus(status);
        repository.save(entity);
    }

    private SkillDefinition requireSkill(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "技能不存在"));
    }

    private SkillVO toVO(SkillDefinition e) {
        return new SkillVO(
                e.getId(),
                e.getSkillId(),
                e.getAssistantId(),
                e.getName(),
                e.getDescription(),
                e.getAgentId(),
                e.getTriggerIntent(),
                e.getSystemPrompt(),
                e.getTools(),
                e.getPriority(),
                e.getBuiltIn(),
                e.getStatus());
    }
}
