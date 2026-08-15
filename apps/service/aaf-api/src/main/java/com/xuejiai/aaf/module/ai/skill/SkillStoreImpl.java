package com.xuejiai.aaf.module.ai.skill;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;
import com.xuejiai.aaf.framework.engine.skill.SkillStore;

import lombok.RequiredArgsConstructor;

/**
 * SkillStore 实现——桥接 JPA Repository，供引擎层使用。
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class SkillStoreImpl implements SkillStore {

    private static final String STATUS_ACTIVE = "active";

    private final SkillDefinitionRepository repository;

    @Override
    public List<SkillRecord> findByAgentId(Long agentId) {
        return repository.findByAgentIdAndStatus(agentId, STATUS_ACTIVE).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<SkillRecord> findBuiltIn() {
        return repository.findByBuiltInTrueAndStatus(STATUS_ACTIVE).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<SkillRecord> findGlobal() {
        return repository.findByIsGlobalTrueAndStatus(STATUS_ACTIVE).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public Optional<SkillRecord> findByCode(String skillCode) {
        return repository.findByCodeAndStatus(skillCode, STATUS_ACTIVE).map(this::toRecord);
    }

    @Override
    public Optional<SkillRecord> findBySkillId(Long skillId) {
        return repository.findByIdAndStatus(skillId, STATUS_ACTIVE).map(this::toRecord);
    }

    private SkillRecord toRecord(SkillDefinition entity) {
        return new SkillRecord(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getAgentId(),
                entity.getTriggerIntent(),
                entity.getSystemPrompt(),
                entity.getInstructions(),
                entity.getPriority(),
                Boolean.TRUE.equals(entity.getBuiltIn()),
                Boolean.TRUE.equals(entity.getIsGlobal()));
    }
}

@Repository
interface SkillDefinitionRepository extends CrudEntityRepository<SkillDefinition> {
    List<SkillDefinition> findByAgentIdAndStatus(Long agentId, String status);

    List<SkillDefinition> findByBuiltInTrueAndStatus(String status);

    List<SkillDefinition> findByIsGlobalTrueAndStatus(String status);

    Optional<SkillDefinition> findByNameAndBuiltInTrue(String name);

    Optional<SkillDefinition> findByCodeAndStatus(String code, String status);

    Optional<SkillDefinition> findByIdAndStatus(Long id, String status);
}
