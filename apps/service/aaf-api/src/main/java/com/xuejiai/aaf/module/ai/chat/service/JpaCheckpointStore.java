package com.xuejiai.aaf.module.ai.chat.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.task.CheckpointStore;
import com.xuejiai.aaf.module.ai.chat.domain.TaskCheckpoint;
import com.xuejiai.aaf.module.ai.chat.repository.TaskCheckpointRepository;

import lombok.RequiredArgsConstructor;

/** {@link CheckpointStore} 的 JPA 实现，持久化到 ai_task_checkpoint 表。 */
@Component
@RequiredArgsConstructor
public class JpaCheckpointStore implements CheckpointStore {

    private final TaskCheckpointRepository repository;

    @Override
    public void save(Long executionId, String scope, int stepIndex, String stateJson) {
        var cp = new TaskCheckpoint();
        cp.setExecutionId(executionId);
        cp.setScope(scope);
        cp.setStepIndex(stepIndex);
        cp.setStateJson(stateJson);
        repository.save(cp);
    }

    @Override
    public Optional<String> loadLatest(Long executionId) {
        return repository
                .findFirstByExecutionIdOrderByStepIndexDesc(executionId)
                .map(TaskCheckpoint::getStateJson);
    }
}
