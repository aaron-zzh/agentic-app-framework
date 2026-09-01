package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * 非自主 L0 的逻辑调用入口。
 *
 * <p>当前切片只做最小消息校验、脱敏长度 preflight 和对零工具 {@code ReActAgent} 的委托，不加载 Constitution、Persona、Role、Skill
 * 或自主任务循环。模型选择完全由 {@link CapabilityRouter} 承担（依 ADR-007）——{@code CapabilityRouter} 是 per-call
 * 路由，而 {@code ReActAgent.builder().model(...)} 是构造期固定参数，两者不能直接合一，因此按 {@link
 * AiModel#getModelId()} 对 {@code ReActAgent} 实例分桶缓存，不是全局唯一单例：同一模型复用同一实例，不同模型各自持有独立实例；每个实例
 * 均为零工具（不触发工具循环）、无状态（不设 {@code stateStore}）、不设 {@code fallbackModel}（不静默切模型）。
 */
@Slf4j
public final class PromptInvocationGateway {
    private static final Pattern MACHINE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final ConcurrentHashMap<String, ReActAgent> agentsByModelId = new ConcurrentHashMap<>();
    private final Function<AiModel, ReActAgent> agentFactory;
    private final CapabilityRouter capabilityRouter;

    public PromptInvocationGateway(
            Function<AiModel, ReActAgent> agentFactory, CapabilityRouter capabilityRouter) {
        this.agentFactory = Objects.requireNonNull(agentFactory, "agentFactory 不能为空");
        this.capabilityRouter =
                Objects.requireNonNull(capabilityRouter, "capabilityRouter 不能为空");
    }

    /** 发起一次无工具的非自主逻辑调用；异常原样交给具体函数决定 fail-closed 或安全默认值。 */
    public ModelInvocationResult call(NonAutonomousInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        var logicalInvocationId = UUID.randomUUID().toString().replace("-", "");
        var lengths = measure(invocation.messages());
        var system = lengths.input(PromptInputKind.SYSTEM);
        var currentUser = lengths.input(PromptInputKind.CURRENT_USER_INPUT);
        var otherUser = lengths.input(PromptInputKind.OTHER_USER_INPUT);
        var controlledContext = lengths.input(PromptInputKind.CONTROLLED_CONTEXT);
        var assistantHistory = lengths.input(PromptInputKind.ASSISTANT_HISTORY);
        log.info(
                "[Prompt预检] boundary=L0_LOGICAL_INVOCATION mode={} logicalInvocationId={} purpose={} functionKey={} routeScene={} systemChars={} systemEstimatedTokens={} currentUserChars={} currentUserEstimatedTokens={} otherUserChars={} otherUserEstimatedTokens={} controlledContextChars={} controlledContextEstimatedTokens={} assistantHistoryChars={} assistantHistoryEstimatedTokens={} totalChars={} totalEstimatedTokens={}",
                InvocationMode.NON_AUTONOMOUS_L0,
                logicalInvocationId,
                invocation.purpose(),
                invocation.functionKey(),
                invocation.routeScene(),
                system.characters(),
                system.estimatedTokensAtFourCodePoints(),
                currentUser.characters(),
                currentUser.estimatedTokensAtFourCodePoints(),
                otherUser.characters(),
                otherUser.estimatedTokensAtFourCodePoints(),
                controlledContext.characters(),
                controlledContext.estimatedTokensAtFourCodePoints(),
                assistantHistory.characters(),
                assistantHistory.estimatedTokensAtFourCodePoints(),
                lengths.totalCharacters(),
                lengths.totalEstimatedTokens());

        var routingContext =
                CapabilityRoutingContext.of(
                        invocation.meteringUserId(),
                        invocation.routeScene(),
                        invocation.explicitModelId());
        var model = capabilityRouter.resolve(routingContext);
        var agent = agentsByModelId.computeIfAbsent(model.getModelId(), key -> agentFactory.apply(model));

        var systemPrompt =
                invocation.messages().stream()
                        .filter(m -> m.inputKind() == PromptInputKind.SYSTEM)
                        .findFirst()
                        .map(m -> m.message().content())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "非自主 L0 缺少 SYSTEM 消息，无法构造 Function Contract"));
        var agentMessages = toAgentScopeMessages(invocation.messages());

        var ctx =
                RuntimeContext.builder()
                        .sessionId(logicalInvocationId)
                        .put(
                                FunctionContractSystemPrompt.class,
                                new FunctionContractSystemPrompt(systemPrompt))
                        .build();

        var result = agent.call(agentMessages, ctx).block();
        return toInvocationResult(result);
    }

    private static List<Msg> toAgentScopeMessages(List<ClassifiedMessage> messages) {
        return messages.stream()
                .filter(m -> m.inputKind() != PromptInputKind.SYSTEM)
                .<Msg>map(
                        m ->
                                switch (m.message().role()) {
                                    case "assistant" ->
                                            new AssistantMessage(m.message().content());
                                    default -> new UserMessage(m.message().content());
                                })
                .toList();
    }

    private static ModelInvocationResult toInvocationResult(Msg result) {
        var text = result.getTextContent();
        var usage = result.getUsage();
        var inputTokens = usage != null ? usage.getInputTokens() : 0L;
        var outputTokens = usage != null ? usage.getOutputTokens() : 0L;
        var generateReason = result.getGenerateReason();
        var finishReason = generateReason != null ? generateReason.toString() : null;
        return new ModelInvocationResult(text, inputTokens, outputTokens, finishReason);
    }

    private static PromptLengthSummary measure(List<ClassifiedMessage> messages) {
        var contents = new EnumMap<PromptInputKind, List<String>>(PromptInputKind.class);
        for (var kind : PromptInputKind.values()) {
            contents.put(kind, new ArrayList<>());
        }
        messages.forEach(
                message -> contents.get(message.inputKind()).add(message.message().content()));
        return PromptLengthSummary.measure(contents, 0);
    }

    /** L0 调用结果：文本 + usage + 结束原因。替代旧 {@code LlmClient} 的裸 {@code String} 返回值。 */
    public record ModelInvocationResult(
            String text, long inputTokens, long outputTokens, String finishReason) {}

    /** 带来源分类的 L0 消息；分类由调用方证明，Gateway 校验分类与模型角色兼容。 */
    public record ClassifiedMessage(PromptInputKind inputKind, LlmMessage message) {
        public ClassifiedMessage {
            Objects.requireNonNull(inputKind, "inputKind 不能为空");
            Objects.requireNonNull(message, "message 不能为空");
            Objects.requireNonNull(message.role(), "message role 不能为空");
            Objects.requireNonNull(message.content(), "message content 不能为空");
            var compatible =
                    switch (inputKind) {
                        case SYSTEM -> "system".equals(message.role());
                        case CURRENT_USER_INPUT, OTHER_USER_INPUT, CONTROLLED_CONTEXT ->
                                "user".equals(message.role());
                        case ASSISTANT_HISTORY -> "assistant".equals(message.role());
                        case ASSISTANT_REASONING, TOOL_RESULT, TOOL_REFERENCE, TOOL_DEFINITION ->
                                false;
                    };
            if (!compatible) {
                throw new IllegalArgumentException(
                        "非自主 L0 输入分类与消息角色不兼容: " + inputKind + "/" + message.role());
            }
        }

        public static ClassifiedMessage system(String content) {
            return new ClassifiedMessage(PromptInputKind.SYSTEM, LlmMessage.system(content));
        }

        public static ClassifiedMessage currentUser(String content) {
            return new ClassifiedMessage(
                    PromptInputKind.CURRENT_USER_INPUT, LlmMessage.user(content));
        }

        public static ClassifiedMessage otherUser(String content) {
            return new ClassifiedMessage(
                    PromptInputKind.OTHER_USER_INPUT, LlmMessage.user(content));
        }

        public static ClassifiedMessage controlledContext(String content) {
            return new ClassifiedMessage(
                    PromptInputKind.CONTROLLED_CONTEXT, LlmMessage.user(content));
        }
    }

    /** L0 消息载体，替代旧 {@code LlmClient.LlmMessage}。 */
    public record LlmMessage(String role, String content) {
        public static LlmMessage system(String content) {
            return new LlmMessage("system", content);
        }

        public static LlmMessage user(String content) {
            return new LlmMessage("user", content);
        }

        public static LlmMessage assistant(String content) {
            return new LlmMessage("assistant", content);
        }
    }

    /** 非自主 L0 的最小逻辑调用合同；不包含工具和自主循环。 */
    public record NonAutonomousInvocation(
            InvocationPurpose purpose,
            String functionKey,
            List<ClassifiedMessage> messages,
            String routeScene,
            Long meteringUserId,
            String explicitModelId) {
        public NonAutonomousInvocation {
            Objects.requireNonNull(purpose, "purpose 不能为空");
            functionKey = requireMachineKey(functionKey, "functionKey");
            routeScene = requireMachineKey(routeScene, "routeScene");
            messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为空"));
            if (messages.isEmpty()) {
                throw new IllegalArgumentException("非自主 L0 messages 不能为空");
            }
            var systemCount =
                    messages.stream()
                            .filter(message -> message.inputKind() == PromptInputKind.SYSTEM)
                            .count();
            var userDataCount =
                    messages.stream()
                            .filter(
                                    message ->
                                            message.inputKind()
                                                            == PromptInputKind.CURRENT_USER_INPUT
                                                    || message.inputKind()
                                                            == PromptInputKind.OTHER_USER_INPUT
                                                    || message.inputKind()
                                                            == PromptInputKind.CONTROLLED_CONTEXT)
                            .count();
            if (systemCount != 1) {
                throw new IllegalArgumentException("非自主 L0 必须且只能包含一个 Function Contract SYSTEM 消息");
            }
            if (userDataCount < 1) {
                throw new IllegalArgumentException("非自主 L0 至少需要一个数据消息");
            }
        }

        /** 兼容既有调用方：不显式指定模型，完全走 {@code routeScene} 对应的路由决策链。 */
        public NonAutonomousInvocation(
                InvocationPurpose purpose,
                String functionKey,
                List<ClassifiedMessage> messages,
                String routeScene,
                Long meteringUserId) {
            this(purpose, functionKey, messages, routeScene, meteringUserId, null);
        }

        private static String requireMachineKey(String value, String field) {
            if (value == null || !MACHINE_KEY.matcher(value).matches()) {
                throw new IllegalArgumentException(field + " 必须是稳定机器标识");
            }
            return value;
        }
    }
}
