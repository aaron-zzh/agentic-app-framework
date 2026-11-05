package com.xuejiai.aaf.module.knowledge.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.knowledge.api.KnowledgeSearchApi;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchDTO;

import lombok.RequiredArgsConstructor;

/** 知识模块对业务域提供的受控检索适配器。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultKnowledgeSearchApi implements KnowledgeSearchApi {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public java.util.Optional<DocumentReference> findDocument(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return java.util.Optional.empty();
        }
        return documentRepository
                .findById(documentId)
                .flatMap(
                        document ->
                                knowledgeBaseRepository
                                        .findById(document.getKnowledgeBaseId())
                                        .map(
                                                knowledgeBase ->
                                                        new DocumentReference(
                                                                document.getId(),
                                                                document.getStableId(),
                                                                knowledgeBase.getStableId(),
                                                                document.getTitle(),
                                                                document.getStatus())));
    }

    @Override
    public SearchResult search(SearchQuery query) {
        var response =
                knowledgeBaseService.search(
                        new KnowledgeSearchDTO(
                                query.query(),
                                query.knowledgeBaseIds(),
                                true, // 公开知识库默认纳入检索
                                Map.of(),
                                Map.of("documentIds", query.documentIds()),
                                query.topK(),
                                query.threshold(),
                                "hybrid"));
        var hits =
                response.results().stream()
                        .map(
                                hit ->
                                        new SearchHit(
                                                hit.content(),
                                                hit.score(),
                                                new Source(
                                                        hit.source().knowledgeBaseId(),
                                                        hit.source().documentId(),
                                                        hit.source().sourceKey(),
                                                        hit.source().sourceUri())))
                        .toList();
        return new SearchResult(hits, response.degradedChannels());
    }

    @Override
    public List<KnowledgeBaseSummary> findKnowledgeBases(Set<UUID> stableIds) {
        if (stableIds == null || stableIds.isEmpty()) {
            return List.of();
        }
        return knowledgeBaseRepository.findByStableIdIn(stableIds).stream()
                .map(
                        knowledgeBase ->
                                new KnowledgeBaseSummary(
                                        knowledgeBase.getStableId(),
                                        knowledgeBase.getName(),
                                        knowledgeBase.getDescription()))
                .toList();
    }
}
