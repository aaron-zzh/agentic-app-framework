package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.List;
import java.util.Objects;

/**
 * 与 {@link PromptEnvelope} 一一对应的完整 Prompt 正文。
 *
 * <p>{@code PromptEnvelope} 仍只承载 hash、长度与引用；本记录单独承载 System/User/Assistant 消息正文与工具 Schema 正文，
 * 落在独立表中，读取需显式查询，信封的重试检测与恢复路径不加载正文。
 *
 * <p>治理现状（AAF-113 #11328，人类决策 2026-09-18）：正文**不加密、不做访问审计、不到期删除**。因此本存储是长期明文留存，
 * 只用于调试与质量分析；后续若开启加密或保留期治理，在本类型与其存储上扩展，不新建第二份正文副本。
 *
 * <p>正文必须与信封摘要一致：消息条数、顺序、角色与逐条 {@code sha256(text)} 均由存储层对齐校验，防止正文与 manifest 漂移。
 */
public record PromptEnvelopeBody(
        String envelopeId, List<MessageBody> messages, List<ToolSchemaBody> tools) {

    public PromptEnvelopeBody {
        envelopeId = requireText(envelopeId, "envelopeId");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为空"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空列表");
        }
        tools = List.copyOf(Objects.requireNonNull(tools, "tools 不能为空"));
    }

    /** 单条消息正文；{@code index} 与 {@code role} 必须与信封投影逐项一致。 */
    public record MessageBody(int index, String role, String text) {
        public MessageBody {
            if (index < 0) {
                throw new IllegalArgumentException("消息序号不能为负");
            }
            role = requireText(role, "role");
            Objects.requireNonNull(text, "消息正文不能为 null");
        }
    }

    /** 单个工具 Schema 正文；工具可见不代表动作获权。 */
    public record ToolSchemaBody(
            String name,
            String description,
            String parameters,
            String outputSchema,
            Boolean strict) {
        public ToolSchemaBody {
            name = requireText(name, "name");
        }
    }

    /** 与信封投影逐项比对；不一致即拒绝落库，避免正文与 manifest 漂移。 */
    public void requireConsistentWith(PromptEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope 不能为空");
        if (messages.size() != envelope.messages().size()
                || tools.size() != envelope.tools().size()) {
            throw new IllegalArgumentException("Prompt 正文与信封投影条目数不一致");
        }
        for (var index = 0; index < messages.size(); index++) {
            var body = messages.get(index);
            var snapshot = envelope.messages().get(index);
            if (body.index() != snapshot.index()
                    || !body.role().equals(snapshot.role())
                    || !PromptEnvelope.sha256(body.text()).equals(snapshot.contentSha256())) {
                throw new IllegalArgumentException("Prompt 消息正文与信封摘要不一致：index=" + index);
            }
        }
        for (var index = 0; index < tools.size(); index++) {
            if (!tools.get(index).name().equals(envelope.tools().get(index).name())) {
                throw new IllegalArgumentException("Prompt 工具正文与信封投影顺序不一致：index=" + index);
            }
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
