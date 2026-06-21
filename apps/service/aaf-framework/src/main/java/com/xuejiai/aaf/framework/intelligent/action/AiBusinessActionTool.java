package com.xuejiai.aaf.framework.intelligent.action;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.RequiredArgsConstructor;

/** 暴露给 Agent 的受控业务动作工具。 */
@Component
@RequiredArgsConstructor
public class AiBusinessActionTool {

    private final AiBusinessActionExecutor executor;
    private final EntityActionRegistry registry;

    @Tool(description = "代表当前用户执行已开放实体的受控业务动作。")
    public String executeBusinessAction(
            @ToolParam(
                            description =
                                    "JSON 请求，包含 action、entity、params，可选 sessionId/confidence/verifiable")
                    String requestJson) {
        try {
            var request = JsonUtils.parseObject(requestJson, AiBusinessActionRequest.class);
            return JsonUtils.toJsonString(executor.execute(request));
        } catch (Exception ex) {
            return errorJson(ex);
        }
    }

    @Tool(description = "查询当前 AI 可调用的业务实体与动作清单。")
    public String listBusinessActions() {
        try {
            return JsonUtils.toJsonString(AiBusinessActionResult.success(registry.list()));
        } catch (Exception ex) {
            return errorJson(ex);
        }
    }

    private String errorJson(Exception ex) {
        try {
            return JsonUtils.toJsonString(
                    AiBusinessActionResult.failure("ACTION_ERROR", ex.getMessage()));
        } catch (Exception ignored) {
            return "{\"success\":false,\"code\":\"ACTION_ERROR\",\"message\":\"Action failed\"}";
        }
    }
}
