package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Comparator;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 模型语义前注意不可用时的确定性兜底路由。 */
public final class DefaultSkillRouter implements SkillRouter {

    @Override
    public Optional<SkillRoute> route(
            AssistantDefinition definition, String input, UserId userId) {
        var matched =
                definition.skillRoutes().stream()
                        .filter(route -> !route.defaultRoute())
                        .filter(route -> route.matches(input))
                        .max(Comparator.comparingInt(SkillRoute::priority));
        return matched.or(
                () ->
                        definition.skillRoutes().stream()
                                .filter(SkillRoute::defaultRoute)
                                .findFirst());
    }
}
