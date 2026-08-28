package com.xuejiai.aaf.module.ai.flow.agui;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 工作流 AG-UI 运行请求。
 *
 * <p>本端点是编排调试通道，只接受 {@code debug=true}：服务端编译草稿并临时部署，仅工作流创建者可用。
 *
 * <p>{@code debug} 字段保留为显式声明——传 false 会被直接拒绝，用于给"想走此端点做生产运行"的调用方一个响亮失败，
 * 而不是静默降级成调试运行。已发布工作流的生产运行属于统一运行时的 {@code processMode=PREDEFINED_WORKFLOW} 分支（尚未实现），不在本端点兜底。
 *
 * @param flowId 工作流定义 ID
 * @param debug 必须为 true；false 直接拒绝
 * @param variables 流程变量
 * @param messages 对话消息（可选，用于对话式流程）
 */
@Schema(description = "工作流 AG-UI 调试运行请求")
public record WorkflowRunRequest(
        @NotNull @Schema(description = "工作流定义 ID") Long flowId,
        @Schema(description = "必须为 true，本端点仅支持调试运行", defaultValue = "true") boolean debug,
        @Schema(description = "流程变量") Map<String, Object> variables,
        @Schema(description = "对话消息列表（可选）") List<Message> messages) {

    /** 对话消息 */
    public record Message(
            @Schema(description = "角色", example = "user") String role,
            @Schema(description = "内容") String content) {}
}
