package com.xuejiai.aaf.module.ai.chat.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.chat.domain.ChatTask;
import com.xuejiai.aaf.module.ai.chat.domain.enums.ChatTaskStatus;
import com.xuejiai.aaf.module.ai.chat.repository.ChatTaskRepository;

import lombok.RequiredArgsConstructor;

/**
 * AI 对话任务服务——管理与会话关联的任务队列。
 *
 * <p>用户可在对话中创建任务列表，助理按优先级逐个处理。 支持定时任务（scheduledAt）和自动执行下一个任务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class ChatTaskService {

    private final ChatTaskRepository taskRepository;

    /** 创建任务 */
    @Transactional
    public ChatTask create(
            Long sessionId,
            Long creatorId,
            String title,
            String description,
            Integer priority,
            LocalDateTime scheduledAt) {
        var task = new ChatTask();
        task.setConversationId(sessionId);
        task.setCreatorId(creatorId);
        task.setTitle(title);
        task.setDescription(description);
        if (priority != null) task.setPriority(priority);
        task.setScheduledAt(scheduledAt);
        var tasks =
                taskRepository.findByConversationIdAndDeletedFalseOrderByPriorityAscSortOrderAsc(sessionId);
        task.setSortOrder(tasks.size());
        taskRepository.save(task);
        return task;
    }

    /** 创建任务（无定时） */
    @Transactional
    public ChatTask create(
            Long sessionId, Long creatorId, String title, String description, Integer priority) {
        return create(sessionId, creatorId, title, description, priority, null);
    }

    /** 获取会话的任务列表 */
    @Transactional(readOnly = true)
    public List<ChatTask> listBySession(Long sessionId) {
        return taskRepository.findByConversationIdAndDeletedFalseOrderByPriorityAscSortOrderAsc(sessionId);
    }

    /** 获取下一个可执行的待处理任务（已到期或无定时） */
    @Transactional(readOnly = true)
    public Optional<ChatTask> nextPending(Long sessionId) {
        return taskRepository.findNextPending(sessionId);
    }

    /** 查找所有到期的待处理任务（供调度器使用） */
    @Transactional(readOnly = true)
    public List<ChatTask> findDueTasks() {
        return taskRepository.findDueTasks(LocalDateTime.now());
    }

    /** CAS 抢占启动任务（pending → running），返回是否抢占成功 */
    @Transactional
    public boolean tryStart(Long taskId) {
        return taskRepository.casStartTask(taskId) > 0;
    }

    /** 开始处理任务（无竞争保护，内部使用） */
    @Transactional
    public ChatTask start(Long taskId) {
        var task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(ChatTaskStatus.RUNNING);
        return taskRepository.save(task);
    }

    /** 完成任务 */
    @Transactional
    public ChatTask complete(Long taskId, String result) {
        var task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(ChatTaskStatus.DONE);
        task.setResult(result);
        return taskRepository.save(task);
    }

    /** 任务失败 */
    @Transactional
    public ChatTask fail(Long taskId, String reason) {
        var task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(ChatTaskStatus.FAILED);
        task.setResult(reason);
        return taskRepository.save(task);
    }

    /** 取消任务 */
    @Transactional
    public ChatTask cancel(Long taskId) {
        var task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(ChatTaskStatus.CANCELLED);
        return taskRepository.save(task);
    }

    /** 统计待处理任务数 */
    @Transactional(readOnly = true)
    public long countPending(Long sessionId) {
        return taskRepository.countByConversationIdAndStatusAndDeletedFalse(
                sessionId, ChatTaskStatus.PENDING);
    }

    /** 回收孤儿任务（running 超时未完成的重置为 pending），返回回收数量 */
    @Transactional
    public int recoverOrphans(LocalDateTime cutoff) {
        return taskRepository.recoverOrphans(cutoff);
    }
}
