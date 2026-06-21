package com.xuejiai.aaf.module.ai.aigc.task.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoTaskResult.TaskStatus;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频生成任务状态定时同步。
 *
 * <p>轮询 PENDING 的 VIDEO 类型任务，查询第三方结果后更新 AigcTask 状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTaskSyncJob {

    private final AigcTaskRepository taskRepo;
    private final AigcTaskService aigcTaskService;
    private final AiServiceRegistry aiServiceRegistry;
    private final ConfigCacheManager configCacheManager;

    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void sync() {
        var tasks = taskRepo.findByStatusAndType("PENDING", "VIDEO");
        if (tasks.isEmpty()) return;

        for (var task : tasks) {
            try {
                String thirdTaskId = task.getTaskId();
                if (thirdTaskId == null || task.getModel() == null) continue;

                var aiModel = configCacheManager.getAiModelByModelId(task.getModel());
                if (aiModel == null) continue;

                var svc = aiServiceRegistry.get(VideoGenerationService.class, aiModel);
                var result = svc.query(thirdTaskId);

                if (result.getStatus() == TaskStatus.SUCCEEDED) {
                    log.info(
                            "[VideoSync] 任务完成: aigcTaskId={}, url={}",
                            task.getId(),
                            result.getVideoUrl());
                    aigcTaskService.completeTask(thirdTaskId, result);
                } else if (result.getStatus() == TaskStatus.FAILED
                        || result.getStatus() == TaskStatus.CANCELED) {
                    log.warn(
                            "[VideoSync] 任务失败: aigcTaskId={}, status={}",
                            task.getId(),
                            result.getStatus());
                    aigcTaskService.failTask(thirdTaskId, "视频生成失败: " + result.getStatus());
                }
                // PENDING/RUNNING 继续等待
            } catch (Exception e) {
                log.warn("[VideoSync] 同步失败: taskId={}, err={}", task.getId(), e.getMessage());
            }
        }
    }
}
