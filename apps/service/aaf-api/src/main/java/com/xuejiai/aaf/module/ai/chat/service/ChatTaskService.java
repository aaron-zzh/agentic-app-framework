package com.xuejiai.aaf.module.ai.chat.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.chat.domain.ChatTask;
import com.xuejiai.aaf.module.ai.chat.repository.ChatTaskRepository;

import lombok.RequiredArgsConstructor;

/**
 * AI 对话任务服务——管理与会话关联的任务队列。
 *
 * <p>用户可在对话中创建任务列表，助理按优先级逐个处理。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class ChatTaskService {

    private final ChatTaskRepository taskRepository;

    /** 创建任务 */
    @Transactional
    public ChatTask create(Long sessionId, Long creatorId, String title, String description, Integer priority) {
        var task = new ChatTask();
        task.setSessionId(sessionId);
        task.setCreatorId(creatorId);
        task.setTitle(title);
        task.setDescription(description);
        if (priority != null) task.setPriority(priority);
        // 排序序号：当前会话最大序号 + 1
        var tasks = taskRepository.findBySessionIdAndDeletedFalseOrderByPriorityAscSortOrderAsc(sessionId);
        task.setSortOrder(tasks.size());
        taskRepository.save(task);
        return task;
    }

    /** 获取会话的任务列表 */
    @Transactional(readOnly = true)
    public List<ChatTask> listBySession(Long sessionId) {
        return taskRepository.findBySessionIdAndDeletedFalseOrderByPriorityAscSortOrderAsc(sessionId);
    }

    /** 获取下一个待处理任务 */
    @Transactional(readOnly = true)
    public Optional<ChatTask> nextPending(Long sessionId) {
        return taskRepository.findFirstBySessionIdAndStatusAndDeletedFalseOrderByPriorityAscSortOrderAsc(
                sessionId, "pending");
    }

    /** 开始处理任务 */
    @Transactional
    public ChatTask start(Long taskId) {
        var task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus("running");
        return taskRepository.save(task);
    }

    /** 完成任务 */
    @Transactional
    public ChatTask complete(Long taskId, String result) {
        var task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus("done");
        task.setResult(result);
        return taskRepository.save(task);
    }

    /** 任务失败 */
    @Transactional
    public ChatTask fail(Long taskId, String reason) {
        var task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus("failed");
        task.setResult(reason);
        return taskRepository.save(task);
    }

    /** 取消任务 */
    @Transactional
    public ChatTask cancel(Long taskId) {
        var task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus("cancelled");
        return taskRepository.save(task);
    }

    /** 统计待处理任务数 */
    @Transactional(readOnly = true)
    public long countPending(Long sessionId) {
        return taskRepository.countBySessionIdAndStatusAndDeletedFalse(sessionId, "pending");
    }
}
