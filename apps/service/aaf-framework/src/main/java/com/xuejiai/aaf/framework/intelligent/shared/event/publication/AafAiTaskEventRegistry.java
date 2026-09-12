package com.xuejiai.aaf.framework.intelligent.shared.event.publication;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.AafAiTaskEvent.Audience;

/** 内部执行事件到公开稳定类型的唯一注册表。 */
public final class AafAiTaskEventRegistry {

    private AafAiTaskEventRegistry() {}

    public static Descriptor descriptor(ExecutionEventType type) {
        var publicType =
                switch (type) {
                    case EXECUTION_STARTED -> "aaf.task.started";
                    case EXECUTION_PROMOTED -> "aaf.task.promoted";
                    case EXECUTION_COMPLETED -> "aaf.task.completed";
                    case EXECUTION_FAILED -> "aaf.task.failed";
                    case EXECUTION_CANCELED -> "aaf.task.canceled";
                    case EXECUTION_PAUSED -> "aaf.task.paused";
                    case EXECUTION_RESUMED -> "aaf.task.resumed";
                    case COMMAND_REJECTED -> "aaf.task.rejected";
                    case RUN_STARTED -> "aaf.run.started";
                    case RUN_COMPLETED -> "aaf.run.completed";
                    case RUN_FAILED -> "aaf.run.failed";
                    case MESSAGE_STARTED -> "aaf.message.started";
                    case MESSAGE_DELTA -> "aaf.message.progress";
                    case MESSAGE_COMPLETED -> "aaf.message.completed";
                    case MESSAGE_BLOCK_COMPLETED -> "aaf.message.block_completed";
                    case MODEL_CALL_STARTED -> "aaf.model.started";
                    case MODEL_CALL_COMPLETED -> "aaf.model.completed";
                    case MODEL_CALL_FAILED -> "aaf.model.failed";
                    case TOOL_CALL_STARTED -> "aaf.tool.started";
                    case TOOL_CALL_ARGS_DELTA -> "aaf.tool.args_progress";
                    case TOOL_CALL_ARGS_COMPLETED -> "aaf.tool.args_completed";
                    case TOOL_CALL_COMPLETED -> "aaf.tool.completed";
                    case TOOL_CALL_FAILED -> "aaf.tool.failed";
                    case TOOL_RESULT_STARTED -> "aaf.tool.result_started";
                    case TOOL_RESULT_DELTA -> "aaf.tool.result_progress";
                    case AUTHORIZATION_REQUESTED -> "aaf.authorization.requested";
                    case AUTHORIZATION_GRANTED -> "aaf.authorization.granted";
                    case AUTHORIZATION_DENIED -> "aaf.authorization.denied";
                    case AUTHORIZATION_REVOKED -> "aaf.authorization.revoked";
                    case APPROVAL_REQUESTED -> "aaf.hitl.requested";
                    case APPROVAL_RESOLVED -> "aaf.hitl.resolved";
                    case EXTERNAL_EXECUTION_REQUESTED -> "aaf.external_execution.requested";
                    case EXTERNAL_EXECUTION_SUPPLIED -> "aaf.external_execution.supplied";
                    case CLARIFICATION_REQUESTED -> "aaf.clarification.requested";
                    case CLARIFICATION_UPDATED -> "aaf.clarification.updated";
                    case CLARIFICATION_RESOLVED -> "aaf.clarification.resolved";
                    case CLARIFICATION_CANCELED -> "aaf.clarification.canceled";
                    case CLARIFICATION_EXPIRED -> "aaf.clarification.expired";
                    case ITERATION_EVALUATED -> "aaf.iteration.evaluated";
                    case ITERATION_STOPPED -> "aaf.iteration.stopped";
                    case EXECUTOR_PLAN_CREATED -> "aaf.executor_plan.created";
                    case EXECUTOR_PLAN_SUBMITTED -> "aaf.executor_plan.submitted";
                    case EXECUTOR_PLAN_EXECUTION_STARTED -> "aaf.executor_plan.execution_started";
                    case EXECUTOR_PLAN_COMPLETED -> "aaf.executor_plan.completed";
                    case EXECUTOR_PLAN_FAILED -> "aaf.executor_plan.failed";
                    case EXECUTOR_PLAN_CANCELLED -> "aaf.executor_plan.cancelled";
                    case EXECUTOR_PLAN_STEP_STARTED -> "aaf.executor_plan.step_started";
                    case EXECUTOR_PLAN_STEP_COMPLETED -> "aaf.executor_plan.step_completed";
                    case EXECUTOR_PLAN_STEP_FAILED -> "aaf.executor_plan.step_failed";
                    case VALIDATION_STARTED -> "aaf.validation.started";
                    case VALIDATION_COMPLETED -> "aaf.validation.completed";
                    case VALIDATION_FAILED -> "aaf.validation.failed";
                    case RECOVERY_STARTED -> "aaf.recovery.started";
                    case RECOVERY_COMPLETED -> "aaf.recovery.completed";
                    case OWNERSHIP_TRANSFERRED -> "aaf.ownership.transferred";
                    case TASK_STATUS_CHANGED -> "aaf.task.status_changed";
                    case CONTROL_MODE_CHANGED -> "aaf.control_mode.changed";
                    case ROLE_RESOLVED -> "aaf.role.resolved";
                    case INPUT_CANCELED -> "aaf.input.canceled";
                    case INPUT_MODIFIED -> "aaf.input.modified";
                    case INPUT_SUPPLEMENTED -> "aaf.input.supplemented";
                    case INPUT_UNRELATED -> "aaf.input.unrelated";
                };
        return new Descriptor(publicType, 1, Audience.END_USER);
    }

    public record Descriptor(String type, int version, Audience audience) {}
}
