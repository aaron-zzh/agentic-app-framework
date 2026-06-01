package com.xuejiai.aaf.module.ai.skill;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

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

    private final SkillDefinitionRepository repository;

    @Override
    public List<SkillRecord> findByAssistant(String assistantId) {
        return repository.findByAssistantIdAndStatus(assistantId, "active").stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<SkillRecord> findByAgentId(String agentId) {
        return repository.findByAgentIdAndStatus(agentId, "active").stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<SkillRecord> findBuiltIn() {
        return repository.findByBuiltInTrueAndStatus("active").stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public Optional<SkillRecord> findBySkillId(String skillId) {
        return repository.findBySkillId(skillId).map(this::toRecord);
    }

    private SkillRecord toRecord(SkillDefinition e) {
        return new SkillRecord(
                e.getSkillId(),
                e.getAssistantId(),
                e.getName(),
                e.getDescription(),
                e.getAgentId(),
                e.getTriggerIntent(),
                e.getSystemPrompt(),
                e.getInstructions(),
                e.getTools(),
                e.getPriority(),
                Boolean.TRUE.equals(e.getBuiltIn()));
    }
}

@Repository
interface SkillDefinitionRepository extends JpaRepository<SkillDefinition, Long> {
    List<SkillDefinition> findByAssistantIdAndStatus(String assistantId, String status);

    List<SkillDefinition> findByAgentIdAndStatus(String agentId, String status);

    List<SkillDefinition> findByBuiltInTrueAndStatus(String status);

    Optional<SkillDefinition> findBySkillId(String skillId);
}
