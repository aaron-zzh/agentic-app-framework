package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import java.util.concurrent.TimeoutException;

import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort.ApprovalRequiredException;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort.BudgetExceededException;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort.StaleExecutionException;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextBudgetExceededException;

/**
 * 把执行期异常收敛为稳定的失败分类契约（RQ-04）。
 *
 * <p>调用方不得依赖异常类简单名做重试决策——类名随重构变化且不是契约。分类与 {@code retryable} 才是对外稳定字段，异常原文与堆栈只写服务端日志。
 */
public final class AgentScopeFailureClassifier {

    private AgentScopeFailureClassifier() {}

    /** 失败类别：一个类别对应一种处置方式，不按异常类型细分。 */
    public enum FailureCategory {
        /** 时限耗尽（总时限 / 事件静默 / 入库 SLA）：换更宽时限或降载后可重试。 */
        TIMEOUT(true),
        /** 会话租约换代或执行已过期：本次执行必须作废，由新代际重新发起。 */
        FENCED(false),
        /** 预算 / 上下文额度耗尽：重试只会再次耗尽，必须先调预算或压缩上下文。 */
        BUDGET_EXCEEDED(false),
        /** 等待人工授权：需人类动作，不可自动重试。 */
        AUTHORIZATION_REQUIRED(false),
        /** 输入或规格非法：修正入参前重试无意义。 */
        INVALID_REQUEST(false),
        /** 基础设施故障（数据库 / Redis / 网络 / 模型服务）：瞬时性居多，可重试。 */
        INFRASTRUCTURE(true),
        /** 无法归类：保守判定为不可自动重试，必须人工介入定位。 */
        UNKNOWN(false);

        private final boolean retryable;

        FailureCategory(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    /** 按异常语义归类；只认显式契约异常，其余落 UNKNOWN 而不是乐观判为可重试。 */
    public static FailureCategory classify(Throwable failure) {
        if (failure == null) {
            return FailureCategory.UNKNOWN;
        }
        return switch (failure) {
            case TimeoutException ignored -> FailureCategory.TIMEOUT;
            case ExecutionDeadlineExceededException ignored -> FailureCategory.TIMEOUT;
            case StaleExecutionException ignored -> FailureCategory.FENCED;
            case ContextBudgetExceededException ignored -> FailureCategory.BUDGET_EXCEEDED;
            case BudgetExceededException ignored -> FailureCategory.BUDGET_EXCEEDED;
            case ApprovalRequiredException ignored -> FailureCategory.AUTHORIZATION_REQUIRED;
            case IllegalArgumentException ignored -> FailureCategory.INVALID_REQUEST;
            default -> classifyByCause(failure);
        };
    }

    /** Reactor 会把源异常包一层，因此归类必须穿透 cause 链（最多 5 层，防环）。 */
    private static FailureCategory classifyByCause(Throwable failure) {
        var cause = failure.getCause();
        var depth = 0;
        while (cause != null && cause != failure && depth < 5) {
            var category = classify(cause);
            if (category != FailureCategory.UNKNOWN) {
                return category;
            }
            cause = cause.getCause();
            depth++;
        }
        return FailureCategory.UNKNOWN;
    }

    /** 总时限耗尽：与"事件静默超时"区分开，便于定位是整体太慢还是单点卡死。 */
    public static final class ExecutionDeadlineExceededException extends IllegalStateException {
        public ExecutionDeadlineExceededException(String message) {
            super(message);
        }
    }
}
