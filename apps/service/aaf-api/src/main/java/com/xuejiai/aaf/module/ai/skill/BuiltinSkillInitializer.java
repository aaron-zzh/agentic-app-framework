package com.xuejiai.aaf.module.ai.skill;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.skill.BuiltinSkills;
import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 内置技能初始化器：应用启动时将 BuiltinSkills 枚举 upsert 到数据库。 - 不存在 → INSERT - 存在且版本相同 → 跳过 - 存在但版本升级 →
 * UPDATE（仅更新内置记录，不影响用户覆盖版本）
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinSkillInitializer implements ApplicationRunner {

    private final SkillDefinitionRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        for (var builtin : BuiltinSkills.values()) {
            // 用 name + builtIn=true 作为唯一标识（skillId 字段已删除）
            var existing = repository.findByNameAndBuiltInTrue(builtin.name);
            if (existing.isEmpty()) {
                repository.save(toEntity(builtin));
                log.info("初始化内置技能: {}", builtin.name);
            } else {
                var entity = existing.get();
                if (!builtin.version.equals(entity.getSkillVersion())) {
                    updateEntity(entity, builtin);
                    repository.save(entity);
                    log.info("更新内置技能: {} -> v{}", builtin.name, builtin.version);
                }
            }
        }
    }

    private SkillDefinition toEntity(BuiltinSkills builtin) {
        var entity = new SkillDefinition();
        entity.setName(builtin.name);
        entity.setDescription(builtin.description);
        // agentId 为 null（内置技能由 Assistant 直接处理）
        entity.setTriggerIntent(builtin.triggerIntent);
        entity.setSystemPrompt(builtin.systemPrompt);
        entity.setSkillVersion(builtin.version);
        entity.setPriority(builtin.priority);
        entity.setBuiltIn(true);
        entity.setStatus("active");
        return entity;
    }

    private void updateEntity(SkillDefinition entity, BuiltinSkills builtin) {
        entity.setName(builtin.name);
        entity.setDescription(builtin.description);
        entity.setTriggerIntent(builtin.triggerIntent);
        entity.setSystemPrompt(builtin.systemPrompt);
        entity.setSkillVersion(builtin.version);
        entity.setPriority(builtin.priority);
    }
}
