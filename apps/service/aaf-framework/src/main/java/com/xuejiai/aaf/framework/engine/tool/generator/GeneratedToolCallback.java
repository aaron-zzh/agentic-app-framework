package com.xuejiai.aaf.framework.engine.tool.generator;

import java.time.Duration;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.xuejiai.aaf.framework.engine.tool.ScriptExecutor;

/** AI 生成工具的 ToolCallback 实现——执行 LLM 生成的 JavaScript 代码。 */
public class GeneratedToolCallback implements ToolCallback {

    private final ToolBlueprint blueprint;
    private final ScriptExecutor scriptExecutor;
    private final ToolDefinition toolDefinition;

    public GeneratedToolCallback(ToolBlueprint blueprint, ScriptExecutor scriptExecutor) {
        this.blueprint = blueprint;
        this.scriptExecutor = scriptExecutor;
        this.toolDefinition =
                DefaultToolDefinition.builder()
                        .name(blueprint.getName())
                        .description(blueprint.getDescription())
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String arguments) {
        var result =
                scriptExecutor.executeJs(blueprint.getCode(), arguments, Duration.ofSeconds(10));
        if (result.success()) {
            return result.stdout().isBlank() ? "执行成功" : result.stdout();
        }
        return "执行失败: " + result.stderr();
    }
}
