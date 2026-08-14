package com.xuejiai.aaf.framework.task;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.org.OrgIgnore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 受管异步任务唯一投递器，把数据库 PENDING 主记录可靠投递到 Redis Stream。 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "aaf.task.queue",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
public class AsyncTaskDispatcher {

    private static final int DISPATCH_BATCH_SIZE = 100;

    private final AsyncQueueTaskService asyncTaskService;

    @OrgIgnore
    @Scheduled(fixedDelayString = "${aaf.task.queue.dispatch-poll-interval-ms:1000}")
    @DistributedLock(key = "async-task-dispatcher", ttlSeconds = 30)
    public void dispatchPending() {
        for (var index = 0; index < DISPATCH_BATCH_SIZE; index++) {
            try {
                if (!asyncTaskService.dispatchNextPending()) {
                    return;
                }
            } catch (RuntimeException e) {
                log.warn("受管异步任务投递失败，保留 PENDING 等待下轮重试: {}", e.getMessage());
                return;
            }
        }
    }
}
