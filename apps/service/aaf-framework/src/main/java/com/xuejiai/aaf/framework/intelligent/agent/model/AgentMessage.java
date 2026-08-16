package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;

/** Agent 端口使用的安全消息，不暴露运行时消息类型。 */
public record AgentMessage(String messageId, Role role, String text, List<Attachment> attachments) {

    public AgentMessage {
        Objects.requireNonNull(messageId, "messageId 不能为空");
        Objects.requireNonNull(role, "role 不能为空");
        Objects.requireNonNull(text, "text 不能为空");
        attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments 不能为空"));
        if (messageId.isBlank()) {
            throw new IllegalArgumentException("messageId 不能为空白");
        }
        if (!attachments.isEmpty() && role != Role.USER) {
            throw new IllegalArgumentException("仅 USER 消息允许携带附件");
        }
    }

    public AgentMessage(String messageId, Role role, String text) {
        this(messageId, role, text, List.of());
    }

    /** 消息附件。 */
    public record Attachment(
            AttachmentType type, String resourceId, String signedUrl, String mimeType) {

        public Attachment {
            Objects.requireNonNull(type, "附件 type 不能为空");
            resourceId = requireText(resourceId, "附件 resourceId 不能为空");
            signedUrl = requireText(signedUrl, "附件 signedUrl 不能为空");
            mimeType = requireText(mimeType, "附件 mimeType 不能为空");
            if (type == AttachmentType.IMAGE && !mimeType.startsWith("image/")) {
                throw new IllegalArgumentException("IMAGE 附件 mimeType 必须为 image/*");
            }
        }

        private static String requireText(String value, String message) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(message);
            }
            return value.trim();
        }
    }

    /** 附件类型。 */
    public enum AttachmentType {
        IMAGE
    }

    /** 消息作者角色。 */
    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }
}
