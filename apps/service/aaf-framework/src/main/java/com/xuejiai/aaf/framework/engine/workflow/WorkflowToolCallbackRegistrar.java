package com.xuejiai.aaf.framework.engine.workflow;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
                        "查询系统中所有可用的工作流列表。在启动工作流前，先调用此工具了解有哪些流程可以启动。",
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
                        "启动一个预定义的工作流流程。适用于需要多步骤审批、自动化处理或复杂业务流程的场景。",
                        """
                {"type":"object","properties":{
                  "process_key":{"type":"string","description":"工作流定义 Key"},
                  "description":{"type":"string","description":"向用户展示的工作流描述"},
                  "variables":{"type":"string","description":"流程变量 JSON，可为空"}
                },"required":["process_key","description"]}""") {
                    @Override
                    public String call(String arguments) {
                        try {
                            var om = new com.fasterxml.jackson.databind.ObjectMapper();
                            var map =
                                    om.readValue(
                                            arguments,
                                            new com.fasterxml.jackson.core.type.TypeReference<
                                                    java.util.Map<String, String>>() {});
                            return workflowTool.startWorkflow(
                                    map.get("process_key"),
                                    map.get("description"),
                                    map.get("variables"));
                        } catch (Exception e) {
                            return "{\"error\":\"" + e.getMessage() + "\"}";
                        }
                    }
                },
                ToolRegistry.SOURCE_LOCAL);

        log.info("WorkflowTool 已注册进 ToolRegistry（list_workflows, start_workflow）");
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
