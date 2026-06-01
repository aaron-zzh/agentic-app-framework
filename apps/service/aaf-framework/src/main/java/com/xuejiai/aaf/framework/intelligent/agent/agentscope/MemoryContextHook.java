package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import java.util.ArrayList;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.cognition.retrieval.UnifiedRetrievalService;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 记忆上下文 Hook——每轮 LLM 推理前（{@link PreReasoningEvent}）按当前 query
 * 检索长期记忆 + 知识库，作为临时 system 消息注入。
 *
 * <p>注入仅作用于本次 LLM 调用（修改 inputMessages 副本），不写回 Memory，不累积。
 * userId/knowledgeBaseId 从 {@link AgentRunContextHolder} 取（由 AafAgentResolver 在执行线程设置）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryContextHook implements Hook {

    private static final int TOP_K = 8;

    private final UnifiedRetrievalService retrievalService;

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent pre) {
            injectMemoryContext(pre);
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 800;
    }

    private void injectMemoryContext(PreReasoningEvent event) {
        var ctx = AgentRunContextHolder.current().orElse(null);
        if (ctx == null || ctx.userId() == null) {
            return; // 无用户上下文，跳过检索
        }

        var msgs = event.getInputMessages();
        if (msgs == null || msgs.isEmpty()) return;

        // 取最后一条 user 消息作为检索 query
        String query = null;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            var m = msgs.get(i);
            if (m.getRole() == MsgRole.USER && m.getTextContent() != null) {
                query = m.getTextContent();
                break;
            }
        }
        if (query == null || query.isBlank()) return;

        try {
            var result = retrievalService.retrieve(
                    new UnifiedRetrievalService.RetrievalRequest(
                            query, ctx.userId(), ctx.knowledgeBaseId(), TOP_K));
            if (result.fused().isEmpty()) return;

            var sb = new StringBuilder("以下是与当前问题相关的记忆和知识，供参考：\n");
            for (var item : result.fused()) {
                sb.append("- [").append(item.source()).append("] ").append(item.content()).append("\n");
            }

            // 临时注入：system 消息放在最前，仅本次 LLM 调用生效（不写回 Memory）
            var enriched = new ArrayList<Msg>(msgs.size() + 1);
            enriched.add(Msg.builder().role(MsgRole.SYSTEM).textContent(sb.toString()).build());
            enriched.addAll(msgs);
            event.setInputMessages(enriched);
        } catch (Exception e) {
            log.warn("记忆检索注入失败，跳过: {}", e.getMessage());
        }
    }
}
