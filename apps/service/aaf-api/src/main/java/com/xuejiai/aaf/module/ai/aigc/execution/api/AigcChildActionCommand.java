package com.xuejiai.aaf.module.ai.aigc.execution.api;

import java.util.List;

public record AigcChildActionCommand(
        Long projectId,
        Long projectObjectId,
        String actionKey,
        Long parentExecutionRunId,
        Long rootExecutionRunId,
        String workflowNodeKey,
        String prompt,
        List<Long> attachmentMediaVersionIds,
        String idempotencyKey) {

    public AigcChildActionCommand {
        attachmentMediaVersionIds =
                attachmentMediaVersionIds == null
                        ? List.of()
                        : List.copyOf(attachmentMediaVersionIds);
    }
}
