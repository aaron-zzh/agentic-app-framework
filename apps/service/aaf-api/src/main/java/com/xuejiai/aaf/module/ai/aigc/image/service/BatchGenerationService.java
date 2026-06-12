package com.xuejiai.aaf.module.ai.aigc.image.service;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.module.ai.aigc.image.domain.BatchGenerationTask;
import com.xuejiai.aaf.module.ai.aigc.image.repository.BatchGenerationTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.image.vo.BatchGenerationSubmitDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.BatchGenerationTaskVO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.BatchTaskStatus;

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
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ImageServiceFactory imageServiceFactory;

    /**
     * 提交批量生成任务。
     *
     * @param userId 用户 ID
     * @param dto 提交请求
     * @return 任务信息
     */
    @Transactional
    public BatchGenerationTaskVO submit(Long userId, BatchGenerationSubmitDTO dto) {
        var task = new BatchGenerationTask();
        task.setUserId(userId);
        task.setStatus(BatchTaskStatus.PENDING);
        task.setTotalCount(dto.prompts().size());
        task.setCompletedCount(0);
        task.setFailedCount(0);
        try {
            task.setParams(objectMapper.writeValueAsString(dto));
        } catch (JsonProcessingException e) {
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "参数序列化失败");
        }
        task = taskRepository.save(task);

        // 将任务 ID 推入 Redis 队列
        redisTemplate.opsForList().rightPush(QUEUE_KEY, task.getId().toString());
        log.info("批量生成任务已提交: taskId={}, count={}", task.getId(), dto.prompts().size());

        // 异步触发执行
        processQueue();

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
    public List<BatchGenerationTaskVO> listByUser(Long userId) {
        return taskRepository.findByUserId(userId).stream().map(this::toVO).toList();
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
        task.setStatus(BatchTaskStatus.CANCELLED);
        taskRepository.save(task);
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
        if (task == null || task.getStatus() == BatchTaskStatus.CANCELLED) return;

        task.setStatus(BatchTaskStatus.RUNNING);
        taskRepository.save(task);

        BatchGenerationSubmitDTO dto;
        try {
            dto = objectMapper.readValue(task.getParams(), BatchGenerationSubmitDTO.class);
        } catch (JsonProcessingException e) {
            task.setStatus(BatchTaskStatus.FAILED);
            taskRepository.save(task);
            return;
        }

        for (var prompt : dto.prompts()) {
            if (task.getStatus() == BatchTaskStatus.CANCELLED) break;

            var success = executeWithRetry(prompt, dto.model(), dto.width(), dto.height());
            if (success) {
                task.setCompletedCount(task.getCompletedCount() + 1);
            } else {
                task.setFailedCount(task.getFailedCount() + 1);
            }
            taskRepository.save(task);
        }

        // 更新最终状态
        if (task.getStatus() != BatchTaskStatus.CANCELLED) {
            task.setStatus(
                    task.getFailedCount() > 0 && task.getCompletedCount() == 0
                            ? BatchTaskStatus.FAILED
                            : BatchTaskStatus.COMPLETED);
            taskRepository.save(task);
        }
        log.info(
                "批量生成任务完成: taskId={}, completed={}, failed={}",
                taskId,
                task.getCompletedCount(),
                task.getFailedCount());
    }

    /** 带重试的单次生成执行。 */
    private boolean executeWithRetry(String prompt, String model, Integer width, Integer height) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                var service = imageServiceFactory.getSyncService(model);
                var request =
                        new ImageRequest(
                                prompt,
                                model,
                                width != null ? width : 1024,
                                height != null ? height : 1024,
                                "url");
                service.generate(request);
                log.debug("批量生成成功: prompt={}, attempt={}", prompt, attempt);
                return true;
            } catch (Exception e) {
                log.warn("生成失败 (attempt {}/{}): {}", attempt, MAX_RETRY, e.getMessage());
                if (attempt == MAX_RETRY) return false;
            }
        }
        return false;
    }

    private BatchGenerationTask findById(Long id) {
        return taskRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "批量任务不存在"));
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
