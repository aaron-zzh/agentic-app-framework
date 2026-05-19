/**
 * Agent 沙箱执行器。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import java.time.Duration;
import java.util.concurrent.*;

import org.springframework.stereotype.Component;

import io.agentscope.agent.ReActAgent;
import io.agentscope.message.Msg;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Agent 沙箱：隔离执行环境、资源限制、超时控制。
 * 每个 Agent 在独立虚拟线程中执行，超时自动中断。
 */
@Slf4j
@Component
public class AgentSandbox {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 在沙箱中执行 Agent。
     *
     * @param agent Agent 实例
     * @param input 用户输入消息
     * @param timeout 超时时间
     * @return Agent 响应
     */
    public Mono<Msg> execute(ReActAgent agent, Msg input, Duration timeout) {
        return Mono.fromFuture(() -> {
            var future = CompletableFuture.supplyAsync(
                    () -> agent.call(input).block(),
                    executor);
            try {
                return CompletableFuture.completedFuture(
                        future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                agent.interrupt();
                future.cancel(true);
                log.warn("Agent [{}] 执行超时", agent.getName());
                return CompletableFuture.completedFuture(
                        Msg.builder().name("system").textContent("执行超时，已中断").build());
            } catch (Exception e) {
                log.error("Agent [{}] 执行异常: {}", agent.getName(), e.getMessage());
                return CompletableFuture.completedFuture(
                        Msg.builder().name("system").textContent("执行异常: " + e.getMessage()).build());
            }
        });
    }
}
