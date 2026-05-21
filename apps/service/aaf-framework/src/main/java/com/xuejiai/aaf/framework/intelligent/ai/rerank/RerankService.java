package com.xuejiai.aaf.framework.intelligent.ai.rerank;

import java.util.List;

/** 重排序服务接口，用于 RAG 精排。 */
public interface RerankService {

    /**
     * 对候选文档按与 query 的相关性重排序。
     *
     * @param query 用户查询
     * @param documents 候选文档列表
     * @param topN 返回前 N 条
     * @return 按相关性降序排列的文档列表
     */
    List<RankedDocument> rerank(String query, List<String> documents, int topN);

    record RankedDocument(int index, String text, double score) {}
}
