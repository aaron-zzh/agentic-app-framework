package com.xuejiai.aaf.framework.task;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.org.OrgIgnore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 清理过期的异步任务终态记录。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskHistoryCleaner {

    private static final Set<AsyncTaskStatus> TERMINAL_STATUSES =
            Set.of(AsyncTaskStatus.SUCCEEDED, AsyncTaskStatus.FAILED);

    private final AsyncTaskRepository repository;

    @Value("${aaf.task.async-retention-days:30}")
    private int retentionDays;

    /** 每天凌晨清理过期终态任务；负数保留期禁用清理。 */
    @OrgIgnore
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupHistory() {
        if (retentionDays < 0) {
            return;
        }
        var threshold = LocalDateTime.now().minusDays(retentionDays);
        var deleted = repository.deleteCompletedBefore(TERMINAL_STATUSES, threshold);
        if (deleted > 0) {
            log.info("清理过期异步任务记录 {} 条（保留 {} 天）", deleted, retentionDays);
        }
    }
}
