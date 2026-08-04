package com.xuejiai.aaf.module.ai.aigc.execution.api;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** AIGC 动作与异步 Agent/Workflow runtime 之间的稳定执行契约。 */
public final class AigcRuntimeExecution {

    private AigcRuntimeExecution() {}

    public record Command(
            Long executionRunId,
            Long projectId,
            Long projectObjectId,
            Long orgId,
            Long workspaceId,
            Long userId,
            String targetRef,
            String actionKey,
            String prompt,
            String idempotencyKey,
            Map<String, Object> input) {

        public Command {
            Objects.requireNonNull(executionRunId, "executionRunId 不能为空");
            Objects.requireNonNull(projectId, "projectId 不能为空");
            Objects.requireNonNull(orgId, "orgId 不能为空");
            Objects.requireNonNull(userId, "userId 不能为空");
            targetRef = requireText(targetRef, "targetRef");
            actionKey = requireText(actionKey, "actionKey");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
            prompt = prompt == null ? "" : prompt;
            input = input == null ? Map.of() : Map.copyOf(input);
        }
    }

    public record Result(String output, Map<String, Object> metadata) {

        public Result {
            output = output == null ? "" : output;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record Submission(
            String runtimeTraceId, String runtimeRunId, CompletionStage<Result> completion) {

        public Submission {
            runtimeTraceId = requireText(runtimeTraceId, "runtimeTraceId");
            runtimeRunId = requireText(runtimeRunId, "runtimeRunId");
            Objects.requireNonNull(completion, "completion 不能为空");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
