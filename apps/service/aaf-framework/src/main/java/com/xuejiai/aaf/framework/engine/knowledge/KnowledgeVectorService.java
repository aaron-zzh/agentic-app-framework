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
     * 相似度查询
     *
     * @param query 查询文本
     * @param topK 返回条数
     * @return 相似文档列表
     */
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
    }

    /** 带过滤条件的相似度查询 */
    public List<Document> search(String query, int topK, String filterExpression) {
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
