package com.xuejiai.aaf.framework.engine.knowledge.search;

import com.xuejiai.aaf.framework.engine.knowledge.KnowledgeVectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强相似度搜索服务，在 KnowledgeVectorService 基础上提供阈值过滤、去重等能力
 */
@Service
@RequiredArgsConstructor
public class SimilaritySearchService {

    private final KnowledgeVectorService knowledgeVectorService;

    /**
     * 执行相似度搜索
     */
    public List<SearchResult> search(SearchRequest request) {
        var filterExpression = buildFilterExpression(request);
        var documents = knowledgeVectorService.search(request.query(), request.topK(), filterExpression);

        return documents.stream()
                .map(this::toSearchResult)
                .filter(r -> r.score() >= request.similarityThreshold())
                .collect(Collectors.toMap(
                        r -> r.content().hashCode(),
                        r -> r,
                        (a, b) -> a.score() >= b.score() ? a : b,
                        LinkedHashMap::new
                ))
                .values().stream().toList();
    }

    /**
     * 根据请求参数构建 Spring AI filterExpression
     */
    private String buildFilterExpression(SearchRequest request) {
        var conditions = new ArrayList<String>();

        if (request.knowledgeBaseId() != null) {
            conditions.add("knowledge_base_id == %d".formatted(request.knowledgeBaseId()));
        }
        if (request.documentId() != null) {
            conditions.add("document_id == %d".formatted(request.documentId()));
        }
        if (request.tags() != null && !request.tags().isEmpty()) {
            for (var tag : request.tags()) {
                conditions.add("tags in [\"%s\"]".formatted(tag));
            }
        }

        return conditions.isEmpty() ? null : String.join(" && ", conditions);
    }

    private SearchResult toSearchResult(Document doc) {
        var metadata = doc.getMetadata();
        var score = metadata.containsKey("distance")
                ? 1.0 - ((Number) metadata.get("distance")).doubleValue()
                : metadata.containsKey("score")
                        ? ((Number) metadata.get("score")).doubleValue()
                        : 1.0;

        return new SearchResult(
                doc.getText(),
                score,
                metadata,
                doc.getId(),
                Objects.toString(metadata.get("document_id"), null)
        );
    }
}
