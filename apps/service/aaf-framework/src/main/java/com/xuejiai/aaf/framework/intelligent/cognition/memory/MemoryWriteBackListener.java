package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.trace.ExecutionCompletedEvent;
import com.xuejiai.aaf.framework.intelligent.agent.trace.ExecutionStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 记忆写回监听器——Agent 执行完成后异步将对话内容写入长期记忆。
 *
 * <p>流程：ExecutionCompletedEvent → LLM 抽取记忆原子 → 去重 → 持久化到 AtomMemoryEngine。 失败仅记录 warn，不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryWriteBackListener {

    private final MemoryExtractionService extractionService;

    @Async
    @EventListener
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        // 只处理有用户上下文且执行成功的对话
        if (event.userId() == null || event.input() == null || event.output() == null) {
            return;
        }
        if (event.status() != ExecutionStatus.SUCCESS) {
            return;
        }
        try {
            var conversationText = "用户: %s\n助手: %s".formatted(event.input(), event.output());
            var stored =
                    extractionService.extractAndStore(
                            event.userId(), conversationText, Instant.now());
            if (!stored.isEmpty()) {
                log.debug("记忆写回完成 [{}] 写入 {} 条原子", event.executionId(), stored.size());
            }
        } catch (Exception e) {
            log.warn("记忆写回失败 [{}]: {}", event.executionId(), e.getMessage());
        }
    }
}
