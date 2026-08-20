package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextCompressionSnapshot;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;

/** Harness 模型调用前的 L1 动态上下文压缩边界。 */
public interface ContextCompressionPort {

    ContextCompressionSnapshot compress(
            CompressionRequest request, Optional<ContextCompressionSnapshot> frozenSnapshot);

    record CompressionRequest(
            String systemPrompt,
            List<AgentMessage> messages,
            String currentUserMessageId,
            ModelSpec executionModel,
            Long meteringUserId) {

        public CompressionRequest {
            java.util.Objects.requireNonNull(systemPrompt, "systemPrompt 不能为空");
            messages = List.copyOf(java.util.Objects.requireNonNull(messages, "messages 不能为空"));
            if (messages.isEmpty()) {
                throw new IllegalArgumentException("messages 不能为空");
            }
            if (currentUserMessageId == null || currentUserMessageId.isBlank()) {
                throw new IllegalArgumentException("currentUserMessageId 不能为空白");
            }
            java.util.Objects.requireNonNull(executionModel, "executionModel 不能为空");
        }
    }
}
