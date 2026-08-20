package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.AuthorizationDecision;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.AuthorizationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.ClarificationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.CommittedParent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.InputCommit;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.InputTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.IterationCommit;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.IterationEvaluationTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.ParentFailureTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskTransition.ParentStateTransition;

/** 委托任务状态、看板、治理事实、事件和 outbox 的原子提交边界。 */
public interface TaskTransitionPort {

    DelegatedTask create(TaskTransition transition);

    HumanApproval requestAuthorization(AuthorizationRequestTransition transition);

    ClarificationRequest requestClarification(ClarificationRequestTransition transition);

    Optional<InputCommit> consumeInputs(InputTransition transition);

    IterationCommit evaluateIteration(IterationEvaluationTransition transition);

    CommittedParent failOrRetry(ParentFailureTransition transition);

    CommittedParent commitParent(ParentStateTransition transition);

    HumanApproval decideAuthorization(
            AuthorizationDecision transition, ConversationLeasePort.Lease lease);

    int publishOutbox(int limit);

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
