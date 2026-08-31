package com.xuejiai.aaf.module.ai.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;
import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine.InstanceInfo;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentCallableWorkflowPort.TrustedScope;
import com.xuejiai.aaf.module.ai.flow.domain.AiFlowDefinition;
import com.xuejiai.aaf.module.ai.flow.repository.AiFlowDefinitionRepository;

/**
 * Agent 可调用 AI Flow 的同步等待语义：启动即等待完成，返回结束节点 {@code output} 变量文本； 超时或以 {@code terminated} 状态结束时视为失败。
 */
@ExtendWith(MockitoExtension.class)
class AgentCallableWorkflowServiceTest {

    @Mock private AiFlowDefinitionRepository repository;
    @Mock private AiFlowBpmnCompiler bpmnCompiler;
    @Mock private BpmnEngine bpmnEngine;

    private AgentCallableWorkflowService service;

    private final TrustedScope scope = new TrustedScope(2L, 1L, 3L);

    @Test
    @DisplayName("Given 工作流快速完成 When start Then 返回结束节点输出文本")
    void should_return_output_text_when_workflow_completes() {
        service = new AgentCallableWorkflowService(repository, bpmnCompiler, bpmnEngine);
        var flow = flow(10L, "摘要流程");
        when(repository.findOne(ArgumentMatchers.<Specification<AiFlowDefinition>>any()))
                .thenReturn(Optional.of(flow));
        when(bpmnCompiler.processKey(10L)).thenReturn("ai_flow_10");
        when(bpmnEngine.startProcess(eq("ai_flow_10"), any(), any())).thenReturn("proc-1");
        when(bpmnEngine.isProcessRunning("proc-1")).thenReturn(false);
        when(bpmnEngine.getInstance("proc-1"))
                .thenReturn(new InstanceInfo("proc-1", "ai_flow_10", "biz-1", "completed", 1L, 2L));
        when(bpmnEngine.getProcessVariables("proc-1")).thenReturn(Map.of("output", "生成完成"));

        var result = service.start(10L, Map.of(), scope, "run-1");

        assertThat(result.outputText()).isEqualTo("生成完成");
        assertThat(result.processInstanceId()).isEqualTo("proc-1");
    }

    @Test
    @DisplayName("Given 工作流完成但未配置 output 变量 When start Then 返回空字符串代表成功无产出")
    void should_return_empty_text_when_output_variable_missing() {
        service = new AgentCallableWorkflowService(repository, bpmnCompiler, bpmnEngine);
        var flow = flow(11L, "无输出流程");
        when(repository.findOne(ArgumentMatchers.<Specification<AiFlowDefinition>>any()))
                .thenReturn(Optional.of(flow));
        when(bpmnCompiler.processKey(11L)).thenReturn("ai_flow_11");
        when(bpmnEngine.startProcess(eq("ai_flow_11"), any(), any())).thenReturn("proc-2");
        when(bpmnEngine.isProcessRunning("proc-2")).thenReturn(false);
        when(bpmnEngine.getInstance("proc-2"))
                .thenReturn(new InstanceInfo("proc-2", "ai_flow_11", "biz-2", "completed", 1L, 2L));
        when(bpmnEngine.getProcessVariables("proc-2")).thenReturn(Map.of());

        var result = service.start(11L, Map.of(), scope, "run-2");

        assertThat(result.outputText()).isEmpty();
    }

    @Test
    @DisplayName("Given 工作流以 terminated 状态结束 When start Then 抛出状态异常")
    void should_throw_when_workflow_terminated() {
        service = new AgentCallableWorkflowService(repository, bpmnCompiler, bpmnEngine);
        var flow = flow(12L, "被终止流程");
        when(repository.findOne(ArgumentMatchers.<Specification<AiFlowDefinition>>any()))
                .thenReturn(Optional.of(flow));
        when(bpmnCompiler.processKey(12L)).thenReturn("ai_flow_12");
        when(bpmnEngine.startProcess(eq("ai_flow_12"), any(), any())).thenReturn("proc-3");
        when(bpmnEngine.isProcessRunning("proc-3")).thenReturn(false);
        when(bpmnEngine.getInstance("proc-3"))
                .thenReturn(
                        new InstanceInfo("proc-3", "ai_flow_12", "biz-3", "terminated", 1L, 2L));

        assertThatThrownBy(() -> service.start(12L, Map.of(), scope, "run-3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未成功完成");
    }

    @Test
    @DisplayName("Given workflowId 非正整数 When start Then 拒绝请求")
    void should_reject_invalid_workflow_id() {
        service = new AgentCallableWorkflowService(repository, bpmnCompiler, bpmnEngine);

        assertThatThrownBy(() -> service.start(0L, Map.of(), scope, "run-4"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Given agentRunId 为空 When start Then 拒绝请求")
    void should_reject_blank_agent_run_id() {
        service = new AgentCallableWorkflowService(repository, bpmnCompiler, bpmnEngine);

        assertThatThrownBy(() -> service.start(10L, Map.of(), scope, " "))
                .isInstanceOf(BusinessException.class);
    }

    private static AiFlowDefinition flow(Long id, String name) {
        var flow = new AiFlowDefinition();
        flow.setId(id);
        flow.setName(name);
        return flow;
    }
}
