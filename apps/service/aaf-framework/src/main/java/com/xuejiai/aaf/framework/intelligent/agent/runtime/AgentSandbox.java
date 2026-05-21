package com.xuejiai.aaf.framework.intelligent.agent.runtime;

import java.time.Duration;
import java.util.concurrent.*;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;

import lombok.extern.slf4j.Slf4j;

/** Agent 沙箱：虚拟线程隔离执行 + 超时控制。 依赖 AgentExecutor 接口，不直接引用 AgentScope ReActAgent。 */
@Slf4j
@Component
public class AgentSandbox {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 在沙箱中执行 Agent。
     *
     * @param agent AgentExecutor 实例
     * @param input 用户输入
     * @param timeout 超时时间
     * @return Agent 执行结果
     */
    public AgentExecutor.AgentResult execute(AgentExecutor agent, String input, Duration timeout) {
        var future = CompletableFuture.supplyAsync(() -> agent.execute(input), executor);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            agent.interrupt();
            future.cancel(true);
            log.warn("Agent [{}] 执行超时 ({}s)", agent.getName(), timeout.toSeconds());
            return AgentExecutor.AgentResult.error("执行超时，已中断");
        } catch (Exception e) {
            log.error("Agent [{}] 执行异常: {}", agent.getName(), e.getMessage());
            return AgentExecutor.AgentResult.error("执行异常: " + e.getMessage());
        }
    }
}
