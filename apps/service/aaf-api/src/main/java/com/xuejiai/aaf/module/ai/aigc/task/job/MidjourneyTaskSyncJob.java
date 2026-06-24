package com.xuejiai.aaf.module.ai.aigc.task.job;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.intelligent.ai.image.MidjourneyAsyncImageService;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Midjourney 任务状态定时同步。
 *
 * <p>轮询 PENDING 的 IMAGE 类型任务，若对应模型为 MIDJOURNEY，则调用 Midjourney API 查询并更新任务状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MidjourneyTaskSyncJob {

    private final AigcTaskRepository taskRepo;
    private final AigcTaskService aigcTaskService;
    private final ConfigCacheManager configCacheManager;
    private final PermissionExecutionService permissionExecutionService;

    @Autowired(required = false)
    private MidjourneyAsyncImageService midjourneyService;

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void sync() {
        if (midjourneyService == null) return;

        var tasks = taskRepo.findByStatusAndType("PENDING", "IMAGE");
        if (tasks.isEmpty()) return;

        for (var task : tasks) {
            try {
                String compositeTaskId = task.getTaskId();
                if (compositeTaskId == null || !compositeTaskId.contains(":")) continue;

                int sep = compositeTaskId.indexOf(':');
                String modelId = compositeTaskId.substring(0, sep);
                String mjTaskId = compositeTaskId.substring(sep + 1);

                // 确认是 Midjourney 模型
                var model = configCacheManager.getAiModelByModelId(modelId);
                if (model == null
                        || model.effectiveProviderType() != AiModelProviderType.MIDJOURNEY)
                    continue;

                var results = midjourneyService.queryTasks(modelId, List.of(mjTaskId));
                if (results.isEmpty()) continue;

                var result = results.get(0);
                switch (result.status()) {
                    case "SUCCESS" -> {
                        log.info(
                                "[MidjourneySync] 任务完成: aigcTaskId={}, url={}",
                                task.getId(),
                                result.imageUrl());
                        permissionExecutionService.runAsOwner(
                                task.getUserId(),
                                "Midjourney任务完成",
                                () ->
                                        aigcTaskService.completeTask(
                                                compositeTaskId, result.imageUrl()));
                    }
                    case "FAILURE" -> {
                        log.warn(
                                "[MidjourneySync] 任务失败: aigcTaskId={}, reason={}",
                                task.getId(),
                                result.failReason());
                        permissionExecutionService.runAsOwner(
                                task.getUserId(),
                                "Midjourney任务失败",
                                () ->
                                        aigcTaskService.failTask(
                                                compositeTaskId, result.failReason()));
                    }
                    default -> {} // IN_PROGRESS，继续等待
                }
            } catch (Exception e) {
                log.warn("[MidjourneySync] 同步失败: taskId={}, err={}", task.getId(), e.getMessage());
            }
        }
    }
}
