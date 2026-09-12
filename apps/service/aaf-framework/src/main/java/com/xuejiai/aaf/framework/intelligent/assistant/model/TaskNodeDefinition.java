package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan.AssistantTarget;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan.TaskNode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan.TaskNode.Kind;

/** 冻结计划中的不可变节点定义；运行状态与当前 attempt 不进入该 JSON 值对象。 */
public record TaskNodeDefinition(
        Kind kind,
        String description,
        Map<String, TaskPlanDraft.InputBinding> inputBindings,
        String roleKey,
        String skillKey,
        AssistantTarget assistantTarget,
        TaskModelSelection modelSelection,
        boolean retryable,
        int maxAttempts) {

    public TaskNodeDefinition {
        Objects.requireNonNull(kind, "kind 不能为空");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description 不能为空白");
        }
        inputBindings = Map.copyOf(Objects.requireNonNull(inputBindings, "inputBindings 不能为空"));
        Objects.requireNonNull(modelSelection, "modelSelection 不能为空");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 必须大于 0");
        }
    }

    public static TaskNodeDefinition from(TaskNode node) {
        Objects.requireNonNull(node, "node 不能为空");
        return new TaskNodeDefinition(
                node.kind(),
                node.description(),
                node.inputBindings(),
                node.roleKey(),
                node.skillKey(),
                node.assistantTarget(),
                node.modelSelection(),
                node.retryable(),
                node.maxAttempts());
    }
}
