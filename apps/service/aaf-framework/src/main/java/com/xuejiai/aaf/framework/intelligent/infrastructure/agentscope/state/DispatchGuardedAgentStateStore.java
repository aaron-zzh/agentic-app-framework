package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.state;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork.AgentStateAccess;

import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;

/** AgentScope 状态存储的唯一执行权边界；逻辑状态槽与 Dispatch fence 分离，读写权限仍逐次校验。 */
public final class DispatchGuardedAgentStateStore implements AgentStateStore {
    private final AgentStateStore delegate;
    private final TaskUnitOfWork tasks;
    private final ConcurrentHashMap<SessionKey, InvocationContext> bindings =
            new ConcurrentHashMap<>();

    public DispatchGuardedAgentStateStore(AgentStateStore delegate, TaskUnitOfWork tasks) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    /** 将共享 ReActAgent 的本次调用绑定到不可伪造的 InvocationContext；同一状态槽禁止本机并发占用。 */
    public Registration register(
            String stateUserKey, String sessionId, InvocationContext context) {
        var key = new SessionKey(stateUserKey, sessionId);
        Objects.requireNonNull(context, "context 不能为空");
        var existing = bindings.putIfAbsent(key, context);
        if (existing != null) {
            throw new TaskUnitOfWork.StaleExecutionException("AgentState 状态槽已被本机执行占用");
        }
        return () -> bindings.remove(key, context);
    }

    /** HITL 已关闭原 Dispatch 后，只允许仍为 exact waiting 且尚无新 Dispatch 的最终快照。 */
    public void saveSuspended(InvocationContext context, AgentState state) {
        Objects.requireNonNull(state, "state 不能为空");
        requireBound(state.getUserId(), state.getSessionId(), context);
        tasks.accessAgentState(
                context,
                AgentStateAccess.SUSPENDED_SNAPSHOT,
                () -> {
                    delegate.save(state.getUserId(), state.getSessionId(), "agent_state", state);
                    return null;
                });
    }

    /** 终态异步清理仅在原 Dispatch 仍是最新代时执行，禁止旧 worker 删除恢复后的同一状态槽。 */
    public void deleteStaleSafe(
            InvocationContext context, String stateUserKey, String sessionId) {
        tasks.accessAgentState(
                context,
                AgentStateAccess.STALE_SAFE_CLEANUP,
                () -> {
                    delegate.delete(stateUserKey, sessionId);
                    return null;
                });
    }

    @Override
    public void save(String userId, String sessionId, String key, State state) {
        access(
                userId,
                sessionId,
                () -> {
                    delegate.save(userId, sessionId, key, state);
                    return null;
                });
    }

    @Override
    public void save(
            String userId, String sessionId, String key, List<? extends State> states) {
        access(
                userId,
                sessionId,
                () -> {
                    delegate.save(userId, sessionId, key, states);
                    return null;
                });
    }

    @Override
    public <T extends State> Optional<T> get(
            String userId, String sessionId, String key, Class<T> stateType) {
        return access(userId, sessionId, () -> delegate.get(userId, sessionId, key, stateType));
    }

    @Override
    public <T extends State> List<T> getList(
            String userId, String sessionId, String key, Class<T> stateType) {
        return access(
                userId, sessionId, () -> delegate.getList(userId, sessionId, key, stateType));
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return access(userId, sessionId, () -> delegate.exists(userId, sessionId));
    }

    @Override
    public void delete(String userId, String sessionId) {
        access(
                userId,
                sessionId,
                () -> {
                    delegate.delete(userId, sessionId);
                    return null;
                });
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        access(
                userId,
                sessionId,
                () -> {
                    delegate.delete(userId, sessionId, key);
                    return null;
                });
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        var matches =
                bindings.entrySet().stream()
                        .filter(entry -> entry.getKey().userId().equals(userId))
                        .toList();
        if (matches.size() != 1) {
            throw new TaskUnitOfWork.StaleExecutionException(
                    "AgentState userId 未绑定唯一调用上下文");
        }
        var binding = matches.getFirst();
        return tasks.accessAgentState(
                binding.getValue(),
                AgentStateAccess.ACTIVE,
                () -> delegate.listSessionIds(userId));
    }

    @Override
    public void close() {
        bindings.clear();
        delegate.close();
    }

    private <T> T access(String userId, String sessionId, java.util.function.Supplier<T> operation) {
        var context = requireBound(userId, sessionId);
        return tasks.accessAgentState(context, AgentStateAccess.ACTIVE, operation);
    }

    private InvocationContext requireBound(String userId, String sessionId) {
        var context = bindings.get(new SessionKey(userId, sessionId));
        if (context == null) {
            throw new TaskUnitOfWork.StaleExecutionException(
                    "AgentState 访问缺少调用级 authority 绑定");
        }
        return context;
    }

    private void requireBound(
            String userId, String sessionId, InvocationContext expectedContext) {
        if (requireBound(userId, sessionId) != expectedContext) {
            throw new TaskUnitOfWork.StaleExecutionException(
                    "AgentState 调用上下文已被替换");
        }
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private record SessionKey(String userId, String sessionId) {
        private SessionKey {
            if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException("AgentState userId/sessionId 不能为空白");
            }
        }
    }
}
