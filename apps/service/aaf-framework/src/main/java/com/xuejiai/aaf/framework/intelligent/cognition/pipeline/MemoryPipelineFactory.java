package com.xuejiai.aaf.framework.intelligent.cognition.pipeline;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.cognition.memory.ShortTermMemoryService;
import com.xuejiai.aaf.framework.intelligent.cognition.retrieval.UnifiedRetrievalService;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryContext;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryPipeline;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryStrategy;
import com.xuejiai.aaf.framework.intelligent.core.memory.PipelineInput;

import lombok.RequiredArgsConstructor;

/** 记忆管道工厂：按 MemoryStrategy 选择对应 Pipeline 实现。 v0.6 编排落地后可扩展为可视化节点配置。 */
@Component
@RequiredArgsConstructor
public class MemoryPipelineFactory {

    private final DefaultMemoryPipeline defaultPipeline;
    private final ShortTermMemoryService shortTermMemory;
    private final UnifiedRetrievalService unifiedRetrieval;

    /** 根据策略创建对应的 MemoryPipeline 实现。 */
    public MemoryPipeline create(MemoryStrategy strategy) {
        if (strategy == null) return defaultPipeline;
        return switch (strategy) {
            case HYBRID, FULL -> defaultPipeline;
            case MEMORY_ONLY -> new MemoryOnlyPipeline(shortTermMemory, unifiedRetrieval);
            case KNOWLEDGE_ONLY -> new KnowledgeOnlyPipeline(unifiedRetrieval);
            case PROCEDURAL_FIRST -> new ProceduralFirstPipeline(shortTermMemory, unifiedRetrieval);
        };
    }

    /** 仅查个人记忆（个人助理场景） */
    private record MemoryOnlyPipeline(
            ShortTermMemoryService shortTermMemory, UnifiedRetrievalService unifiedRetrieval)
            implements MemoryPipeline {
        @Override
        public MemoryContext execute(PipelineInput input) {
            var recent = shortTermMemory.getContext(input.conversationId(), 10);
            var shortTermBlock = recent.isEmpty() ? null : recent.toString();
            var result =
                    unifiedRetrieval.retrieve(
                            new UnifiedRetrievalService.RetrievalRequest(
                                    input.query(), input.userId(), null, 8));
            var longTermBlock =
                    result.memoryResults().isEmpty() ? null : result.memoryResults().toString();
            return new MemoryContext(shortTermBlock, longTermBlock, null, null, 0);
        }
    }

    /** 仅查知识库（客服/问答场景） */
    private record KnowledgeOnlyPipeline(UnifiedRetrievalService unifiedRetrieval)
            implements MemoryPipeline {
        @Override
        public MemoryContext execute(PipelineInput input) {
            var result =
                    unifiedRetrieval.retrieve(
                            new UnifiedRetrievalService.RetrievalRequest(
                                    input.query(), null, input.knowledgeBaseId(), 10));
            var knowledgeBlock =
                    result.knowledgeResults().isEmpty()
                            ? null
                            : result.knowledgeResults().toString();
            return new MemoryContext(null, null, null, knowledgeBlock, 0);
        }
    }

    /** 程序化记忆优先（代码助理/经验场景） */
    private record ProceduralFirstPipeline(
            ShortTermMemoryService shortTermMemory, UnifiedRetrievalService unifiedRetrieval)
            implements MemoryPipeline {
        @Override
        public MemoryContext execute(PipelineInput input) {
            var recent = shortTermMemory.getContext(input.conversationId(), 5);
            var shortTermBlock = recent.isEmpty() ? null : recent.toString();
            // 程序化记忆通过 UnifiedRetrievalService 的 Bundle Search 获取
            var result =
                    unifiedRetrieval.retrieve(
                            new UnifiedRetrievalService.RetrievalRequest(
                                    input.query(), input.userId(), input.knowledgeBaseId(), 10));
            var proceduralBlock = result.bundles().isEmpty() ? null : result.bundles().toString();
            var longTermBlock =
                    result.memoryResults().isEmpty() ? null : result.memoryResults().toString();
            return new MemoryContext(shortTermBlock, longTermBlock, proceduralBlock, null, 0);
        }
    }
}
