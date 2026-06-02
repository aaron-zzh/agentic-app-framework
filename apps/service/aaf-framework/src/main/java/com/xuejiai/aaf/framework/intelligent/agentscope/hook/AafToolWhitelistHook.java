package com.xuejiai.aaf.framework.intelligent.agentscope.hook;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 工具白名单 Hook——PreActingEvent 时校验工具是否在允许列表中，
 * 不在白名单的工具调用将被拦截并返回拒绝结果。
 */
@Slf4j
public class AafToolWhitelistHook implements Hook {

    private final Set<String> allowedTools;
    private final ToolCatalogProvider catalogProvider;

    public AafToolWhitelistHook(List<String> allowedTools) {
        this(allowedTools, null);
    }

    public AafToolWhitelistHook(List<String> allowedTools, ToolCatalogProvider catalogProvider) {
        this.allowedTools = allowedTools != null ? Set.copyOf(allowedTools) : Set.of();
        this.catalogProvider = catalogProvider;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreActingEvent preActing) {
            var toolUse = preActing.getToolUse();
            if (toolUse != null && !isAllowed(toolUse.getName())) {
                log.warn("工具白名单拦截：{} 不在允许列表中", toolUse.getName());
                // 替换为空操作工具调用（返回拒绝提示）
                var blocked = new ToolUseBlock(
                        toolUse.getId(), "__blocked__", toolUse.getInput());
                preActing.setToolUse(blocked);
            }
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 20; // 安全类 Hook，高优先级
    }

    private boolean isAllowed(String toolName) {
        // 空白名单表示不限制
        var whitelistAllowed = allowedTools.isEmpty() || allowedTools.contains(toolName);
        if (!whitelistAllowed) {
            return false;
        }
        return catalogProvider == null
                || catalogProvider.find(toolName).map(entry -> entry.enabled()).orElse(false);
    }
}
