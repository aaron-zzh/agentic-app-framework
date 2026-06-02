package com.xuejiai.aaf.module.ai.skill;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;

import lombok.RequiredArgsConstructor;

/**
 * 技能管理服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillService
        extends BaseCrudService<SkillDefinition, SkillVO, SkillCreateDTO, SkillUpdateDTO, PageParam> {

    private final SkillDefinitionRepository repository;

    @Override
    protected JpaRepository<SkillDefinition, Long> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<SkillDefinition> getSpecExecutor() {
        return repository;
    }

    @Override
    protected SkillVO toVO(SkillDefinition e) {
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

    @Override
    protected SkillDefinition toEntity(SkillCreateDTO dto) {
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
        return entity;
    }

    @Override
    protected void updateEntity(SkillDefinition entity, SkillUpdateDTO dto) {
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.description() != null) entity.setDescription(dto.description());
        if (dto.agentId() != null) entity.setAgentId(dto.agentId());
        if (dto.triggerIntent() != null) entity.setTriggerIntent(dto.triggerIntent());
        if (dto.systemPrompt() != null) entity.setSystemPrompt(dto.systemPrompt());
        if (dto.tools() != null) entity.setTools(dto.tools());
        if (dto.priority() != null) entity.setPriority(dto.priority());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        var entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "技能不存在"));
        if (Boolean.TRUE.equals(entity.getBuiltIn())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "内置技能不可删除");
        }
        repository.delete(entity);
    }

    @Override
    protected String entityName() {
        return "技能";
    }

    // ─── 自定义方法 ───

    public List<SkillVO> list(String assistantId) {
        var skills = assistantId != null
                ? repository.findByAssistantIdAndStatus(assistantId, "active")
                : repository.findAll();
        return skills.stream().map(this::toVO).toList();
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        var entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "技能不存在"));
        entity.setStatus(status);
        repository.save(entity);
    }
}
