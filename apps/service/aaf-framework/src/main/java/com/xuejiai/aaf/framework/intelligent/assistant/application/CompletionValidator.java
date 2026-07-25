package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;

/** 模型回合结束后独立判断业务任务是否真正完成。 */
public interface CompletionValidator {

    CompletionDecision validate(ValidationRequest request);

    record ValidationRequest(
            AssistantTask task,
            SkillRoute route,
            CompletionCriteria criteria,
            Optional<TaskBoard> taskBoard,
            List<ExecutionEvent> events) {

        public ValidationRequest {
            Objects.requireNonNull(task, "task 不能为空");
            Objects.requireNonNull(route, "route 不能为空");
            Objects.requireNonNull(criteria, "criteria 不能为空");
            taskBoard = Objects.requireNonNull(taskBoard, "taskBoard Optional 不能为空");
            events = List.copyOf(Objects.requireNonNull(events, "events 不能为空"));
        }
    }
}
