package com.xuejiai.aaf.framework.engine.knowledge;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 知识库向量服务，封装向量写入和相似度查询 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeVectorService {

    private final VectorStore vectorStore;

    /** 写入文档向量 */
    public void store(List<Document> documents) {
        vectorStore.add(documents);
        log.info("写入 {} 条向量", documents.size());
    }

    /**
     * 带过滤条件的相似度查询。
     *
     * <p>M45：已删除无过滤的 {@code search(query, topK)} 重载——它不带任何 kbId/租户条件，一旦被新增调用方
     * 使用就会跨知识库检索。检索必须显式给出过滤表达式；确实要全局检索时也必须由调用方显式传入 过滤条件（如 {@code "knowledge_base_id ==
     * 'x'"}），不提供"省略即全量"的入口。
     *
     * @param query 查询文本
     * @param topK 返回条数
     * @param filterExpression 向量库过滤表达式，不可为空
     */
    public List<Document> search(String query, int topK, String filterExpression) {
        if (filterExpression == null || filterExpression.isBlank()) {
            throw new IllegalArgumentException("向量检索必须提供过滤表达式（至少限定知识库/租户）");
        }
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(filterExpression)
                        .build());
    }

    /** 删除向量 */
    public void delete(List<String> ids) {
        vectorStore.delete(ids);
    }
}
