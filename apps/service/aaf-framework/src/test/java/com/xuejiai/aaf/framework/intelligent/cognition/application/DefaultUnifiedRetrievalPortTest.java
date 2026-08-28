package com.xuejiai.aaf.framework.intelligent.cognition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Hit;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Response;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceRef;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Visibility;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.intelligent.ai.rerank.RerankService;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryRerankerService;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRetrievalPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRetrievalPort.MemoryRetrievalResult;
import com.xuejiai.aaf.framework.intelligent.cognition.port.UnifiedRetrievalPort.UnifiedRetrievalRequest;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;

@ExtendWith(MockitoExtension.class)
class DefaultUnifiedRetrievalPortTest {

    @Mock private MemoryRetrievalPort memoryRetrieval;
    @Mock private HybridSearchService knowledgeSearch;
    @Mock private EmbeddingService embeddingService;
    @Mock private ObjectProvider<RerankService> noRerankModel;

    /** 无 RerankService 时 rerankContents 内部降级为原融合序（下标恒等映射），不影响融合结果验证。 */
    private MemoryRerankerService reranker;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        reranker = new MemoryRerankerService(noRerankModel);
    }

    @Test
    void fusesMemoryAndKnowledgeWithRrf() {
        var atom = atom("原子记忆内容");
        when(embeddingService.embed(any(), any())).thenReturn(new float[] {0.1f});
        when(memoryRetrieval.retrieve(any()))
                .thenReturn(new MemoryRetrievalResult(List.of(atom), List.of(), List.of()));
        when(knowledgeSearch.search(any(AuthorizedQuery.class)))
                .thenReturn(new Response(List.of(hit("kb-1")), Set.of(), Set.of()));

        var port =
                new DefaultUnifiedRetrievalPort(
                        memoryRetrieval, knowledgeSearch, embeddingService, reranker);
        var subject = new AuthorizationSubject(1L, 1L, 1L, null);
        var request =
                new UnifiedRetrievalRequest(
                        subject, 1L, "怎么办", Set.of(UUID.randomUUID()), false, 10);

        var result = port.retrieve(request);

        assertThat(result.fused()).isNotEmpty();
        assertThat(result.fused())
                .extracting(candidate -> candidate.channel())
                .contains("atomic", "knowledge");
    }

    @Test
    void knowledgeSearchUsesResolvedSubjectNotUnresolved() {
        when(knowledgeSearch.search(any(AuthorizedQuery.class)))
                .thenReturn(new Response(List.of(hit("kb-1")), Set.of(), Set.of()));

        var port =
                new DefaultUnifiedRetrievalPort(
                        memoryRetrieval, knowledgeSearch, embeddingService, reranker);
        var subject = new AuthorizationSubject(1L, 1L, 1L, null);
        var request =
                new UnifiedRetrievalRequest(
                        subject, null, "合同条款", Set.of(UUID.randomUUID()), false, 10);

        port.retrieve(request);

        var captor = ArgumentCaptor.forClass(AuthorizedQuery.class);
        verify(knowledgeSearch).search(captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo(subject);
        assertThat(captor.getValue().subject().subjectId()).isNotNull();
    }

    @Test
    void skipsMemoryChannelWhenUserIdMissing() {
        when(knowledgeSearch.search(any(AuthorizedQuery.class)))
                .thenReturn(new Response(List.of(), Set.of(), Set.of()));

        var port =
                new DefaultUnifiedRetrievalPort(
                        memoryRetrieval, knowledgeSearch, embeddingService, reranker);
        var subject = new AuthorizationSubject(1L, 1L, 1L, null);
        var request =
                new UnifiedRetrievalRequest(
                        subject, null, "怎么办", Set.of(UUID.randomUUID()), false, 10);

        port.retrieve(request);

        verify(memoryRetrieval, org.mockito.Mockito.never()).retrieve(any());
    }

    @Test
    void returnsEmptyWhenNoMemoryAndNoKnowledgeRequested() {
        var port =
                new DefaultUnifiedRetrievalPort(
                        memoryRetrieval, knowledgeSearch, embeddingService, reranker);
        var request =
                new UnifiedRetrievalRequest(
                        AuthorizationSubject.unresolved(), null, "怎么办", Set.of(), false, 10);

        var result = port.retrieve(request);

        assertThat(result.fused()).isEmpty();
    }

    @Test
    void respectsMaxItemsAfterFusion() {
        when(embeddingService.embed(any(), any())).thenReturn(new float[] {0.1f});
        var atoms = List.of(atom("a1"), atom("a2"), atom("a3"));
        when(memoryRetrieval.retrieve(any()))
                .thenReturn(new MemoryRetrievalResult(atoms, List.of(), List.of()));

        var port =
                new DefaultUnifiedRetrievalPort(
                        memoryRetrieval, knowledgeSearch, embeddingService, reranker);
        var request =
                new UnifiedRetrievalRequest(
                        AuthorizationSubject.unresolved(), 1L, "怎么办", Set.of(), false, 2);

        var result = port.retrieve(request);

        assertThat(result.fused()).hasSizeLessThanOrEqualTo(2);
    }

    private static MemoryAtom atom(String content) {
        var atom = new MemoryAtom();
        atom.setId(UUID.randomUUID());
        atom.setUserId(1L);
        atom.setScope("long_term");
        atom.setContent(content);
        atom.setEventTime(java.time.Instant.now());
        atom.setValidFrom(java.time.Instant.now());
        atom.setWeight(0.5);
        atom.setAccessCount(0);
        atom.setCreatedAt(java.time.Instant.now());
        return atom;
    }

    private static Hit hit(String candidateKeySuffix) {
        return new Hit(
                "KNOWLEDGE:" + candidateKeySuffix,
                "知识片段正文",
                0.8,
                Set.of(),
                new SourceRef(
                        UUID.randomUUID(),
                        "测试知识库",
                        Visibility.ORG,
                        UUID.randomUUID(),
                        "document",
                        candidateKeySuffix,
                        null,
                        null,
                        null,
                        Set.of(),
                        Set.of()));
    }
}
