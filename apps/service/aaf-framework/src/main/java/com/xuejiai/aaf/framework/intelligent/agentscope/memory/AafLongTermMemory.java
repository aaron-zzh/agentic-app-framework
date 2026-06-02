package com.xuejiai.aaf.framework.intelligent.agentscope.memory;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryContext;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryWritePipeline;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryWritePipeline.WriteInput;
import com.xuejiai.aaf.framework.intelligent.core.memory.PipelineInput;
import com.xuejiai.aaf.framework.intelligent.core.memory.RetrievalPipeline;

import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * AAF 记忆管道的 AgentScope LongTermMemory 适配。 retrieve 委托 RetrievalPipeline，record 委托
 * MemoryWritePipeline。
 */
@Slf4j
@RequiredArgsConstructor
public class AafLongTermMemory implements LongTermMemory {

    private final MemoryWritePipeline writePipeline;
    private final RetrievalPipeline retrievalPipeline;
    private final Long userId;
    private final String sessionId;

    @Override
    public Mono<String> retrieve(Msg msg) {
        if (msg == null || msg.getTextContent() == null) {
            return Mono.just("");
        }
        var input = new PipelineInput(msg.getTextContent(), userId, sessionId);
        return Mono.fromCallable(() -> retrievalPipeline.execute(input))
                .subscribeOn(Schedulers.boundedElastic())
                .map(MemoryContext::toPromptSection)
                .onErrorResume(
                        e -> {
                            log.warn("记忆检索失败 userId={}: {}", userId, e.getMessage());
                            return Mono.just("");
                        });
    }

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        if (msgs == null || msgs.isEmpty()) {
            return Mono.empty();
        }
        // 提取最后一轮 USER + ASSISTANT 消息
        String userMsg = null;
        String assistantMsg = null;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            var m = msgs.get(i);
            if (m == null) continue;
            if (assistantMsg == null && m.getRole() == MsgRole.ASSISTANT) {
                assistantMsg = m.getTextContent();
            }
            if (userMsg == null && m.getRole() == MsgRole.USER) {
                userMsg = m.getTextContent();
            }
            if (userMsg != null && assistantMsg != null) break;
        }
        if (userMsg == null && assistantMsg == null) {
            return Mono.empty();
        }
        var input = new WriteInput(userMsg, assistantMsg, userId, sessionId, null);
        return Mono.fromRunnable(() -> writePipeline.execute(input))
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(
                        e -> {
                            log.warn("记忆写入失败 userId={}: {}", userId, e.getMessage());
                            return Mono.empty();
                        });
    }
}
