package com.xuejiai.aaf.module.system.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.domain.ScheduledTask;
import com.xuejiai.aaf.module.system.repository.ScheduledTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 计划任务业务逻辑。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final ScheduledTaskRepository scheduledTaskRepository;
    private final NotificationService notificationService;

    /** 连续失败阈值 */
    @Value("${aaf.scheduled-task.max-fail-count:3}")
    private int maxFailCount;

    /** 查询所有计划任务 */
    public List<ScheduledTask> list() {
        return scheduledTaskRepository.findAll();
    }

    /** 暂停任务 */
    @Transactional
    public void pause(Long id) {
        var task = getById(id);
        task.setStatus("paused");
        scheduledTaskRepository.save(task);
    }

    /** 恢复任务 */
    @Transactional
    public void resume(Long id) {
        var task = getById(id);
        task.setStatus("active");
        task.setFailCount(0);
        scheduledTaskRepository.save(task);
    }

    /** 手动触发执行 */
    @Transactional
    public void runNow(Long id) {
        var task = getById(id);
        try {
            // 记录执行时间
            task.setLastRun(LocalDateTime.now());
            task.setFailCount(0);
            task.setStatus("active");
            scheduledTaskRepository.save(task);
            log.info("手动触发计划任务: {} ({})", task.getName(), task.getType());
        } catch (Exception e) {
            recordFailure(task);
            throw e;
        }
    }

    /** 记录任务失败，连续失败超阈值自动暂停并通知 */
    @Transactional
    public void recordFailure(ScheduledTask task) {
        task.setFailCount(task.getFailCount() + 1);
        if (task.getFailCount() >= maxFailCount) {
            task.setStatus("failed");
            log.warn("计划任务 [{}] 连续失败 {} 次，已自动暂停", task.getName(), task.getFailCount());
            // 通知管理员（userId=1 为系统管理员）
            notificationService.sendSystemNotification(
                    1L,
                    "计划任务告警",
                    "任务 [%s] 连续失败 %d 次，已自动暂停".formatted(task.getName(), task.getFailCount()));
        }
        scheduledTaskRepository.save(task);
    }

    private ScheduledTask getById(Long id) {
        return scheduledTaskRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "计划任务不存在"));
    }
}
