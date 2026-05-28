package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import io.agentscope.core.session.Session;
import io.agentscope.core.session.redis.RedisSession;
import io.lettuce.core.RedisClient;

/**
 * AAF Session 适配器——封装 AgentScope RedisSession 实现状态持久化。
 *
 * <h3>解决什么问题</h3>
 *
 * <p>Agent 执行中的状态（对话历史、Memory、PlanNotebook）需要持久化到 Redis，支持：
 *
 * <ul>
 *   <li>断点续跑：Agent 中断后从 Redis 恢复状态继续执行
 *   <li>优雅关闭：服务重启不丢失正在执行的 Agent 状态
 *   <li>跨实例迁移：多实例部署时 Agent 状态可在实例间转移
 * </ul>
 *
 * <h3>与 AAF 现有组件的关系</h3>
 *
 * <ul>
 *   <li>{@code SessionManager}：管会话元数据（创建/列表/删除），本适配器管 Agent 运行时状态持久化
 *   <li>{@code AgentCheckpointService}：可委托给 RedisSession 的 saveTo/loadFrom 实现
 *   <li>{@code AgentPool}：Agent 归还时通过 Session 清理状态
 * </ul>
 *
 * <h3>使用流程</h3>
 *
 * <pre>
 * // 1. 创建 Session（应用启动时，单例）
 * var session = AafSessionAdapter.createRedisSession(redisClient);
 *
 * // 2. Agent 创建时绑定 Session
 * var agent = ReActAgent.builder()
 *     .session(session)
 *     .sessionId("user-123-task-456")
 *     .build();
 *
 * // 3. Agent 执行中自动持久化（每步执行后 AgentScope 自动 saveTo）
 *
 * // 4. 断点恢复
 * var agent = ReActAgent.builder()
 *     .session(session)
 *     .sessionId("user-123-task-456")  // 同一 ID → 自动 loadFrom 恢复
 *     .build();
 *
 * // 5. Agent 完成后清理
 * session.delete("user-123-task-456");
 * </pre>
 */
public class AafSessionAdapter {

    private AafSessionAdapter() {}

    /**
     * 创建基于 Lettuce 的 RedisSession 实例。
     *
     * @param redisClient Lettuce RedisClient
     * @return AgentScope RedisSession
     */
    public static Session createRedisSession(RedisClient redisClient) {
        return RedisSession.builder().lettuceClient(redisClient).build();
    }
}
