package com.xuejiai.aaf.framework.intelligent.cognition.pipeline;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.cognition.memory.ShortTermMemoryService;
import com.xuejiai.aaf.framework.intelligent.cognition.retrieval.UnifiedRetrievalService;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryContext;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryPipeline;
import com.xuejiai.aaf.framework.intelligent.core.memory.PipelineInput;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认记忆管道：Memory + Knowledge 混合检索 + RRF 融合 + LLM 重排。
 * 对应 MemoryStrategy.HYBRID（默认策略）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMemoryPipeline implements MemoryPipeline {

    private final ShortTermMemoryService shortTermMemory;
    private final UnifiedRetrievalService unifiedRetrieval;

    @Override
    public MemoryContext execute(PipelineInput input) {
        // Stage 1: 短期记忆（近期对话）
        var recentMessages = shortTermMemory.getContext(input.conversationId(), 10);
        var shortTermBlock = formatMessages(recentMessages);

        // Stage 2-5: 统一融合检索（长期记忆 + 知识库 + Bundle Search）
        var retrievalResult = unifiedRetrieval.retrieve(
            new UnifiedRetrievalService.RetrievalRequest(
                input.query(), input.userId(), input.knowledgeBaseId(), 10
            )
        );

        var longTermBlock = formatAtoms(retrievalResult.memoryResults());
        var knowledgeBlock = formatKnowledge(retrievalResult.knowledgeResults());

        return new MemoryContext(shortTermBlock, longTermBlock, null, knowledgeBlock,
            estimateTokens(shortTermBlock, longTermBlock, knowledgeBlock));
    }

    private String formatMessages(java.util.List<?> messages) {
        if (messages == null || messages.isEmpty()) return null;
        var sb = new StringBuilder();
        messages.forEach(m -> sb.append(m.toString()).append("\n"));
        return sb.toString().trim();
    }

    private String formatAtoms(java.util.List<?> atoms) {
        if (atoms == null || atoms.isEmpty()) return null;
        var sb = new StringBuilder();
        atoms.forEach(a -> sb.append("- ").append(a.toString()).append("\n"));
        return sb.toString().trim();
    }

    private String formatKnowledge(java.util.List<?> results) {
        if (results == null || results.isEmpty()) return null;
        var sb = new StringBuilder();
        results.forEach(r -> sb.append(r.toString()).append("\n\n"));
        return sb.toString().trim();
    }

    private int estimateTokens(String... blocks) {
        int total = 0;
        for (var block : blocks) {
            if (block != null) total += block.length() / 4; // 粗估：4字符≈1token
        }
        return total;
    }
}
