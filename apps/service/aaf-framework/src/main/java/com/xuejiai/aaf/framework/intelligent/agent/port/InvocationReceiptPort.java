package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

/** 所有本地和外部写工具共用的 invocation receipt。 */
public interface InvocationReceiptPort {

    Claim claim(ReceiptRequest request);

    void complete(
            String receiptKey, InvocationContext context, ToolInvocationResult result, Instant at);

    void fail(String receiptKey, InvocationContext context, String failure, Instant at);

    record ReceiptRequest(
            String receiptKey,
            String requestDigest,
            String toolId,
            String actionKey,
            InvocationContext context,
            Instant requestedAt) {
        public ReceiptRequest {
            if (receiptKey == null
                    || receiptKey.isBlank()
                    || requestDigest == null
                    || requestDigest.isBlank()
                    || toolId == null
                    || toolId.isBlank()
                    || actionKey == null
                    || actionKey.isBlank()) {
                throw new IllegalArgumentException("receiptKey、digest、toolId、actionKey 不能为空白");
            }
            Objects.requireNonNull(context, "context 不能为空");
            Objects.requireNonNull(requestedAt, "requestedAt 不能为空");
        }
    }

    record Claim(Disposition disposition, ToolInvocationResult existingResult) {
        public Claim {
            Objects.requireNonNull(disposition, "disposition 不能为空");
            if (disposition == Disposition.REPLAY && existingResult == null) {
                throw new IllegalArgumentException("REPLAY 必须携带已有结果");
            }
        }
    }

    enum Disposition {
        CLAIMED,
        REPLAY,
        IN_PROGRESS
    }
}
