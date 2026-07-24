package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.time.Instant;
import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceReference;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;

/** 选择本次实际生效上下文并返回引用清单的边界。 */
public interface EffectiveContextPort {

    EffectiveContextManifest resolve(
            AssistantDefinition definition,
            AssistantTask task,
            SkillRoute route,
            List<SourceReference> candidates,
            Instant at);
}
