package com.xuejiai.aaf.module.ai.chat.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.task.TaskEventBus;
import com.xuejiai.aaf.module.ai.chat.domain.TaskEvent;
import com.xuejiai.aaf.module.ai.chat.repository.TaskEventRepository;

import lombok.RequiredArgsConstructor;

/** {@link TaskEventBus} 实现：写库（ai_task_event）+ SSE 实时广播。 */
@Component
@RequiredArgsConstructor
public class PersistentTaskEventBus implements TaskEventBus {

    private final TaskEventRepository eventRepository;
    private final TaskEventStreamService eventStreamService;

    @Override
    @Transactional
    public void publish(
            Long taskId, Long executionId, String subtaskKey, String type, String payloadJson) {
        var event = TaskEvent.of(taskId, executionId, subtaskKey, type, payloadJson);
        eventRepository.save(event);
        eventStreamService.broadcast(event);
    }
}
