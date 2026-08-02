package com.xuejiai.aaf.framework.engine.knowledge.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;

@ExtendWith(MockitoExtension.class)
class EmbeddingKnowledgeReservationTest {

    @Mock private EmbeddingModel embeddingModel;
    @Mock private AiCreditGuard creditGuard;
    @Mock private KnowledgeUsagePort usagePort;
    @Mock private ModelManagementService modelManagementService;
    @Mock private AiModel model;

    @Test
    @DisplayName("Given 新知识向量单元 When 调用 embedding Then 先预留占用且落盘后结算")
    void should_reserve_before_embedding_and_persist_before_settlement() {
        var properties = new EmbeddingProperties("embedding-model", 3, 100, 1, 1);
        var service =
                new EmbeddingService(
                        embeddingModel, properties, creditGuard, usagePort, modelManagementService);
        var billing = new KnowledgeUsagePort.BillingContext("tenant", UUID.randomUUID(), 10L);
        var text = "knowledge";
        var digest = sha256(properties.model() + "|" + sha256(text));
        var invocationId =
                KnowledgeUsagePort.invocationId(billing.runId(), "embedding", "chunks", digest);
        var reservation =
                new KnowledgeUsagePort.InvocationReservation(
                        billing,
                        "embedding",
                        "chunks",
                        digest,
                        invocationId,
                        KnowledgeUsagePort.usageKey(
                                billing.runId(), "embedding", "chunks", invocationId),
                        Instant.parse("2026-01-02T03:04:05Z"),
                        UUID.randomUUID(),
                        KnowledgeUsagePort.ReservationState.RESERVED,
                        null,
                        invocationId);
        when(modelManagementService.getModel("embedding-model")).thenReturn(model);
        when(model.getInputPricePerK()).thenReturn(BigDecimal.ONE);
        when(usagePort.findProviderResult(billing, "embedding", "chunks", digest))
                .thenReturn(Optional.empty());
        when(usagePort.reserve(billing, "embedding", "chunks", digest)).thenReturn(reservation);
        when(usagePort.start(reservation)).thenReturn(true);
        when(embeddingModel.embed(text)).thenReturn(new float[] {1, 2, 3});

        var result = service.embedKnowledgeBatch(List.of(text), billing, "chunks");

        assertThat(result).hasSize(1);
        var ordered = inOrder(usagePort, embeddingModel);
        ordered.verify(usagePort).precheck(any(), anyString(), anyLong());
        ordered.verify(usagePort).reserve(billing, "embedding", "chunks", digest);
        ordered.verify(usagePort).start(reservation);
        ordered.verify(embeddingModel).embed(text);
        ordered.verify(usagePort).persistProviderResult(any(), anyString(), anyString());
        ordered.verify(usagePort).settle(any());
    }

    private String sha256(String value) {
        return com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.sha256(
                value);
    }
}
