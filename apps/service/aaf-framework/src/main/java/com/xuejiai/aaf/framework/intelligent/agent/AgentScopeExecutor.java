package com.xuejiai.aaf.framework.intelligent.agent;

import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;

import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope ReActAgent 的 AgentExecutor 适配器。
 * 上层只依赖 AgentExecutor 接口，不直接引用 ReActAgent。
 */
@Slf4j
public class AgentScopeExecutor implements AgentExecutor {

    private final ReActAgent delegate;

    public AgentScopeExecutor(ReActAgent delegate) {
        this.delegate = delegate;
    }

    @Override
    public AgentResult execute(String input) {
        try {
            var msg = Msg.builder().name("user").textContent(input).build();
            var response = delegate.call(msg).block();
            if (response == null) return AgentResult.error("Agent 返回空响应");
            // getTextContent() 提取纯文本，避免 getContent() 返回 List<ContentBlock>
            var text = response.getTextContent();
            return AgentResult.success(text != null ? text : "");
        } catch (Exception e) {
            log.warn("Agent [{}] 执行失败: {}", delegate.getName(), e.getMessage());
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
        // AgentScope ReActAgent 暂无公开 clearHistory，reset 由 AgentPool 通过重建实例实现
        log.debug("Agent [{}] reset", delegate.getName());
    }
}
