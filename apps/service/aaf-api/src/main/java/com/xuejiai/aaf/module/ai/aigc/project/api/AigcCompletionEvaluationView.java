package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcCompletionEvaluationView(
        String publicationPolicy,
        boolean satisfied,
        long activeWorkCount,
        List<Long> requiredChannelSpecVersionIds,
        List<Long> succeededChannelSpecVersionIds,
        List<String> blockers) {

    public AigcCompletionEvaluationView {
        requiredChannelSpecVersionIds = List.copyOf(requiredChannelSpecVersionIds);
        succeededChannelSpecVersionIds = List.copyOf(succeededChannelSpecVersionIds);
        blockers = List.copyOf(blockers);
    }
}
