package com.xuejiai.aaf.framework.intelligent.agentscope.knowledge;

import java.util.List;

import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchConfig;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.RagSearchResult;

import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.message.TextBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * AAF 知识库的 AgentScope Knowledge 适配。
 * retrieve 委托 HybridSearchService（三路融合 + RRF）。
 */
@Slf4j
@RequiredArgsConstructor
public class AafKnowledge implements Knowledge {

    private final HybridSearchService hybridSearchService;
    private final Long knowledgeBaseId;

    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        var searchConfig = new HybridSearchConfig(0.5, 0.3, 0.2, config.getLimit());
        return Mono.fromCallable(() ->
                        hybridSearchService.search(query, knowledgeBaseId, searchConfig))
                .subscribeOn(Schedulers.boundedElastic())
                .map(results -> results.stream().map(this::toDocument).toList())
                .onErrorResume(e -> {
                    log.warn("知识库检索失败 kbId={}: {}", knowledgeBaseId, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        // 文档入库由 AAF 文档引擎独立处理，此处为占位
        log.debug("addDocuments 调用，文档数={}，由 AAF 文档引擎处理", documents.size());
        return Mono.empty();
    }

    private Document toDocument(RagSearchResult result) {
        var metadata = DocumentMetadata.builder()
                .content(TextBlock.builder().text(result.content()).build())
                .build();
        var doc = new Document(metadata);
        doc.setScore(result.score());
        return doc;
    }
}
