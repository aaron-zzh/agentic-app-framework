package com.xuejiai.aaf.framework.engine.meta.runtime;

import java.util.List;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务完成通知器——通过 {@link MessageService} 统一消息总线发送任务通知。
 *
 * <p>当前走 {@link MessageChannel#INTERNAL} 渠道（存库 + WS 实时推送）， 未来可扩展为多渠道（钉钉/邮件等），业务代码不变。
 *
 * <p>触发条件由 {@link TaskRuntime} 判断： {@link TaskContext} 中存在 {@code notifyUserId} 且 {@code
 * important=true}。
 *
 * <p>可选注入——{@link MessageService} 不存在时（如单测）静默跳过。
 *
 * @author Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskNotifier {

    private final MessageService messageService;

    public void notifyComplete(Long userId, String taskType, TaskResult result) {
        try {
            var title = result.success() ? "任务执行完成" : "任务执行失败";
            var body =
                    result.success()
                            ? "任务 [" + taskType + "] 已成功执行"
                            : "任务 [" + taskType + "] 执行失败：" + result.error();
            messageService.send(
                    MessageRequest.direct(
                            MessageChannel.INTERNAL, title, body, List.of(userId.toString())));
        } catch (Exception e) {
            log.warn("任务完成通知发送失败，userId={} taskType={}", userId, taskType, e);
        }
    }

    public void notifyTimeout(Long userId, String taskType) {
        try {
            messageService.send(
                    MessageRequest.direct(
                            MessageChannel.INTERNAL,
                            "任务执行超时",
                            "任务 [" + taskType + "] 执行超时，请稍后重试",
                            List.of(userId.toString())));
        } catch (Exception e) {
            log.warn("任务超时通知发送失败，userId={} taskType={}", userId, taskType, e);
        }
    }
}
