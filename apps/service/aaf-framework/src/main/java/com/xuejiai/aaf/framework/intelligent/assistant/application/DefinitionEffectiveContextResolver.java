package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceReference;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceType;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;
import com.xuejiai.aaf.framework.intelligent.assistant.port.EffectiveContextPort;

/** P2 基础解析器：合并角色规则、命中 Skill 与上层已选择的资料引用。 */
public final class DefinitionEffectiveContextResolver implements EffectiveContextPort {

    @Override
    public EffectiveContextManifest resolve(
            AssistantDefinition definition,
            AssistantTask task,
            SkillRoute route,
            List<SourceReference> candidates,
            Instant at) {
        var unique = new LinkedHashMap<String, SourceReference>();
        var roleReference =
                new SourceReference(
                        SourceType.RULE,
                        definition.role().key(),
                        definition.version().toString(),
                        "ASSISTANT",
                        "当前 Assistant 角色与职责边界",
                        definition.role().name(),
                        false);
        var skillReference =
                new SourceReference(
                        SourceType.SKILL,
                        route.skillKey(),
                        Long.toString(route.agentDefinitionVersion()),
                        "TASK",
                        "用户意图命中该技能路由",
                        route.name(),
                        false);
        add(unique, roleReference);
        add(unique, skillReference);
        candidates.forEach(reference -> add(unique, reference));
        return new EffectiveContextManifest(
                task.taskId(),
                definition.assistantId(),
                definition.version(),
                route.skillKey(),
                new ArrayList<>(unique.values()),
                at);
    }

    private static void add(
            LinkedHashMap<String, SourceReference> values, SourceReference reference) {
        values.put(reference.type() + ":" + reference.sourceKey(), reference);
    }
}
