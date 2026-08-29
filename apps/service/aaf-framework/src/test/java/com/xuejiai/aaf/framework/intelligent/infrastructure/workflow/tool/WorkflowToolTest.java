package com.xuejiai.aaf.framework.intelligent.infrastructure.workflow.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentCallableWorkflowPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentCallableWorkflowPort.WorkflowStartResult;
import com.xuejiai.aaf.framework.org.OrgContext;

/** 工作流工具：同步等待完成后按是否有输出文本分支拼接返回文案。 */
@ExtendWith(MockitoExtension.class)
class WorkflowToolTest {

    @Mock private AgentCallableWorkflowPort workflowPort;

    private WorkflowTool tool;

    @AfterEach
    void tearDown() {
        AgentRunContextHolder.clear();
        OrgContext.setCurrentOrgId(null);
        OrgContext.setCurrentWorkspaceId(null);
    }

    @Test
    @DisplayName("Given 工作流有输出文本 When startWorkflow Then 返回包含输出的文案")
    void should_return_output_text_when_present() {
        tool = new WorkflowTool(workflowPort);
        OrgContext.setCurrentOrgId(1L);
        OrgContext.setCurrentWorkspaceId(3L);
        when(workflowPort.start(any(), any(), any(), any()))
                .thenReturn(new WorkflowStartResult(10L, "摘要流程", "proc-1", "biz-1", "生成完成"));

        try (var ignored = AgentRunContextHolder.open("run-1", 2L, "agent-1")) {
            var result = tool.startWorkflow(10L, null);
            assertThat(result).contains("摘要流程").contains("生成完成");
        }
    }

    @Test
    @DisplayName("Given 工作流无输出文本 When startWorkflow Then 返回仅成功文案")
    void should_return_success_only_when_output_blank() {
        tool = new WorkflowTool(workflowPort);
        OrgContext.setCurrentOrgId(1L);
        OrgContext.setCurrentWorkspaceId(3L);
        when(workflowPort.start(any(), any(), any(), any()))
                .thenReturn(new WorkflowStartResult(11L, "无输出流程", "proc-2", "biz-2", ""));

        try (var ignored = AgentRunContextHolder.open("run-2", 2L, "agent-1")) {
            var result = tool.startWorkflow(11L, null);
            assertThat(result).contains("无输出流程").contains("已成功执行完成").doesNotContain("输出：");
        }
    }
}
