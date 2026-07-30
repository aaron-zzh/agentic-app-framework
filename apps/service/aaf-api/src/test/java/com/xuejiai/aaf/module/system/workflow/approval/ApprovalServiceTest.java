package com.xuejiai.aaf.module.system.workflow.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.module.system.org.service.OrganizationService;
import com.xuejiai.aaf.module.system.role.service.RoleService;
import com.xuejiai.aaf.module.system.workflow.approval.ApprovalNodeConfig.AssigneeStrategy;
import com.xuejiai.aaf.module.system.workflow.approval.ApprovalNodeConfig.EmptyAssigneeStrategy;
import com.xuejiai.aaf.module.system.workflow.approval.ApprovalNodeConfig.TimeoutStrategy;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock private RoleService roleService;
    @Mock private OrganizationService organizationService;
    @Mock private WorkflowEngine workflowEngine;
    @InjectMocks private ApprovalService approvalService;

    @Test
    void shouldResolveActiveUsersByRoleCode() {
        when(roleService.listActiveUserIdsByCode("AUDITOR")).thenReturn(List.of(11L, 12L));
        var config = config(AssigneeStrategy.ROLE, TimeoutStrategy.REMIND, "AUDITOR", null);

        var assignees = approvalService.resolveAssignees(config, Map.of());

        assertThat(assignees).containsExactly("11", "12");
    }

    @Test
    void shouldResolveOnlySafeVariablePathExpression() {
        var config =
                config(
                        AssigneeStrategy.EXPRESSION,
                        TimeoutStrategy.REMIND,
                        null,
                        "${form.managerId}");

        var assignees =
                approvalService.resolveAssignees(
                        config, Map.of("form", Map.of("managerId", 99L)));

        assertThat(assignees).containsExactly("99");
    }

    @Test
    void shouldRejectExecutableExpressionSyntax() {
        var config =
                config(
                        AssigneeStrategy.EXPRESSION,
                        TimeoutStrategy.REMIND,
                        null,
                        "T(java.lang.Runtime).getRuntime()");

        assertThatThrownBy(() -> approvalService.resolveAssignees(config, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全的变量路径");
    }

    @Test
    void shouldExecuteAutoApproveTimeoutStrategy() {
        var config = config(AssigneeStrategy.FIXED_USER, TimeoutStrategy.AUTO_APPROVE, null, null);

        approvalService.handleTimeout(config, "task-1");

        verify(workflowEngine)
                .completeTask("task-1", Map.of("approved", true), "审批超时自动通过");
    }

    private ApprovalNodeConfig config(
            AssigneeStrategy assigneeStrategy,
            TimeoutStrategy timeoutStrategy,
            String roleKey,
            String expression) {
        return new ApprovalNodeConfig(
                assigneeStrategy,
                List.of("1"),
                roleKey,
                expression,
                null,
                List.of(),
                timeoutStrategy,
                24,
                EmptyAssigneeStrategy.ERROR);
    }
}
