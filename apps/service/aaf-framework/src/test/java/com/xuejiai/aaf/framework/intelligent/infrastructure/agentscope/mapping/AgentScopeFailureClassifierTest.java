package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort.StaleExecutionException;
import com.xuejiai.aaf.framework.intelligent.cognition.model.ContextBudgetExceededException;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeFailureClassifier.ExecutionDeadlineExceededException;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeFailureClassifier.FailureCategory;

/** RQ-04：失败分类是对外稳定契约，重试决策不得依赖异常类简单名。 */
class AgentScopeFailureClassifierTest {

    @Test
    @DisplayName("Given 时限类异常 When 分类 Then TIMEOUT 且可重试")
    void should_classify_timeouts_as_retryable() {
        assertThat(AgentScopeFailureClassifier.classify(new TimeoutException()))
                .isEqualTo(FailureCategory.TIMEOUT);
        assertThat(
                        AgentScopeFailureClassifier.classify(
                                new ExecutionDeadlineExceededException("总时限耗尽")))
                .isEqualTo(FailureCategory.TIMEOUT);
        assertThat(FailureCategory.TIMEOUT.retryable()).isTrue();
    }

    @Test
    @DisplayName("Given 租约换代或额度耗尽 When 分类 Then 不可自动重试")
    void should_classify_non_retryable_categories() {
        assertThat(AgentScopeFailureClassifier.classify(new StaleExecutionException("执行已过期")))
                .isEqualTo(FailureCategory.FENCED);
        assertThat(AgentScopeFailureClassifier.classify(new ContextBudgetExceededException("超额")))
                .isEqualTo(FailureCategory.BUDGET_EXCEEDED);
        assertThat(FailureCategory.FENCED.retryable()).isFalse();
        assertThat(FailureCategory.BUDGET_EXCEEDED.retryable()).isFalse();
    }

    @Test
    @DisplayName("Given 异常被包装 When 分类 Then 穿透 cause 链识别类别")
    void should_classify_through_cause_chain() {
        var wrapped =
                new RuntimeException("外层", new IllegalStateException("中层", new TimeoutException()));

        assertThat(AgentScopeFailureClassifier.classify(wrapped))
                .isEqualTo(FailureCategory.TIMEOUT);
    }

    @Test
    @DisplayName("Given 无法归类异常 When 分类 Then UNKNOWN 且保守判为不可重试")
    void should_default_to_unknown_and_not_retryable() {
        assertThat(AgentScopeFailureClassifier.classify(new RuntimeException("未知")))
                .isEqualTo(FailureCategory.UNKNOWN);
        assertThat(FailureCategory.UNKNOWN.retryable()).isFalse();
    }
}
