package com.xuejiai.aaf.framework.engine.tool.generator;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ScriptExecutor;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 启动时从 DB 加载已注册的生成工具到 ToolRegistry。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeneratedToolLoader implements ApplicationRunner {

    private final GeneratedToolStore toolStore;
    private final ToolRegistry toolRegistry;
    private final ScriptExecutor scriptExecutor;

    @Override
    public void run(ApplicationArguments args) {
        var tools = toolStore.findShared();
        tools.addAll(toolStore.findByCreator(0L)); // 系统级工具
        for (var tool : tools) {
            var blueprint = new ToolBlueprint();
            blueprint.setName(tool.getName());
            blueprint.setDescription(tool.getDescription());
            blueprint.setCode(tool.getCode());
            blueprint.setVisibility(tool.getVisibility());
            var callback = new GeneratedToolCallback(blueprint, scriptExecutor);
            toolRegistry.register(callback, ToolRegistry.SOURCE_CUSTOM);
        }
        if (!tools.isEmpty()) {
            log.info("从 DB 加载 {} 个生成工具到 ToolRegistry", tools.size());
        }
    }
}
