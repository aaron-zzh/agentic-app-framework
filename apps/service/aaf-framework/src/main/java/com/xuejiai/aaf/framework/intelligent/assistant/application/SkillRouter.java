package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** Assistant 前注意与技能路由边界。 */
public interface SkillRouter {

    Optional<SkillRoute> route(AssistantDefinition definition, String input, UserId userId);
}
