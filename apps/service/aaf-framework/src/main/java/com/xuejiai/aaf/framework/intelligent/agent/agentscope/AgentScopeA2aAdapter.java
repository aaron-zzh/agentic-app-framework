package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope A2A → AAF 跨系统 Agent 协作适配器。
 *
 * <p>适配策略：委托给 AgentScope 官方 A2A 扩展 ({@code agentscope-extensions-a2a} + {@code
 * agentscope-a2a-spring-boot-starter})， 替换 AAF 自研的 {@code A2AProtocolService}（当前仅占位）。
 *
 * <p>AgentScope A2A 扩展提供：
 *
 * <ul>
 *   <li>标准 A2A 协议（Task / Artifact / Message 三种对象）
 *   <li>多种服务发现后端：Well-Known / Nacos / File
 *   <li>RocketMQ 异步传输（{@code agentscope-extensions-rocketmq}）
 *   <li>Spring Boot Starter 自动配置 A2A Server/Client
 * </ul>
 *
 * <p>TODO: 引入 agentscope-a2a-spring-boot-starter 后， 注入 AgentScope A2A Client/Server Bean， 删除 AAF 自研
 * A2AProtocolService，补全此处委托逻辑。
 */
@Slf4j
public class AgentScopeA2aAdapter {

    /**
     * 向外部 Agent 系统发送任务。
     *
     * @param agentId 目标 Agent ID（通过服务发现解析地址）
     * @param taskInput 任务输入
     * @return 任务 ID（异步，通过回调获取结果）
     */
    public String sendTask(String agentId, String taskInput) {
        // TODO: 引入 agentscope-a2a-spring-boot-starter 后，
        // 使用 AgentScope A2AClient.sendTask() 替换此处逻辑
        log.warn("A2A 适配器尚未完成，agentId={}", agentId);
        throw new UnsupportedOperationException("A2A 适配器待实现：引入 agentscope-a2a-spring-boot-starter");
    }
}
