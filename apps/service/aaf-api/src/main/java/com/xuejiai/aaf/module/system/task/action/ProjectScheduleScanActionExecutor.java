package com.xuejiai.aaf.module.system.task.action;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectScheduleService;
import com.xuejiai.aaf.module.system.task.domain.ScheduledTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目定时任务扫描执行器。
 *
 * <p>在 sys_scheduled_task 中注册为 "project_assistant_schedule_scan"，定期扫描到期的项目定时任务并触发。
 *
 * @author Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectScheduleScanActionExecutor implements ScheduledActionExecutor, Runnable {

    private final AigcProjectScheduleService scheduleService;

    @Override
    public String actionType() {
        return "PROJECT_SCHEDULE_SCAN";
    }

    @Override
    public void run() {
        scan();
    }

    @Override
    public void execute(ScheduledTask task) {
        scan();
    }

    private void scan() {
        try {
            // 每次扫描最多处理 50 个到期任务
            scheduleService.scanAndTrigger(50);
        } catch (Exception e) {
            log.error("[ProjectScheduleScan] 扫描失败", e);
            throw e;
        }
    }
}
