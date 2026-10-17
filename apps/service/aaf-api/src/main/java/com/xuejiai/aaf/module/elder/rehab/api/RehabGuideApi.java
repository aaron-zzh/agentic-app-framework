package com.xuejiai.aaf.module.elder.rehab.api;

import java.util.List;
import java.util.UUID;

/** 康复指导对外只读接口。 */
public interface RehabGuideApi {

    /** 在已发布指导关联的知识文档范围内检索。 */
    SearchResult search(String query, int topK);

    /** 仅依据已发布指导关联文档中的命中片段回答。 */
    AnswerResult answer(String question, int topK);

    /** 康复指导检索结果。 */
    record SearchResult(boolean matched, List<SearchHit> hits, String disclaimer) {}

    /** 一条带引用的检索命中。 */
    record SearchHit(String content, double score, Citation citation) {}

    /** 康复指导回答结果；answered=false 表示已明确拒绝范围外回答。 */
    record AnswerResult(
            boolean answered, String answer, List<Citation> citations, String disclaimer) {}

    /** 回答引用。 */
    record Citation(
            Long guideId,
            String guideTitle,
            Long knowledgeDocumentId,
            UUID knowledgeDocumentStableId,
            String sourceKey,
            String sourceUri,
            String excerpt,
            double score) {}
}
