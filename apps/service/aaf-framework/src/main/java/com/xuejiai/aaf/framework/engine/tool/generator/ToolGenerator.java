package com.xuejiai.aaf.framework.engine.tool.generator;

import java.util.List;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ScriptExecutor;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具生成器——作为 Agent 可调用的 @Tool 注册。
 *
 * <p>由内置技能 "tool_generation" 关联的 Agent 调用。 生成后默认 PRIVATE（仅创建者可见），管理员/用户可标记为 SHARED。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolGenerator {

    private final ResilientChatService chatService;
    private final ToolRegistry toolRegistry;
    private final ScriptExecutor scriptExecutor;
    private final HumanApprovalService approvalService;
    private final GeneratedToolStore toolStore;

    private static final String SYSTEM_PROMPT =
            """
            你是一个工具生成器。根据用户描述生成可执行的 JavaScript 函数。

            规则：
            1. 输入通过全局变量 args（JSON 对象）获取
            2. 输出赋值给全局变量 __result（JSON 对象或字符串）
            3. 不能使用 require/import，不能访问文件系统和网络
            4. 只用纯 JavaScript 逻辑（计算、字符串处理、数据转换）

            输出格式（严格 JSON）：
            {"name":"工具名","description":"描述","parameters":{"参数名":"参数说明"},"code":"JavaScript代码"}
            """;

    /** Agent 可调用的工具生成方法。 */
    public String generateTool(String description) {
        var response =
                chatService.call(
                        List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(description)),
                        "tool-gen",
                        null);
        var content = response.getResult().getOutput().getText();
        var blueprint = ToolBlueprint.parse(content);
        if (blueprint == null) {
            return "工具生成失败，无法解析 LLM 输出";
        }
        // 返回蓝图供 Agent 展示给用户确认
        return "工具生成成功：\n- 名称: %s\n- 描述: %s\n- 参数: %s\n\n源码:\n```javascript\n%s\n```\n\n请用户确认后调用 confirm_tool 注册。"
                .formatted(
                        blueprint.getName(),
                        blueprint.getDescription(),
                        blueprint.getParameters(),
                        blueprint.getCode());
    }

    /** 确认并注册生成的工具（Agent 调用）。 */
    public String confirmTool(String toolJson) {
        var blueprint = ToolBlueprint.parse(toolJson);
        if (blueprint == null) return "工具定义解析失败";
        blueprint.setVisibility(ToolBlueprint.Visibility.PRIVATE);
        var callback = new GeneratedToolCallback(blueprint, scriptExecutor);
        toolRegistry.register(callback, ToolRegistry.SOURCE_CUSTOM);
        log.info("AI 生成工具已注册（PRIVATE）: {}", blueprint.getName());
        return "工具 [%s] 已注册成功（私有，仅你可见）。如需共享请联系管理员。".formatted(blueprint.getName());
    }

    /** 标记工具为共享（创建者或管理员可操作）。 共享后：所有人可见源码、可使用、可复用到自己的 Role。 */
    public void share(String toolName) {
        toolStore.updateVisibility(toolName, ToolBlueprint.Visibility.SHARED);
        log.info("工具 [{}] 已标记为共享（所有人可见源码和使用）", toolName);
    }

    /** 查看工具源码。 */
    public String viewSource(String toolName) {
        return toolStore.findByName(toolName).map(GeneratedTool::getCode).orElse("工具不存在或无源码");
    }

    /** 外部调用入口：生成并注册（含持久化）。 */
    public void confirmAndRegister(ToolBlueprint blueprint) {
        blueprint.setVisibility(ToolBlueprint.Visibility.PRIVATE);
        // 持久化
        var entity = new GeneratedTool();
        entity.setName(blueprint.getName());
        entity.setDescription(blueprint.getDescription());
        entity.setParametersJson(
                blueprint.getParameters() != null ? blueprint.getParameters().toString() : null);
        entity.setCode(blueprint.getCode());
        entity.setCreatorUserId(
                blueprint.getCreatorUserId() != null ? blueprint.getCreatorUserId() : 0L);
        entity.setVisibility(blueprint.getVisibility());
        toolStore.save(entity);
        // 注册到内存
        var callback = new GeneratedToolCallback(blueprint, scriptExecutor);
        toolRegistry.register(callback, ToolRegistry.SOURCE_CUSTOM);
        log.info("AI 生成工具已注册并持久化（PRIVATE）: {}", blueprint.getName());
    }
}
