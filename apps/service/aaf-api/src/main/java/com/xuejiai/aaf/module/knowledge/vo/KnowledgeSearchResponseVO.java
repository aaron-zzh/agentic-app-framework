package com.xuejiai.aaf.module.knowledge.vo;

import java.util.List;
import java.util.Map;

/** 知识库搜索响应。 */
public record KnowledgeSearchResponseVO(List<SearchResultItemVO> results) {

    /** 单条知识检索结果。 */
    public record SearchResultItemVO(
            String content, double score, String source, Map<String, Object> metadata) {}
}
