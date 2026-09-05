package com.xuejiai.aaf.module.ai.aigc.execution.event;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionApi;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectArchivedEvent;

import lombok.RequiredArgsConstructor;

/** 项目归档后的 execution 整树取消策略。 */
@Component
@RequiredArgsConstructor
public class AigcProjectArchivedListener {

    private final AigcExecutionRunRepository runRepository;
    private final AigcExecutionApi executionApi;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onArchived(AigcProjectArchivedEvent event) {
        runRepository
                .findByProjectIdAndStatusIn(
                        event.projectId(),
                        List.of(
                                AigcExecutionRunStatus.PENDING_BIND,
                                AigcExecutionRunStatus.PENDING,
                                AigcExecutionRunStatus.RUNNING))
                .stream()
                .map(run -> run.getRootExecutionRunId())
                .distinct()
                .forEach(rootId -> executionApi.cancel(rootId, "项目已归档"));
    }
}
