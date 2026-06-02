package com.xuejiai.aaf.module.ai.agui;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.AgentRegistryService;
import com.xuejiai.aaf.framework.intelligent.agentscope.runtime.AgentScopeRuntime;

import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.spring.boot.agui.common.AguiAgentRegistryCustomizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 将 AAF 的 Assistant/Agent 注册到 AgentScope AG-UI Registry。
 *
 * <p>AgentScope AG-UI starter 自动暴露 /agui/runs 端点，
 * 前端通过 X-Agent-Id header 路由到不同 Agent。
 *
 * <p>每个注册的 Agent 支持：
 * <ul>
 *   <li>AG-UI 标准事件流（TEXT_MESSAGE / TOOL_CALL / STATE_DELTA）
 *   <li>通过 emitStateEvents=true 自动推送 Agent 内部状态
 *   <li>前端 assistant-ui 原生兼容
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AafAguiRegistryCustomizer implements AguiAgentRegistryCustomizer {

    private final AgentRegistryService agentRegistryService;
    private final AgentScopeRuntime agentScopeRuntime;

    @Override
    public void customize(AguiAgentRegistry registry) {
        // 注册所有已定义的 Agent（使用 factory 模式，每次请求创建新实例）
        var agents = agentRegistryService.listActive();
        for (var def : agents) {
            registry.registerFactory(def.getAgentId(),
                    () -> agentScopeRuntime.createRaw(def));
            log.info("[AG-UI] 注册 Agent: {} ({})", def.getName(), def.getAgentId());
        }

        log.info("[AG-UI] Agent 注册完成，共 {} 个", registry.size());
    }
}
