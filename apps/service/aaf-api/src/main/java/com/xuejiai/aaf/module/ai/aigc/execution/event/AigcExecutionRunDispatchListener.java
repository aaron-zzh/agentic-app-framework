package com.xuejiai.aaf.module.ai.aigc.execution.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcActionCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 在创建 ExecutionRun 的事务提交后派发实际执行。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigcExecutionRunDispatchListener {

    private final AigcActionCommandService commandService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRequested(AigcExecutionRunDispatchRequestedEvent event) {
        try {
            commandService.dispatchDeferred(event.runId(), event.command());
        } catch (RuntimeException exception) {
            log.error("ExecutionRun 提交后派发失败，runId={}", event.runId(), exception);
            commandService.failDeferredDispatch(event.runId(), exception.getMessage());
        }
    }
}
