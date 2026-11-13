package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;

/**
 * 轻量态角色切换登记（ADR-011）：{@code activate_role} 成功后登记，前一执行收口时读取并据此续接新角色执行。
 *
 * <p>进程内登记即可：切换只在同一条 SSE 内生效，续接由同一进程的入口管线发起，不跨节点、不持久化。
 */
public final class DirectRoleHandoffRegistry {

    private final ConcurrentHashMap<ExecutionId, Handoff> handoffs = new ConcurrentHashMap<>();

    /** 登记切换；同一执行重复登记时保留首次结果，保证每个执行最多切换一次。 */
    public boolean register(ExecutionId executionId, Handoff handoff) {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(handoff, "handoff 不能为空");
        return handoffs.putIfAbsent(executionId, handoff) == null;
    }

    /** 只读查看，不移除；收口阶段判断是否需要按切换结束前一执行。 */
    public Optional<Handoff> peek(ExecutionId executionId) {
        return Optional.ofNullable(
                handoffs.get(Objects.requireNonNull(executionId, "executionId 不能为空")));
    }

    /** 取出并移除；续接执行开始前调用，避免同一切换被重复续接。 */
    public Optional<Handoff> take(ExecutionId executionId) {
        return Optional.ofNullable(
                handoffs.remove(Objects.requireNonNull(executionId, "executionId 不能为空")));
    }

    public record Handoff(
            String fromRoleKey,
            String toRoleKey,
            String toRoleName,
            String reason,
            long assistantRevision) {
        public Handoff {
            Objects.requireNonNull(toRoleKey, "toRoleKey 不能为空");
            Objects.requireNonNull(toRoleName, "toRoleName 不能为空");
            reason = reason == null ? "" : reason;
            if (assistantRevision < 0) {
                throw new IllegalArgumentException("assistantRevision 不能小于 0");
            }
        }
    }
}
