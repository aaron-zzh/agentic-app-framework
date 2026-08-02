package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;

@ExtendWith(MockitoExtension.class)
class KnowledgeAiMeterTest {

    private static final String MODEL_ID = "frozen-model";

    @Mock private KnowledgeUsagePort usagePort;
    @Mock private ModelManagementService modelManagementService;
    @Mock private AiModel model;
    @Mock private Supplier<ChatResponse> invocation;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatResponse response;

    @Test
    @DisplayName("Given 已持久化 provider 结果 When 恢复 AI 调用 Then 不重复调用模型且按冻结模型结算")
    void should_settle_recovered_provider_result_without_invoking_provider() {
        var meter = new KnowledgeAiMeter(usagePort, modelManagementService);
        var runId = UUID.randomUUID();
        var billing = new KnowledgeUsagePort.BillingContext("tenant", runId, 10L);
        var input = "stable input";
        var digest = TrustedKnowledgeStore.sha256(MODEL_ID + "|" + input);
        var occurredAt = Instant.parse("2026-01-02T03:04:05Z");
        var providerJson = "{\"text\":\"restored\",\"promptTokens\":11,\"completionTokens\":7}";
        when(usagePort.findProviderResult(billing, "extraction", "chunk:1", digest))
                .thenReturn(
                        Optional.of(
                                new KnowledgeUsagePort.StoredProviderResult(
                                        "invocation-1",
                                        "stored-usage-key",
                                        digest,
                                        providerJson,
                                        "provider-1",
                                        occurredAt,
                                        false)));
        stubPricing();

        var result =
                meter.invokeText(
                        billing, "extraction", "chunk:1", MODEL_ID, input, invocation);

        assertThat(result).isEqualTo("restored");
        verify(invocation, never()).get();
        verify(usagePort, never()).reserve(any(), anyString(), anyString(), anyString());
        verify(modelManagementService).getModel(MODEL_ID);
        var callCaptor = ArgumentCaptor.forClass(KnowledgeUsagePort.UsageCall.class);
        verify(usagePort).settle(callCaptor.capture());
        assertThat(callCaptor.getValue().occurredAt()).isEqualTo(occurredAt);
        assertThat(callCaptor.getValue().providerRequestId()).isEqualTo("provider-1");
        assertThat(callCaptor.getValue().inputDigest()).isEqualTo(digest);
    }

    @Test
    @DisplayName("Given 新 AI 单元 When 调用 provider Then 先预留占用再调用且结果落盘后结算")
    void should_reserve_before_provider_and_persist_before_settlement() {
        var meter = new KnowledgeAiMeter(usagePort, modelManagementService);
        var runId = UUID.randomUUID();
        var billing = new KnowledgeUsagePort.BillingContext("tenant", runId, 10L);
        var input = "fresh input";
        var digest = TrustedKnowledgeStore.sha256(MODEL_ID + "|" + input);
        var reservation = reservation(billing, digest, KnowledgeUsagePort.ReservationState.RESERVED);
        when(usagePort.findProviderResult(billing, "extraction", "chunk:1", digest))
                .thenReturn(Optional.empty());
        when(usagePort.reserve(billing, "extraction", "chunk:1", digest)).thenReturn(reservation);
        when(usagePort.start(reservation)).thenReturn(true);
        when(invocation.get()).thenReturn(response);
        when(response.getResult().getOutput().getText()).thenReturn("fresh");
        when(response.getMetadata()).thenReturn(null);
        stubPricing();

        assertThat(
                        meter.invokeText(
                                billing, "extraction", "chunk:1", MODEL_ID, input, invocation))
                .isEqualTo("fresh");

        var ordered = inOrder(usagePort, invocation);
        ordered.verify(usagePort).precheck(any(), anyString(), anyLong());
        ordered.verify(usagePort).reserve(billing, "extraction", "chunk:1", digest);
        ordered.verify(usagePort).start(reservation);
        ordered.verify(invocation).get();
        ordered.verify(usagePort).persistProviderResult(any(), anyString(), anyString());
        ordered.verify(usagePort).settle(any());
        verify(modelManagementService).getModel(MODEL_ID);
    }

    @Test
    @DisplayName("Given 其他调用者已占用 When 恢复 Then 不调用 provider")
    void should_not_invoke_provider_when_reservation_is_busy() {
        var meter = new KnowledgeAiMeter(usagePort, modelManagementService);
        var billing = new KnowledgeUsagePort.BillingContext("tenant", UUID.randomUUID(), 10L);
        var input = "same input";
        var digest = TrustedKnowledgeStore.sha256(MODEL_ID + "|" + input);
        when(usagePort.findProviderResult(billing, "extraction", "chunk:1", digest))
                .thenReturn(Optional.empty());
        when(usagePort.reserve(billing, "extraction", "chunk:1", digest))
                .thenReturn(reservation(billing, digest, KnowledgeUsagePort.ReservationState.BUSY));

        assertThatThrownBy(
                        () ->
                                meter.invokeText(
                                        billing,
                                        "extraction",
                                        "chunk:1",
                                        MODEL_ID,
                                        input,
                                        invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("其他调用者");
        verify(invocation, never()).get();
    }

    @Test
    @DisplayName("Given modelId 为空 When 调用 Then 在预检前拒绝")
    void should_reject_blank_model_id() {
        var meter = new KnowledgeAiMeter(usagePort, modelManagementService);
        var billing = new KnowledgeUsagePort.BillingContext("tenant", UUID.randomUUID(), 10L);

        assertThatThrownBy(
                        () ->
                                meter.invokeText(
                                        billing, "extraction", "chunk:1", " ", "input", invocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelId");
        verify(usagePort, never()).precheck(any(), anyString(), anyLong());
    }

    private KnowledgeUsagePort.InvocationReservation reservation(
            KnowledgeUsagePort.BillingContext billing,
            String digest,
            KnowledgeUsagePort.ReservationState state) {
        var invocationId =
                KnowledgeUsagePort.invocationId(billing.runId(), "extraction", "chunk:1", digest);
        return new KnowledgeUsagePort.InvocationReservation(
                billing,
                "extraction",
                "chunk:1",
                digest,
                invocationId,
                KnowledgeUsagePort.usageKey(billing.runId(), "extraction", "chunk:1", invocationId),
                Instant.parse("2026-01-02T03:04:05Z"),
                UUID.randomUUID(),
                state,
                null,
                invocationId);
    }

    private void stubPricing() {
        when(modelManagementService.getModel(MODEL_ID)).thenReturn(model);
        when(model.getInputPricePerK()).thenReturn(new BigDecimal("0.5"));
        when(model.getOutputPricePerK()).thenReturn(new BigDecimal("1.0"));
    }
}
