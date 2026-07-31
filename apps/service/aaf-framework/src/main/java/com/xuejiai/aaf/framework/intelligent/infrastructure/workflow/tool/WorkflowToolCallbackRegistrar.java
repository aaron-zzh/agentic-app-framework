package com.xuejiai.aaf.framework.intelligent.infrastructure.workflow.tool;

import java.math.BigDecimal;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * 工作流工具注册器——将 {@link WorkflowTool}（AgentScope @Tool）适配为 Spring AI {@link ToolCallback} 并注册进 {@link
 * ToolRegistry}，使工作流工具在两条调用链路（Spring AI / AgentScope）均可使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowToolCallbackRegistrar {

    private final WorkflowTool workflowTool;
    private final ToolRegistry toolRegistry;

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        toolRegistry.register(
                new WorkflowToolCallback(
                        "list_workflows",
                        "查询当前组织和工作区内允许智能体调用的已发布 AI Flow。",
                        "{\"type\":\"object\",\"properties\":{}}") {
                    @Override
                    public String call(String arguments) {
                        return workflowTool.listWorkflows();
                    }
                },
                ToolRegistry.SOURCE_LOCAL);

        toolRegistry.register(
                new WorkflowToolCallback(
                        "start_workflow",
                        "按 workflow_id 启动当前租户允许智能体调用的已发布 AI Flow。",
                        """
                {"type":"object","properties":{
                  "workflow_id":{"type":"integer","description":"AI Flow 业务 ID"},
                  "variables":{"type":"string","description":"流程变量 JSON，可为空；变量名不能以 _aaf 开头"}
                },"required":["workflow_id"]}""") {
                    @Override
                    public String call(String arguments) {
                        var map =
                                JsonUtils.parseObject(
                                        arguments,
                                        new TypeReference<java.util.Map<String, Object>>() {});
                        if (map == null) {
                            throw new IllegalArgumentException("start_workflow 参数必须是 JSON 对象");
                        }
                        var workflowId = requiredWorkflowId(map.get("workflow_id"));
                        var variables = map.get("variables");
                        if (variables != null && !(variables instanceof String)) {
                            throw new IllegalArgumentException("variables 必须是 JSON 字符串");
                        }
                        return workflowTool.startWorkflow(workflowId, (String) variables);
                    }
                },
                ToolRegistry.SOURCE_LOCAL);

        log.info("WorkflowTool 已注册进 ToolRegistry（list_workflows, start_workflow）");
    }

    private Long requiredWorkflowId(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("缺少 workflow_id");
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("workflow_id 必须为正整数");
        }
        try {
            var workflowId = new BigDecimal(number.toString()).longValueExact();
            if (workflowId <= 0) {
                throw new IllegalArgumentException("workflow_id 必须为正整数");
            }
            return workflowId;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("workflow_id 必须为正整数", exception);
        }
    }

    /** 最小 ToolCallback 抽象基类，避免重复 definition 代码。 */
    private abstract static class WorkflowToolCallback implements ToolCallback {
        private final ToolDefinition definition;

        WorkflowToolCallback(String name, String description, String schema) {
            this.definition =
                    DefaultToolDefinition.builder()
                            .name(name)
                            .description(description)
                            .inputSchema(schema)
                            .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }
    }
}
