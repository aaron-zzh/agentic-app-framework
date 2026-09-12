package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HitlTransition.AuthorizationDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HitlTransition.AuthorizationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HitlTransition.ClarificationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;

/** 委托任务状态、看板、治理事实、事件和 outbox 的原子提交边界。 */
public interface HitlTransitionPort {

    HumanApproval requestAuthorization(AuthorizationRequestTransition transition);

    ClarificationRequest requestClarification(ClarificationRequestTransition transition);

    ClarificationCommit resumeClarification(
            ExecutionInput input, ConversationLeasePort.Lease lease);

    HumanApproval decideAuthorization(
            AuthorizationDecision transition, ConversationLeasePort.Lease lease);

    int publishOutbox(int limit);

    record ClarificationCommit(
            Task task,
            ClarificationRequest clarification,
            com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId executionId,
            boolean resumed) {
        public ClarificationCommit {
            Objects.requireNonNull(task, "task 不能为空");
            Objects.requireNonNull(clarification, "clarification 不能为空");
            Objects.requireNonNull(executionId, "executionId 不能为空");
        }
    }

    enum OutboxRelayFailureType {
        CLAIM_FAILED,
        EVENT_MISSING_OR_MISMATCHED,
        DELIVERY_REJECTED,
        STATE_WRITE_FAILED
    }

    final class OutboxRelayException extends IllegalStateException {
        private final OutboxRelayFailureType failureType;

        public OutboxRelayException(OutboxRelayFailureType failureType, RuntimeException cause) {
            super("transition outbox relay 安全失败: " + failureType.name(), cause);
            this.failureType = Objects.requireNonNull(failureType, "failureType 不能为空");
        }

        public OutboxRelayFailureType failureType() {
            return failureType;
        }
    }
}
