package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope AG-UI → AAF 流式输出适配器。
 *
 * <p>适配策略：委托给 AgentScope 官方 AG-UI 扩展
 * ({@code agentscope-extensions-agui} + {@code agentscope-agui-spring-boot-starter})，
 * 替换 AAF 自研的 {@code AgUiStreamHandler} 和 {@code AgUiEvent}。
 *
 * <p>AgentScope AG-UI 扩展提供：
 * <ul>
 *   <li>标准 AG-UI 事件流（RUN_STARTED / TEXT_MESSAGE / TOOL_CALL / RUN_FINISHED）</li>
 *   <li>Spring Boot Starter 自动配置 SSE 端点</li>
 *   <li>与 {@code @assistant-ui/react} 前端框架原生兼容</li>
 * </ul>
 *
 * <p>TODO: 引入 agentscope-agui-spring-boot-starter 后，
 * 删除 AAF 自研的 AgUiStreamHandler / AgUiEvent，直接使用 AgentScope 官方实现。
 * 当前骨架保留接口形状，待依赖引入后补全委托逻辑。
 */
@Slf4j
@RequiredArgsConstructor
public class AgentScopeAguiAdapter {

    /**
     * 以 AG-UI 协议流式执行 Agent，将事件写入 SSE。
     *
     * @param agent     AgentScope ReActAgent 实例
     * @param userInput 用户输入
     * @param emitter   SSE 发射器
     * @param runId     本次运行 ID
     */
    public void streamToSse(ReActAgent agent, String userInput, SseEmitter emitter, String runId) {
        // TODO: 引入 agentscope-agui-spring-boot-starter 后，
        // 使用 AgentScope 官方 AguiEventEmitter 替换此处逻辑
        var msg = Msg.builder().name("user").textContent(userInput).build();
        agent.call(msg)
            .doOnError(e -> {
                log.error("AG-UI 流式执行失败 runId={}", runId, e);
                emitter.completeWithError(e);
            })
            .doOnComplete(emitter::complete)
            .subscribe();
    }
}
