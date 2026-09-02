package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.middleware;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.port.PromptEnvelopePort;
import com.xuejiai.aaf.framework.intelligent.core.prompt.InvocationMode;
import com.xuejiai.aaf.framework.intelligent.core.prompt.InvocationPurpose;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptEnvelope;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 在 provider 发送前冻结一份 {@link PromptEnvelope}。
 *
 * <p>{@code onModelCall} 是 AgentScope 提供的 raw model call 切点，也是 AAF 在循环外部唯一能拿到**真实**请求内容 （messages /
 * tools / options / model）的位置。旁观 {@code MODEL_CALL_START} 事件只能记录"AAF 授予的边界"， 不等于实际发出的请求，因此不作为冻结点。
 *
 * <p>披露约束：只落 hash、角色与长度，不落消息正文与工具 Schema 正文；凭据类选项（apiKey / baseUrl / endpointPath /
 * additionalHeaders）一律不参与哈希，避免把密钥间接写入审计数据。
 *
 * <p>重试识别：与该执行最新一份信封的 {@code promptSha256} 相同即视为传输层重试，推进 {@code attemptNo} 并标记 {@code
 * RETRY}；内容变化则是循环的下一轮，{@code attemptNo} 归 1。
 */
@Slf4j
public final class PromptEnvelopeCaptureMiddleware implements MiddlewareBase {

    private final PromptEnvelopePort envelopes;
    private final Clock clock;

    public PromptEnvelopeCaptureMiddleware(PromptEnvelopePort envelopes, Clock clock) {
        this.envelopes = Objects.requireNonNull(envelopes, "envelopes 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        var context = ctx == null ? null : ctx.get(InvocationContext.class);
        if (context == null) {
            // 上下文缺失说明链路被破坏：不静默漏记，直接拒绝发送。
            return Flux.error(new IllegalStateException("模型调用缺少 typed InvocationContext，无法冻结信封"));
        }
        return Mono.fromCallable(() -> freeze(context, input))
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(Flux.defer(() -> next.apply(input)));
    }

    private PromptEnvelope freeze(InvocationContext context, ModelCallInput input) {
        var messages = messageSnapshots(input.messages());
        var tools = toolSnapshots(input.tools());
        var modelId = modelId(input);
        var optionsSha256 = PromptEnvelope.sha256(canonicalOptions(input.options()));
        var promptSha256 = PromptEnvelope.canonicalSha256(modelId, optionsSha256, messages, tools);
        var latest = envelopes.findLatest(context.tenantId(), context.executionId()).orElse(null);
        var retry = latest != null && latest.promptSha256().equals(promptSha256);
        var draft =
                new PromptEnvelope.Draft(
                        UUID.randomUUID().toString(),
                        context.tenantId(),
                        context.taskId(),
                        context.executionId(),
                        retry ? latest.attemptNo() + 1 : 1,
                        retry ? PromptEnvelope.Trigger.RETRY : PromptEnvelope.Trigger.INITIAL,
                        InvocationMode.AUTONOMOUS_AGENT_LOOP,
                        InvocationPurpose.HARNESS_EXECUTION,
                        modelId,
                        optionsSha256,
                        messages,
                        tools,
                        promptSha256,
                        clock.instant());
        var frozen = envelopes.append(draft);
        log.debug(
                "[PromptEnvelope] 已冻结物理调用信封：executionId={}，seq={}，attempt={}，trigger={}，model={}，消息数={}，工具数={}，promptSha256={}",
                frozen.executionId().value(),
                frozen.envelopeSeq(),
                frozen.attemptNo(),
                frozen.trigger(),
                frozen.modelId(),
                frozen.messages().size(),
                frozen.tools().size(),
                frozen.promptSha256());
        return frozen;
    }

    private static List<PromptEnvelope.MessageSnapshot> messageSnapshots(List<Msg> messages) {
        var snapshots = new ArrayList<PromptEnvelope.MessageSnapshot>();
        if (messages == null) {
            return snapshots;
        }
        for (var index = 0; index < messages.size(); index++) {
            var message = messages.get(index);
            var text = message.getTextContent() == null ? "" : message.getTextContent();
            snapshots.add(
                    new PromptEnvelope.MessageSnapshot(
                            index,
                            message.getRole() == null ? "UNKNOWN" : message.getRole().name(),
                            text.codePointCount(0, text.length()),
                            PromptEnvelope.sha256(text)));
        }
        return snapshots;
    }

    private static List<PromptEnvelope.ToolSchemaSnapshot> toolSnapshots(List<ToolSchema> tools) {
        var snapshots = new ArrayList<PromptEnvelope.ToolSchemaSnapshot>();
        if (tools == null) {
            return snapshots;
        }
        for (var tool : tools) {
            var canonical =
                    new StringBuilder()
                            .append(tool.getName())
                            .append('\u0000')
                            .append(tool.getDescription())
                            .append('\u0000')
                            .append(String.valueOf(tool.getParameters()))
                            .append('\u0000')
                            .append(String.valueOf(tool.getOutputSchema()))
                            .append('\u0000')
                            .append(tool.getStrict())
                            .toString();
            snapshots.add(
                    new PromptEnvelope.ToolSchemaSnapshot(
                            tool.getName() == null ? "unnamed" : tool.getName(),
                            PromptEnvelope.sha256(canonical)));
        }
        return snapshots;
    }

    private static String modelId(ModelCallInput input) {
        var optionModel = input.options() == null ? null : input.options().getModelName();
        if (optionModel != null && !optionModel.isBlank()) {
            return optionModel;
        }
        return input.model() == null ? "unknown" : input.model().getClass().getSimpleName();
    }

    /** 只纳入影响生成结果的参数；凭据与端点信息一律排除。 */
    private static String canonicalOptions(GenerateOptions options) {
        if (options == null) {
            return "none";
        }
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("modelName", options.getModelName());
        values.put("stream", options.getStream());
        values.put("temperature", options.getTemperature());
        values.put("topP", options.getTopP());
        values.put("topK", options.getTopK());
        values.put("maxTokens", options.getMaxTokens());
        values.put("maxCompletionTokens", options.getMaxCompletionTokens());
        values.put("frequencyPenalty", options.getFrequencyPenalty());
        values.put("presencePenalty", options.getPresencePenalty());
        values.put("thinkingBudget", options.getThinkingBudget());
        values.put("reasoningEffort", options.getReasoningEffort());
        values.put("toolChoice", options.getToolChoice());
        values.put("seed", options.getSeed());
        values.put("cacheControl", options.getCacheControl());
        values.put("parallelToolCalls", options.getParallelToolCalls());
        values.put("responseFormat", options.getResponseFormat());
        values.put("additionalBodyParams", options.getAdditionalBodyParams());
        var canonical = new StringBuilder();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            canonical
                    .append(entry.getKey())
                    .append('=')
                    .append(String.valueOf(entry.getValue()))
                    .append(';');
        }
        return canonical.toString();
    }
}
