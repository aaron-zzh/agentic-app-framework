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
        extends BaseCrudService<
                SkillDefinition, SkillVO, SkillCreateDTO, SkillUpdateDTO, PageParam> {

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
                e.getCode(),
                e.getName(),
                e.getDescription(),
                e.getCategory(),
                e.getAgentId(),
                e.getTriggerIntent(),
                e.getSystemPrompt(),
                e.getPriority(),
                e.getBuiltIn(),
                e.getStatus());
    }

    @Override
    protected SkillDefinition toEntity(SkillCreateDTO dto) {
        var entity = new SkillDefinition();
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setCategory(dto.category());
        entity.setAgentId(dto.agentId());
        entity.setTriggerIntent(dto.triggerIntent());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setPriority(dto.priority() != null ? dto.priority() : 0);
        entity.setBuiltIn(false);
        entity.setStatus("active");
        return entity;
    }

    @Override
    protected void updateEntity(SkillDefinition entity, SkillUpdateDTO dto) {
        if (dto.code() != null) entity.setCode(dto.code());
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.description() != null) entity.setDescription(dto.description());
        if (dto.category() != null) entity.setCategory(dto.category());
        if (dto.agentId() != null) entity.setAgentId(dto.agentId());
        if (dto.triggerIntent() != null) entity.setTriggerIntent(dto.triggerIntent());
        if (dto.systemPrompt() != null) entity.setSystemPrompt(dto.systemPrompt());
        if (dto.priority() != null) entity.setPriority(dto.priority());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        var entity =
                repository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "技能不存在"));
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

    /** 查询技能列表：全局技能（owner_id 为空）+ 指定 owner 的私有技能；ownerId 为空时返回全部。 */
    public List<SkillVO> list(Long ownerId) {
        var skills = ownerId != null ? repository.findGlobalOrOwned(ownerId) : repository.findAll();
        return skills.stream().map(this::toVO).toList();
    }

    /**
     * 按 category 和 status 查询全局技能（前端 /studio/create/copy 等页面用）。
     *
     * @param category 技能分类（如 COPYWRITING/STRATEGY），null=不过滤
     * @param activeOnly 仅返回 status='active' 的技能
     * @return 按 priority 降序的技能列表
     */
    public List<SkillVO> listByCategory(String category, boolean activeOnly) {
        var skills = repository.findByCategoryFilter(category, activeOnly);
        return skills.stream().map(this::toVO).toList();
    }

    /**
     * 按 code 查询技能的系统提示词，未找到返回 null。
     *
     * @param code skill code（如 voiceover/redbook）
     * @return systemPrompt 或 null
     */
    public String getSystemPromptByCode(String code) {
        if (code == null || code.isBlank()) return null;
        return repository
                .findByCode(code)
                .map(com.xuejiai.aaf.framework.engine.skill.SkillDefinition::getSystemPrompt)
                .orElse(null);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        var entity =
                repository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "技能不存在"));
        entity.setStatus(status);
        repository.save(entity);
    }
}
