package com.xuejiai.aaf.framework.engine.skill;

import java.util.List;
import java.util.Optional;

/**
 * 技能数据存储契约——引擎层通过此接口获取技能数据，不直接依赖 Repository。
 *
 * <p>标准分层模式：
 *
 * <pre>
 * engine/skill/
 *   ├── SkillMatchEngine.java   ← 领域逻辑（匹配算法）
 *   ├── SkillStore.java         ← 数据契约接口（引擎层定义）
 *   └── （无 Entity/Repository/Controller）
 *
 * api/module/.../skill/
 *   ├── SkillDefinition.java    ← JPA Entity
 *   ├── SkillRepository.java    ← Repository
 *   ├── SkillStoreImpl.java     ← SkillStore 实现（桥接 Repository）
 *   └── SkillController.java    ← CRUD 管理接口
 * </pre>
 */
public interface SkillStore {

    /** 查询绑定了指定 Agent 的活跃技能 */
    List<SkillRecord> findByAgentId(Long agentId);

    /** 查询全局内置技能 */
    List<SkillRecord> findBuiltIn();

    /** 查询全局技能（global=true，注入所有 Agent） */
    List<SkillRecord> findGlobal();

    /** 按 id 查询 */
    Optional<SkillRecord> findBySkillId(Long skillId);

    /** 技能数据记录（引擎层的数据视图，与 Entity 解耦）。 技能为全局/可复用定义，不再直挂助理，也不再绑定工具。 */
    record SkillRecord(
            Long skillId,
            String name,
            String description,
            Long agentId,
            String triggerIntent,
            String systemPrompt,
            String instructions,
            int priority,
            boolean builtIn,
            boolean global) {}
}
