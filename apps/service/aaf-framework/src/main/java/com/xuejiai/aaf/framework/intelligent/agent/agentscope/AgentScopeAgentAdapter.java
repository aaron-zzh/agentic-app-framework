package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;

import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope ReActAgent → AAF AgentExecutor 适配器。
 *
 * <p>适配策略：AAF 上层只依赖 {@link AgentExecutor} 接口，
 * 本类将调用委托给 AgentScope {@link ReActAgent}，屏蔽 AgentScope API 细节。
 *
 * <p>已有实现，此处保留并迁移到 agentscope/ 子包统一管理。
 */
@Slf4j
public class AgentScopeAgentAdapter implements AgentExecutor {

    private final ReActAgent delegate;

    public AgentScopeAgentAdapter(ReActAgent delegate) {
        this.delegate = delegate;
    }

    @Override
    public AgentResult execute(String input) {
        try {
            var msg = Msg.builder().name("user").textContent(input).build();
            var response = delegate.call(msg).block();
            if (response == null) return AgentResult.error("Agent 返回空响应");
            var text = response.getTextContent();
            return AgentResult.success(text != null ? text : "");
        } catch (Exception e) {
            log.warn("AgentScope Agent [{}] 执行失败: {}", delegate.getName(), e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }

    @Override
    public void interrupt() {
        delegate.interrupt();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void reset() {
        // AgentScope ReActAgent 无公开 clearHistory，reset 由 AgentPool 重建实例实现
        log.debug("AgentScope Agent [{}] reset", delegate.getName());
    }
}
