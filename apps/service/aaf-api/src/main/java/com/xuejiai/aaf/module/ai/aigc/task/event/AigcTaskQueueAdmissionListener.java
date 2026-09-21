package com.xuejiai.aaf.module.ai.aigc.task.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskStatusEnum;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskQueueDispatcher;

import lombok.RequiredArgsConstructor;

/**
 * 任务终态后推进同一用户的并发窗口队列。
 *
 * <p>只有真正释放槽位的终态（成功/失败）才派发下一个排队任务；{@code NEEDS_RECONCILIATION} 虽然也发布终态事件，但供应商侧结果不确定、
 * 仍占槽，不得据此放行新任务。派发只是"再尝试一次认领"，真实准入判定仍在认领事务内完成。
 */
@Component
@RequiredArgsConstructor
public class AigcTaskQueueAdmissionListener {

    private final AigcTaskQueueDispatcher queueDispatcher;

    @OrgIgnore
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTerminal(AigcTaskTerminalEvent event) {
        if (!AigcTaskStatusEnum.CONCURRENCY_RELEASING_CODES.contains(event.status())) {
            return;
        }
        queueDispatcher.dispatchNext(event.userId());
    }
}
