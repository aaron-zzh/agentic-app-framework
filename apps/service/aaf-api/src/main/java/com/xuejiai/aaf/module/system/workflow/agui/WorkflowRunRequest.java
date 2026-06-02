package com.xuejiai.aaf.module.system.workflow.agui;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工作流 AG-UI 运行请求。
 *
 * <p>两种模式：
 *
 * <ul>
 *   <li>正式运行（debug=false）：传 flowId，流程必须为 PUBLISHED 状态
 *   <li>调试运行（debug=true）：传 bpmnXml，临时部署执行，执行后自动清理，仅创建者可用
 * </ul>
 *
 * @param flowId 正式运行时传，ai_flow_definition.id
 * @param bpmnXml 调试运行时传，前端转换好的 BPMN XML
 * @param debug 是否调试模式
 * @param variables 流程变量
 * @param messages 对话消息（可选，用于对话式流程）
 */
@Schema(description = "工作流 AG-UI 运行请求")
public record WorkflowRunRequest(
        @Schema(description = "流程定义 ID（正式运行）") Long flowId,
        @Schema(description = "BPMN XML（调试运行）") String bpmnXml,
        @Schema(description = "是否调试模式", defaultValue = "false") boolean debug,
        @Schema(description = "流程变量") Map<String, Object> variables,
        @Schema(description = "对话消息列表（可选）") List<Message> messages) {

    /** 对话消息 */
    public record Message(
            @Schema(description = "角色", example = "user") String role,
            @Schema(description = "内容") String content) {}
}
