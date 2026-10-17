package com.xuejiai.aaf.module.elder.rehab.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.rehab.ErrorCodeConstants.REHAB_GUIDE_QUERY_INVALID;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.elder.RehabGuideStatusEnum;
import com.xuejiai.aaf.module.elder.rehab.api.RehabGuideApi;
import com.xuejiai.aaf.module.elder.rehab.domain.RehabGuide;
import com.xuejiai.aaf.module.elder.rehab.repository.RehabGuideRepository;
import com.xuejiai.aaf.module.knowledge.api.KnowledgeSearchApi;

import lombok.RequiredArgsConstructor;

/** 基于已发布指导关联文档的康复检索与回答。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultRehabGuideApi implements RehabGuideApi {

    private static final int MAX_QUERY_LENGTH = 500;
    private static final int MAX_TOP_K = 20;
    private static final double DEFAULT_THRESHOLD = 0.7;
    private static final String DISCLAIMER = "仅供康复知识参考，不替代医生或治疗师的个体化指导。";
    private static final String NO_EVIDENCE_ANSWER = "资料中没有相关内容，无法依据现有康复指导回答。";

    private final RehabGuideRepository repository;
    private final KnowledgeSearchApi knowledgeSearchApi;

    @Override
    public SearchResult search(String query, int topK) {
        var normalizedQuery = requireQuery(query);
        requireTopK(topK);
        var scopes = publishedScopes();
        if (scopes.isEmpty()) {
            return new SearchResult(false, List.of(), DISCLAIMER);
        }
        var knowledgeBaseIds =
                scopes.values().stream()
                        .map(scope -> scope.document().knowledgeBaseStableId())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var documentIds = Set.copyOf(scopes.keySet());
        var result =
                knowledgeSearchApi.search(
                        new KnowledgeSearchApi.SearchQuery(
                                normalizedQuery,
                                knowledgeBaseIds,
                                documentIds,
                                topK,
                                DEFAULT_THRESHOLD));
        var hits =
                result.hits().stream()
                        .filter(hit -> scopes.containsKey(hit.source().documentId()))
                        .map(hit -> toSearchHit(hit, scopes.get(hit.source().documentId())))
                        .toList();
        return new SearchResult(!hits.isEmpty(), hits, DISCLAIMER);
    }

    @Override
    public AnswerResult answer(String question, int topK) {
        var result = search(question, topK);
        if (!result.matched()) {
            return new AnswerResult(false, NO_EVIDENCE_ANSWER, List.of(), DISCLAIMER);
        }
        var answer = new StringBuilder("根据已发布康复指导资料：");
        for (var index = 0; index < result.hits().size(); index++) {
            answer.append(System.lineSeparator())
                    .append('[')
                    .append(index + 1)
                    .append("] ")
                    .append(result.hits().get(index).content());
        }
        var citations = result.hits().stream().map(SearchHit::citation).toList();
        return new AnswerResult(true, answer.toString(), citations, DISCLAIMER);
    }

    private LinkedHashMap<java.util.UUID, PublishedScope> publishedScopes() {
        var scopes = new LinkedHashMap<java.util.UUID, PublishedScope>();
        repository
                .findAllByStatusOrderByIdDesc(RehabGuideStatusEnum.PUBLISHED.getCode())
                .forEach(
                        guide ->
                                knowledgeSearchApi
                                        .findDocument(guide.getKnowledgeDocumentId())
                                        .ifPresent(
                                                document ->
                                                        scopes.putIfAbsent(
                                                                document.stableId(),
                                                                new PublishedScope(
                                                                        guide, document))));
        return scopes;
    }

    private SearchHit toSearchHit(KnowledgeSearchApi.SearchHit hit, PublishedScope scope) {
        var citation =
                new Citation(
                        scope.guide().getId(),
                        scope.guide().getTitle(),
                        scope.document().id(),
                        scope.document().stableId(),
                        hit.source().sourceKey(),
                        hit.source().sourceUri(),
                        hit.content(),
                        hit.score());
        return new SearchHit(hit.content(), hit.score(), citation);
    }

    private String requireQuery(String query) {
        if (query == null || query.isBlank()) {
            throw exception(REHAB_GUIDE_QUERY_INVALID);
        }
        var normalized = query.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw exception(REHAB_GUIDE_QUERY_INVALID);
        }
        return normalized;
    }

    private void requireTopK(int topK) {
        if (topK <= 0 || topK > MAX_TOP_K) {
            throw exception(REHAB_GUIDE_QUERY_INVALID);
        }
    }

    private record PublishedScope(
            RehabGuide guide, KnowledgeSearchApi.DocumentReference document) {}
}
