package com.xuejiai.aaf.module.ai.skill;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
    public List<SkillRecord> findByAgentId(Long agentId) {
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
    public List<SkillRecord> findGlobal() {
        return repository.findByIsGlobalTrueAndStatus("active").stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public Optional<SkillRecord> findBySkillId(Long skillId) {
        return repository.findById(skillId).map(this::toRecord);
    }

    private SkillRecord toRecord(SkillDefinition e) {
        return new SkillRecord(
                e.getId(),
                e.getName(),
                e.getDescription(),
                e.getAgentId(),
                e.getTriggerIntent(),
                e.getSystemPrompt(),
                e.getInstructions(),
                e.getPriority(),
                Boolean.TRUE.equals(e.getBuiltIn()),
                Boolean.TRUE.equals(e.getIsGlobal()));
    }
}

@Repository
interface SkillDefinitionRepository
        extends JpaRepository<SkillDefinition, Long>, JpaSpecificationExecutor<SkillDefinition> {
    List<SkillDefinition> findByAgentIdAndStatus(Long agentId, String status);

    List<SkillDefinition> findByBuiltInTrueAndStatus(String status);

    List<SkillDefinition> findByIsGlobalTrueAndStatus(String status);

    java.util.Optional<SkillDefinition> findByNameAndBuiltInTrue(String name);

    java.util.Optional<SkillDefinition> findByCode(String code);

    /** 查询全局技能（owner_id 为空）+ 指定 owner 的私有技能。 */
    @org.springframework.data.jpa.repository.Query(
            "SELECT s FROM SkillDefinition s WHERE s.status = 'active' "
                    + "AND (s.ownerId IS NULL OR s.ownerId = :ownerId)")
    List<SkillDefinition> findGlobalOrOwned(
            @org.springframework.data.repository.query.Param("ownerId") Long ownerId);

    /**
     * 按 category 过滤的全局技能列表（owner_id 为空），按 priority 降序。
     *
     * @param category 分类，null=不按分类过滤
     * @param activeOnly true=仅 status='active'
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT s FROM SkillDefinition s WHERE s.ownerId IS NULL "
                    + "AND (:category IS NULL OR s.category = :category) "
                    + "AND (:activeOnly = false OR s.status = 'active') "
                    + "ORDER BY s.priority DESC")
    List<SkillDefinition> findByCategoryFilter(
            @org.springframework.data.repository.query.Param("category") String category,
            @org.springframework.data.repository.query.Param("activeOnly") boolean activeOnly);
}
