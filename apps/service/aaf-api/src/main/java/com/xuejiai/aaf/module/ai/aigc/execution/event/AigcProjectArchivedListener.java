package com.xuejiai.aaf.module.ai.aigc.execution.event;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeCancellationPort;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionTaskRefRepository;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectArchivedEvent;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskApi;

import lombok.RequiredArgsConstructor;

/** 项目归档后的 execution 取消策略。 */
@Component
@RequiredArgsConstructor
public class AigcProjectArchivedListener {

    private final AigcExecutionRunRepository runRepository;
    private final AigcExecutionTaskRefRepository taskRefRepository;
    private final AigcTaskApi taskApi;
    private final AigcRuntimeCancellationPort runtimeCancellationPort;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onArchived(AigcProjectArchivedEvent event) {
        runRepository
                .findByProjectIdAndStatusIn(event.projectId(), List.of("pending", "running"))
                .stream()
                .map(run -> run.getId())
                .forEach(
                        runId -> {
                            var run = runRepository.findLockedById(runId).orElse(null);
                            if (run == null
                                    || !("pending".equals(run.getStatus())
                                            || "running".equals(run.getStatus()))) {
                                return;
                            }
                            var runtimeTraceId = runtimeTraceId(run.getOutputPayload());
                            if (runtimeTraceId != null) {
                                runtimeCancellationPort.cancel(
                                        run.getTargetType(), runtimeTraceId, "项目已归档");
                            }
                            taskRefRepository
                                    .findByExecutionRunIdAndDeletedFalseOrderBySortOrderAsc(
                                            run.getId())
                                    .forEach(ref -> taskApi.cancel(ref.getTaskId(), "项目已归档"));
                            run.setStatus("canceled");
                            run.setErrorMessage("项目已归档");
                            run.setEndTime(LocalDateTime.now());
                            run.setVersion(run.getVersion() + 1);
                            runRepository.save(run);
                        });
    }

    private String runtimeTraceId(java.util.Map<String, Object> output) {
        return output == null || output.get("runtimeTraceId") == null
                ? null
                : String.valueOf(output.get("runtimeTraceId"));
    }
}
