package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import java.util.List;

import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.Msg;

import lombok.RequiredArgsConstructor;

/**
 * AgentScope Memory → AAF 工作记忆适配器。
 *
 * <p>适配策略：委托给 AgentScope {@link Memory}，
 * 替换 AAF 自研的 {@code WorkingMemoryImpl}（Agent 执行期临时上下文）。
 *
 * <p>AgentScope 提供多种 Memory 后端（agentscope-extensions-autocontext-memory 扩展）：
 * <ul>
 *   <li>{@code InMemoryMemory} — 进程内，开发/测试</li>
 *   <li>{@code RedisMemory} — Redis 持久化，生产推荐</li>
 *   <li>{@code SqlAlchemyMemory} — 数据库持久化</li>
 *   <li>{@code TablestoreMemory} — 阿里云 Tablestore</li>
 * </ul>
 *
 * <p>autocontext-memory 扩展还提供 Token 预算自动截断（P0-P5 优先级），
 * 解决 AAF 上下文管理器缺失的 Token 预算控制问题。
 *
 * <p>TODO: 引入 agentscope-extensions-autocontext-memory 后配置 Token 预算策略。
 */
@RequiredArgsConstructor
public class AgentScopeMemoryAdapter {

    private final Memory delegate;

    /** 追加消息到工作记忆 */
    public void add(Msg msg) {
        delegate.add(msg);
    }

    /** 获取全部消息（用于组装 LLM 上下文） */
    public List<Msg> getAll() {
        return delegate.get();
    }

    /** 清空工作记忆（Agent 任务结束后调用） */
    public void clear() {
        delegate.clear();
    }

    /** 获取消息数量 */
    public int size() {
        return delegate.size();
    }
}
