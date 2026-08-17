package com.xuejiai.aaf.module.ai.assistant.port;

import java.time.Instant;
import java.util.Objects;

/** 外部消息渠道调用当前 Assistant 的唯一业务边界。 */
public interface ChannelAssistantExecutionPort {

    String execute(Request request);

    record Request(
            Long tenantId,
            Long ownerId,
            String assistantId,
            String channelCode,
            String bindingKey,
            String externalUserId,
            String sourceMessageId,
            String input,
            Instant receivedAt) {

        public Request {
            if (tenantId == null || tenantId <= 0) {
                throw new IllegalArgumentException("渠道绑定缺少有效 tenantId");
            }
            if (ownerId == null || ownerId <= 0) {
                throw new IllegalArgumentException("渠道绑定缺少有效 ownerId");
            }
            assistantId = requireText(assistantId, "assistantId");
            channelCode = requireText(channelCode, "channelCode");
            bindingKey = requireText(bindingKey, "bindingKey");
            externalUserId = requireText(externalUserId, "externalUserId");
            sourceMessageId = requireText(sourceMessageId, "sourceMessageId");
            input = requireText(input, "input");
            Objects.requireNonNull(receivedAt, "receivedAt 不能为空");
        }

        private static String requireText(String value, String name) {
            Objects.requireNonNull(value, name + " 不能为空");
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空白");
            }
            return value.trim();
        }
    }
}
