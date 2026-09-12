package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;

/** 模型回合结束后独立判断业务任务是否真正完成。 */
public interface CompletionValidator {

    CompletionDecision validate(ValidationRequest request);

    record ValidationRequest(
            Task task,
            CompletionCriteria criteria,
            Optional<TaskPlan> taskPlan,
            List<ExecutionEvent> events) {

        public ValidationRequest {
            Objects.requireNonNull(criteria, "criteria 不能为空");
            taskPlan = Objects.requireNonNull(taskPlan, "taskPlan Optional 不能为空");
            events = List.copyOf(Objects.requireNonNull(events, "events 不能为空"));
        }
    }
}
