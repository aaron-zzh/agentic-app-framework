package com.xuejiai.aaf.module.ai.aigc.image.service;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.image.domain.BatchGenerationTask;
import com.xuejiai.aaf.module.ai.aigc.image.repository.BatchGenerationTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.image.vo.BatchGenerationSubmitDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.BatchGenerationTaskVO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.BatchTaskStatus;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcSubmissionAccessGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 批量生成服务：提交批量任务、查询进度、取消任务。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchGenerationService {

    private static final String QUEUE_KEY = "aigc:batch:queue";
    private static final String RATE_LIMIT_KEY = "aigc:batch:rate_limit";
    private static final int MAX_RETRY = 3;
    private static final int MAX_CONCURRENT = 5;

    private final BatchGenerationTaskRepository taskRepository;
    private final AigcSubmissionAccessGuard submissionAccessGuard;
    private final StringRedisTemplate redisTemplate;
    private final AiServiceRegistry aiServiceRegistry;
    private final CapabilityRouter capabilityRouter;
    private final OperatorContext operatorContext;
    private final ObjectProvider<BatchGenerationService> selfProvider;

    /**
     * 提交批量生成任务。
     *
     * @param userId 用户 ID
     * @param dto 提交请求
     * @return 任务信息
     */
    @Transactional
    public BatchGenerationTaskVO submit(BatchGenerationSubmitDTO dto) {
        var userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        submissionAccessGuard.requireAccess(userId, AigcTaskTypeEnum.IMAGE);
        var task = new BatchGenerationTask();
        task.setUserId(userId);
        task.setStatus(BatchTaskStatus.PENDING);
        task.setTotalCount(dto.prompts().size());
        task.setCompletedCount(0);
        task.setFailedCount(0);
        try {
            task.setParams(JsonUtils.toJsonString(dto));
        } catch (Exception e) {
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "参数序列化失败");
        }
        task = taskRepository.save(task);

        // 将任务 ID 推入 Redis 队列
        redisTemplate.opsForList().rightPush(QUEUE_KEY, task.getId().toString());
        log.info("批量生成任务已提交: taskId={}, count={}", task.getId(), dto.prompts().size());

        // 异步触发执行
        selfProvider.getObject().processQueue();

        return toVO(task);
    }

    /**
     * 查询任务进度。
     *
     * @param taskId 任务 ID
     * @return 任务进度信息
     */
    @Transactional(readOnly = true)
    public BatchGenerationTaskVO getProgress(Long taskId) {
        var task = findById(taskId);
        return toVO(task);
    }

    /**
     * 查询用户所有批量任务。
     *
     * @param userId 用户 ID
     * @return 任务列表
     */
    @Transactional(readOnly = true)
    public List<BatchGenerationTaskVO> listCurrentUser() {
        return taskRepository.findByUserId(currentOwnerId()).stream().map(this::toVO).toList();
    }

    /**
     * 取消任务。
     *
     * @param taskId 任务 ID
     */
    @Transactional
    public void cancel(Long taskId) {
        var task = findById(taskId);
        if (task.getStatus() == BatchTaskStatus.COMPLETED
                || task.getStatus() == BatchTaskStatus.CANCELLED) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "任务已完成或已取消，无法取消");
        }
        var updated =
                taskRepository.transitionStatusIfActive(
                        taskId, BatchTaskStatus.PENDING, BatchTaskStatus.CANCELLED);
        if (updated == 0) {
            taskRepository.transitionStatusIfActive(
                    taskId, BatchTaskStatus.RUNNING, BatchTaskStatus.CANCELLED);
        }
        log.info("批量生成任务已取消: taskId={}", taskId);
    }

    /** 异步处理队列中的任务（令牌桶限流）。 */
    @Async
    public void processQueue() {
        // 令牌桶限流：检查并发数
        var currentCount = redisTemplate.opsForValue().increment(RATE_LIMIT_KEY);
        if (currentCount != null && currentCount > MAX_CONCURRENT) {
            redisTemplate.opsForValue().decrement(RATE_LIMIT_KEY);
            log.debug("批量生成并发已满，等待下次调度");
            return;
        }
        // 设置过期时间防止泄漏
        redisTemplate.expire(RATE_LIMIT_KEY, Duration.ofMinutes(10));

        try {
            var taskIdStr = redisTemplate.opsForList().leftPop(QUEUE_KEY);
            if (taskIdStr == null) return;

            var taskId = Long.parseLong(taskIdStr);
            executeTask(taskId);
        } finally {
            redisTemplate.opsForValue().decrement(RATE_LIMIT_KEY);
        }
    }

    /** 执行单个批量任务（含重试逻辑）。 */
    private void executeTask(Long taskId) {
        var task = taskRepository.findById(taskId).orElse(null);
        if (task == null
                || taskRepository.transitionStatusIfActive(
                                taskId, BatchTaskStatus.PENDING, BatchTaskStatus.RUNNING)
                        == 0) {
            return;
        }

        BatchGenerationSubmitDTO dto;
        try {
            dto = JsonUtils.parseObject(task.getParams(), BatchGenerationSubmitDTO.class);
        } catch (Exception e) {
            taskRepository.transitionStatusIfActive(
                    taskId, BatchTaskStatus.RUNNING, BatchTaskStatus.FAILED);
            return;
        }

        var completedCount = task.getCompletedCount();
        var failedCount = task.getFailedCount();
        for (var prompt : dto.prompts()) {
            if (isCancelled(taskId)) return;

            var success = executeWithRetry(prompt, dto.model(), dto.width(), dto.height());
            var updated =
                    success
                            ? taskRepository.incrementCompletedIfRunning(
                                    taskId, BatchTaskStatus.RUNNING)
                            : taskRepository.incrementFailedIfRunning(
                                    taskId, BatchTaskStatus.RUNNING);
            if (updated == 0) {
                return;
            }
            if (success) {
                completedCount++;
            } else {
                failedCount++;
            }
        }

        var finalStatus =
                failedCount > 0 && completedCount == 0
                        ? BatchTaskStatus.FAILED
                        : BatchTaskStatus.COMPLETED;
        if (taskRepository.transitionStatusIfActive(taskId, BatchTaskStatus.RUNNING, finalStatus)
                == 0) {
            return;
        }
        log.info(
                "批量生成任务完成: taskId={}, completed={}, failed={}",
                taskId,
                completedCount,
                failedCount);
    }

    /** 带重试的单次生成执行。 */
    private boolean executeWithRetry(String prompt, String modelId, Integer width, Integer height) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                var model =
                        capabilityRouter.resolve(
                                CapabilityRoutingContext.of(
                                        null, CapabilityRoutingContext.CAP_IMAGE_GEN, modelId));
                var request =
                        new ImageRequest(
                                prompt,
                                model.getModelId(),
                                width != null ? width : 1024,
                                height != null ? height : 1024,
                                "url");
                aiServiceRegistry.get(ImageGenerationService.class, model).generate(model, request);
                log.debug("批量生成成功: prompt={}, attempt={}", prompt, attempt);
                return true;
            } catch (Exception e) {
                log.warn("生成失败 (attempt {}/{}): {}", attempt, MAX_RETRY, e.getMessage());
                if (attempt == MAX_RETRY) return false;
            }
        }
        return false;
    }

    private boolean isCancelled(Long taskId) {
        return taskRepository
                .findById(taskId)
                .map(task -> task.getStatus() == BatchTaskStatus.CANCELLED)
                .orElse(true);
    }

    private BatchGenerationTask findById(Long id) {
        return taskRepository
                .findByIdAndUserId(id, currentOwnerId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "批量任务不存在"));
    }

    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private BatchGenerationTaskVO toVO(BatchGenerationTask task) {
        return new BatchGenerationTaskVO(
                task.getId(),
                task.getStatus(),
                task.getTotalCount(),
                task.getCompletedCount(),
                task.getFailedCount(),
                task.getCreateTime());
    }
}
