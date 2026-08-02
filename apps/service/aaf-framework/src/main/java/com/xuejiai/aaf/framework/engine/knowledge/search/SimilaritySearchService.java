package com.xuejiai.aaf.framework.engine.knowledge.search;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.KnowledgeVectorService;

import lombok.RequiredArgsConstructor;

/** 增强相似度搜索服务，在 KnowledgeVectorService 基础上提供阈值过滤、去重等能力 */
@Service
@RequiredArgsConstructor
public class SimilaritySearchService {

    private final KnowledgeVectorService knowledgeVectorService;

    /**
     * 执行相似度搜索。
     *
     * <p>M45：必须给出 knowledgeBaseId——底层 {@code KnowledgeVectorService.search} 已删除无过滤重载，
     * 这里再前置一道校验，避免"未传 kbId → 过滤表达式为 null → 全库检索"的静默跨库读取。
     */
    public List<SearchResult> search(SearchRequest request) {
        if (request.knowledgeBaseId() == null) {
            throw new IllegalArgumentException("相似度检索必须指定 knowledgeBaseId，禁止跨知识库全量检索");
        }
        var filterExpression = buildFilterExpression(request);
        var documents =
                knowledgeVectorService.search(request.query(), request.topK(), filterExpression);

        var converted =
                documents.stream()
                        .map(this::toSearchResult)
                        .filter(result -> result.score() >= request.similarityThreshold())
                        .filter(result -> result.chunkId() != null && !result.chunkId().isBlank())
                        .toList();
        var candidateIds =
                converted.stream()
                        .map(SearchResult::chunkId)
                        .map(UUID::fromString)
                        .collect(Collectors.toUnmodifiableSet());
        var currentIds =
                knowledgeVectorService.retainCurrentChunkIds(
                        candidateIds, request.knowledgeBaseId(), request.sourceFilters());
        return converted.stream()
                .filter(result -> currentIds.contains(UUID.fromString(result.chunkId())))
                .collect(
                        Collectors.toMap(
                                SearchResult::chunkId,
                                result -> result,
                                (left, right) -> left.score() >= right.score() ? left : right,
                                LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    /** 根据请求参数构建 Spring AI filterExpression */
    private String buildFilterExpression(SearchRequest request) {
        var conditions = new ArrayList<String>();

        conditions.add("knowledge_base_id == \"%s\"".formatted(request.knowledgeBaseId()));
        var filters = request.sourceFilters();
        if (!filters.sourceTypes().isEmpty()) {
            conditions.add(orEquals("source_type", filters.sourceTypes()));
        }
        if (!filters.sourceKeys().isEmpty()) {
            conditions.add(orEquals("source_key", filters.sourceKeys()));
        }
        if (!filters.documentIds().isEmpty()) {
            conditions.add(
                    orEquals(
                            "document_id",
                            filters.documentIds().stream().map(UUID::toString).toList()));
        }

        return String.join(" && ", conditions);
    }

    private String orEquals(String field, Collection<String> values) {
        return values.stream()
                .map(value -> "%s == \"%s\"".formatted(field, escape(value)))
                .collect(Collectors.joining(" || ", "(", ")"));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private SearchResult toSearchResult(Document doc) {
        var metadata = doc.getMetadata();
        var score =
                metadata.containsKey("distance")
                        ? 1.0 - ((Number) metadata.get("distance")).doubleValue()
                        : metadata.containsKey("score")
                                ? ((Number) metadata.get("score")).doubleValue()
                                : 1.0;

        return new SearchResult(
                doc.getText(),
                score,
                metadata,
                Objects.toString(metadata.get("chunk_id"), null),
                Objects.toString(metadata.get("document_id"), null));
    }
}
