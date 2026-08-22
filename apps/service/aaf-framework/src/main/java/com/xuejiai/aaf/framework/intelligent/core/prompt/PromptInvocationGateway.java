package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 非自主 L0 的逻辑调用入口。
 *
 * <p>当前切片只做最小消息校验、脱敏长度 preflight 和 {@link LlmClient} 委托，不加载 Constitution、Persona、Role、Skill
 * 或自主任务循环。底层模型路由与 fallback 可能产生多个物理请求，因此这里的 logicalInvocationId 不能视为物理请求 ID。
 */
@Slf4j
public final class PromptInvocationGateway {
    private static final Pattern MACHINE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final LlmClient llmClient;

    public PromptInvocationGateway(LlmClient llmClient) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient 不能为空");
    }

    /** 发起一次无工具的非自主逻辑调用；异常原样交给具体函数决定 fail-closed 或安全默认值。 */
    public String call(NonAutonomousInvocation invocation) {
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
        var modelMessages = invocation.messages().stream().map(ClassifiedMessage::message).toList();
        return llmClient.call(modelMessages, invocation.routeScene(), invocation.meteringUserId());
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

    /** 带来源分类的 L0 消息；分类由调用方证明，Gateway 校验分类与模型角色兼容。 */
    public record ClassifiedMessage(PromptInputKind inputKind, LlmClient.LlmMessage message) {
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
            return new ClassifiedMessage(
                    PromptInputKind.SYSTEM, LlmClient.LlmMessage.system(content));
        }

        public static ClassifiedMessage currentUser(String content) {
            return new ClassifiedMessage(
                    PromptInputKind.CURRENT_USER_INPUT, LlmClient.LlmMessage.user(content));
        }

        public static ClassifiedMessage otherUser(String content) {
            return new ClassifiedMessage(
                    PromptInputKind.OTHER_USER_INPUT, LlmClient.LlmMessage.user(content));
        }

        public static ClassifiedMessage controlledContext(String content) {
            return new ClassifiedMessage(
                    PromptInputKind.CONTROLLED_CONTEXT, LlmClient.LlmMessage.user(content));
        }
    }

    /** 非自主 L0 的最小逻辑调用合同；不包含工具和自主循环。 */
    public record NonAutonomousInvocation(
            InvocationPurpose purpose,
            String functionKey,
            List<ClassifiedMessage> messages,
            String routeScene,
            Long meteringUserId) {
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

        private static String requireMachineKey(String value, String field) {
            if (value == null || !MACHINE_KEY.matcher(value).matches()) {
                throw new IllegalArgumentException(field + " 必须是稳定机器标识");
            }
            return value;
        }
    }
}
