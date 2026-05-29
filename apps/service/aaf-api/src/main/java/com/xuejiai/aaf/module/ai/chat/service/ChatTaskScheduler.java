package com.xuejiai.aaf.module.ai.chat.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.AssistantService;
import com.xuejiai.aaf.module.ai.chat.domain.ChatTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 任务调度器——定时扫描到期任务并触发执行，任务完成后自动取下一个。
 *
 * <p>触发源：
 * <ul>
 *   <li>定时调度：每 30 秒扫描 scheduledAt 已到期的 pending 任务
 *   <li>被动触发：任务完成后调用 {@link #executeNext} 自动处理下一个
 * </ul>
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTaskScheduler {

    private final ChatTaskService taskService;
    private final AssistantService assistantService;
    private final ChatService chatService;

    /** 定时扫描到期任务（每 30 秒） */
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void pollDueTasks() {
        var dueTasks = taskService.findDueTasks();
        for (var task : dueTasks) {
            Thread.startVirtualThread(() -> executeTask(task));
        }
    }

    /** 执行单个任务，完成后自动取下一个 */
    public void executeTask(ChatTask task) {
        log.info("[TaskScheduler] 开始执行任务: id={}, title={}", task.getId(), task.getTitle());
        taskService.start(task.getId());

        try {
            // 构建任务输入：标题 + 描述
            var input = task.getDescription() != null
                    ? task.getTitle() + "\n" + task.getDescription()
                    : task.getTitle();

            // 委托 AssistantService 执行
            var response = assistantService.handle(
                    task.getSessionId().toString(),
                    task.getCreatorId(),
                    "default",
                    input);

            // 完成任务
            taskService.complete(task.getId(), response.content());

            // 持久化助理回复到对话消息
            chatService.saveMessage(
                    task.getCreatorId(), "AI", task.getSessionId(),
                    "assistant", "[任务完成: %s]\n%s".formatted(task.getTitle(), response.content()));

            log.info("[TaskScheduler] 任务完成: id={}", task.getId());

            // 自动执行下一个
            executeNext(task.getSessionId());
        } catch (Exception e) {
            log.error("[TaskScheduler] 任务执行失败: id={}", task.getId(), e);
            taskService.fail(task.getId(), e.getMessage());
        }
    }

    /** 自动取下一个待处理任务并执行 */
    public void executeNext(Long sessionId) {
        taskService.nextPending(sessionId).ifPresent(next ->
                Thread.startVirtualThread(() -> executeTask(next)));
    }
}
