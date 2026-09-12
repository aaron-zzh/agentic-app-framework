package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

/** 冻结计划中的显式有向依赖边。 */
public record TaskDependency(
        TaskId taskId,
        String planId,
        int planRevision,
        String predecessorNodeId,
        String successorNodeId,
        DependencyType type) {

    public TaskDependency {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        planId = requireText(planId, "planId");
        predecessorNodeId = requireText(predecessorNodeId, "predecessorNodeId");
        successorNodeId = requireText(successorNodeId, "successorNodeId");
        Objects.requireNonNull(type, "dependency type 不能为空");
        if (planRevision < 1) throw new IllegalArgumentException("planRevision 必须大于 0");
        if (predecessorNodeId.equals(successorNodeId)) {
            throw new IllegalArgumentException("依赖边不能指向自身");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空白");
        return value;
    }

    public enum DependencyType {
        REQUIRED,
        OPTIONAL
    }
}
