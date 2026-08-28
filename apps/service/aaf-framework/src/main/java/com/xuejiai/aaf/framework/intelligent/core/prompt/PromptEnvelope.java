package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/**
 * 一次物理模型请求的信封。
 *
 * <p>对应 provider 适配之后、请求实际发送之前的那一刻：此时最终模型、生成参数、消息顺序与工具 Schema 均已确定。信封 append-only， 一次发送一份，重试推进
 * {@code attemptNo} 而非复用；不回写 {@code ExecutionProfileSnapshot}——后者是执行不变量，只能被引用。
 *
 * <p>按 {@code core/prompt.md} 的披露约束，本记录只保存 hash、长度与引用，不持有消息正文与工具 Schema 正文；完整正文进访问受控的 Prompt
 * 快照存储，不在本类型内。
 */
public record PromptEnvelope(
        String envelopeId,
        TenantId tenantId,
        TaskId taskId,
        ExecutionId executionId,
        int envelopeSeq,
        int attemptNo,
        Trigger trigger,
        InvocationMode mode,
        InvocationPurpose purpose,
        String modelId,
        String generateOptionsSha256,
        List<MessageSnapshot> messages,
        List<ToolSchemaSnapshot> tools,
        String promptSha256,
        Instant createdAt) {

    public PromptEnvelope {
        envelopeId = requireText(envelopeId, "envelopeId");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        if (envelopeSeq < 1) {
            throw new IllegalArgumentException("envelopeSeq 必须从 1 起递增");
        }
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo 必须从 1 起递增");
        }
        Objects.requireNonNull(trigger, "trigger 不能为空");
        Objects.requireNonNull(mode, "mode 不能为空");
        Objects.requireNonNull(purpose, "purpose 不能为空");
        modelId = requireText(modelId, "modelId");
        generateOptionsSha256 = requireText(generateOptionsSha256, "generateOptionsSha256");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为空"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空列表");
        }
        tools = List.copyOf(Objects.requireNonNull(tools, "tools 不能为空"));
        promptSha256 = requireText(promptSha256, "promptSha256");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
    }

    /**
     * 尚未分配 {@code envelopeSeq} 的信封草稿。
     *
     * <p>序号必须由存储层在同一事务内按 execution 分配，调用方不得自行编号——否则并发下会产生空洞或冲突。
     */
    public record Draft(
            String envelopeId,
            TenantId tenantId,
            TaskId taskId,
            ExecutionId executionId,
            int attemptNo,
            Trigger trigger,
            InvocationMode mode,
            InvocationPurpose purpose,
            String modelId,
            String generateOptionsSha256,
            List<MessageSnapshot> messages,
            List<ToolSchemaSnapshot> tools,
            String promptSha256,
            Instant createdAt) {

        public PromptEnvelope withSeq(int envelopeSeq) {
            return new PromptEnvelope(
                    envelopeId,
                    tenantId,
                    taskId,
                    executionId,
                    envelopeSeq,
                    attemptNo,
                    trigger,
                    mode,
                    purpose,
                    modelId,
                    generateOptionsSha256,
                    messages,
                    tools,
                    promptSha256,
                    createdAt);
        }
    }

    /** 信封产生的原因；与 {@code attemptNo} 正交。 */
    public enum Trigger {
        /** 执行首次进入模型，或 ReAct 循环内的常规下一轮。 */
        INITIAL,
        /** 同一逻辑调用的传输层重试。 */
        RETRY,
        /** 上下文压缩改变了消息集合。 */
        COMPACTED,
        /** 运行中加载了技能正文，工具集或正文因此增长。 */
        SKILL_LOADED,
        /** 运行中加载了技能参考资料。 */
        REFERENCE_LOADED
    }

    /** 单条消息的披露安全投影：只有角色、长度与正文 hash。 */
    public record MessageSnapshot(int index, String role, int characters, String contentSha256) {
        public MessageSnapshot {
            if (index < 0) {
                throw new IllegalArgumentException("消息序号不能为负");
            }
            role = requireText(role, "role");
            if (characters < 0) {
                throw new IllegalArgumentException("消息长度不能为负");
            }
            contentSha256 = requireText(contentSha256, "contentSha256");
        }
    }

    /** 单个工具 Schema 的披露安全投影：工具可见不代表动作获权。 */
    public record ToolSchemaSnapshot(String name, String schemaSha256) {
        public ToolSchemaSnapshot {
            name = requireText(name, "name");
            schemaSha256 = requireText(schemaSha256, "schemaSha256");
        }
    }

    /** 对模型、参数、消息与工具的规范化联合 hash；同一请求内容必得同一值。 */
    public static String canonicalSha256(
            String modelId,
            String generateOptionsSha256,
            List<MessageSnapshot> messages,
            List<ToolSchemaSnapshot> tools) {
        var canonical = new StringBuilder();
        append(canonical, "model", modelId);
        append(canonical, "options", generateOptionsSha256);
        for (var message : messages) {
            append(
                    canonical,
                    "msg",
                    message.index() + ":" + message.role() + ":" + message.contentSha256());
        }
        for (var tool : tools) {
            append(canonical, "tool", tool.name() + ":" + tool.schemaSha256());
        }
        return sha256(canonical.toString());
    }

    public static String sha256(String value) {
        Objects.requireNonNull(value, "待哈希内容不能为空");
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境不支持 SHA-256", failure);
        }
    }

    /** 长度前缀分隔，避免不同字段拼接产生同一规范化串。 */
    private static void append(StringBuilder target, String field, String value) {
        var normalized = value == null ? "" : value;
        target.append(field)
                .append('=')
                .append(normalized.length())
                .append(':')
                .append(normalized)
                .append(';');
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
