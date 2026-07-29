package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Comparator;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;

/** P2 的确定性前注意路由；后续小模型分流仍必须收敛到同一 SkillRoute。 */
public final class DefaultSkillRouter implements SkillRouter {

    @Override
    public Optional<SkillRoute> route(AssistantDefinition definition, String input) {
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
