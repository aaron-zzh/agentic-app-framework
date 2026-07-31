package com.xuejiai.aaf.module.ai.flow.agui;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 工作流 AG-UI 运行请求。
 *
 * <p>两种模式：
 *
 * <ul>
 *   <li>正式运行（debug=false）：传 flowId，流程必须为 PUBLISHED 状态
 *   <li>调试运行（debug=true）：传 flowId，由服务端编译草稿并临时部署，仅创建者可用
 * </ul>
 *
 * @param flowId 工作流定义 ID
 * @param debug 是否调试模式
 * @param variables 流程变量
 * @param messages 对话消息（可选，用于对话式流程）
 */
@Schema(description = "工作流 AG-UI 运行请求")
public record WorkflowRunRequest(
        @NotNull @Schema(description = "工作流定义 ID") Long flowId,
        @Schema(description = "是否调试模式", defaultValue = "false") boolean debug,
        @Schema(description = "流程变量") Map<String, Object> variables,
        @Schema(description = "对话消息列表（可选）") List<Message> messages) {

    /** 对话消息 */
    public record Message(
            @Schema(description = "角色", example = "user") String role,
            @Schema(description = "内容") String content) {}
}
