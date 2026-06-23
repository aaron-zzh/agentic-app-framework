package com.xuejiai.aaf.module.ai.aigc.task.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService.Model3dTaskResult.TaskStatus;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 3D 模型生成任务状态定时同步。
 *
 * <p>轮询 PENDING 的 MODEL_3D 类型任务，查询第三方结果后更新 AigcTask 状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Model3dTaskSyncJob {

    private final AigcTaskRepository taskRepo;
    private final AigcTaskService aigcTaskService;
    private final Model3dGenerationService model3dGenerationService;
    private final PermissionExecutionService permissionExecutionService;

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void sync() {
        var tasks = taskRepo.findByStatusAndType("PENDING", "MODEL_3D");
        if (tasks.isEmpty()) return;

        for (var task : tasks) {
            try {
                String thirdTaskId = task.getTaskId();
                if (thirdTaskId == null) continue;

                var result = model3dGenerationService.query(thirdTaskId);
                if (result.status() == TaskStatus.SUCCEEDED) {
                    String modelUrl =
                            result.modelUrl() != null ? result.modelUrl() : result.baseModelUrl();
                    log.info("[Model3dSync] 任务完成: aigcTaskId={}, url={}", task.getId(), modelUrl);
                    permissionExecutionService.runAsOwner(
                            task.getUserId(),
                            "3D任务完成",
                            () -> aigcTaskService.completeTask(thirdTaskId, modelUrl));
                } else if (result.status() == TaskStatus.FAILED) {
                    log.warn("[Model3dSync] 任务失败: aigcTaskId={}", task.getId());
                    permissionExecutionService.runAsOwner(
                            task.getUserId(),
                            "3D任务失败",
                            () -> aigcTaskService.failTask(thirdTaskId, "3D 生成失败"));
                }
                // 其他状态继续等待
            } catch (Exception e) {
                log.warn("[Model3dSync] 同步失败: taskId={}, err={}", task.getId(), e.getMessage());
            }
        }
    }
}
