package com.xuejiai.aaf.framework.intelligent.agentscope.knowledge;

import java.util.List;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchConfig;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.RagSearchResult;
import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * AAF 知识库的 AgentScope Knowledge 适配。
 *
 * <p>动态从 {@link AgentRunContextHolder} 读取 knowledgeBaseId， 支持两种模式：
 *
 * <ul>
 *   <li>{@code RAGMode.GENERIC}：每轮 LLM 前自动检索注入（被动）
 *   <li>{@code RAGMode.AGENTIC}：暴露为 retrieve_knowledge 工具，Agent 主动查（主动）
 * </ul>
 *
 * <p>当 knowledgeBaseId 为 null 时（未关联知识库），retrieve 返回空列表，不报错。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AafKnowledge implements Knowledge {

    private final HybridSearchService hybridSearchService;

    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        var knowledgeBaseId =
                AgentRunContextHolder.current().map(ctx -> ctx.knowledgeBaseId()).orElse(null);
        if (knowledgeBaseId == null) {
            return Mono.just(List.of());
        }
        var searchConfig = new HybridSearchConfig(0.5, 0.3, 0.2, config.getLimit());
        return Mono.fromCallable(
                        () -> hybridSearchService.search(query, knowledgeBaseId, searchConfig))
                .subscribeOn(Schedulers.boundedElastic())
                .map(results -> results.stream().map(this::toDocument).toList())
                .onErrorResume(
                        e -> {
                            log.warn("知识库检索失败 kbId={}: {}", knowledgeBaseId, e.getMessage());
                            return Mono.just(List.of());
                        });
    }

    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        // 文档入库由 AAF 文档引擎独立处理，此处为占位
        return Mono.empty();
    }

    private Document toDocument(RagSearchResult result) {
        var metadata =
                DocumentMetadata.builder()
                        .content(TextBlock.builder().text(result.content()).build())
                        .build();
        var doc = new Document(metadata);
        doc.setScore(result.score());
        return doc;
    }
}
