package com.xuejiai.aaf.framework.intelligent.shared.id;

import java.util.Objects;

/** AAF 智能层跨边界使用的稳定标识。 */
public sealed interface StableId
        permits StableId.TenantId,
                StableId.UserId,
                StableId.AssistantId,
                StableId.AgentId,
                StableId.ConversationId,
                StableId.SessionId,
                StableId.TaskId,
                StableId.ExecutionId,
                StableId.RunId,
                StableId.EventId,
                StableId.CorrelationId,
                StableId.CausationId,
                StableId.IdempotencyKey {

    /** 返回不带类型前缀的稳定值。 */
    String value();

    private static String validate(String name, String value) {
        Objects.requireNonNull(value, name + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException(name + " 不能包含首尾空白");
        }
        if (value.length() > 128) {
            throw new IllegalArgumentException(name + " 长度不能超过 128");
        }
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(name + " 包含非法字符");
        }
        return value;
    }

    /**
     * 租户命名空间标识。
     *
     * <p>当前由业务系统的 {@code org_id} 承载（Controller 层以 {@code new TenantId(orgId.toString())}
     * 构造），是 {@code org_id} 的字符串投影，与 {@code org_id} 是同一个隔离维度，不是并行的第二套隔离机制。
     * 领域层使用 {@code TenantId} 而非直接依赖 {@code org_id: Long} 是为了不让五层架构领域模型依赖具体业务模块的
     * 持久化类型；若未来隔离维度的实际来源发生变化，只需调整取值处，领域层契约不受影响。
     */
    record TenantId(String value) implements StableId {
        public TenantId {
            value = StableId.validate("tenantId", value);
        }
    }

    /** 用户标识。 */
    record UserId(String value) implements StableId {
        public UserId {
            value = StableId.validate("userId", value);
        }
    }

    /** 助理定义标识。 */
    record AssistantId(String value) implements StableId {
        public AssistantId {
            value = StableId.validate("assistantId", value);
        }
    }

    /** Agent 定义标识。 */
    record AgentId(String value) implements StableId {
        public AgentId {
            value = StableId.validate("agentId", value);
        }
    }

    /** 用户可见对话标识。 */
    record ConversationId(String value) implements StableId {
        public ConversationId {
            value = StableId.validate("conversationId", value);
        }
    }

    /** Agent 执行状态槽位标识。 */
    record SessionId(String value) implements StableId {
        public SessionId {
            value = StableId.validate("sessionId", value);
        }
    }

    /** 用户目标或任务标识。 */
    record TaskId(String value) implements StableId {
        public TaskId {
            value = StableId.validate("taskId", value);
        }
    }

    /** 一次责任主体持有的持久执行标识。 */
    record ExecutionId(String value) implements StableId {
        public ExecutionId {
            value = StableId.validate("executionId", value);
        }
    }

    /** 一次活跃调用或恢复段标识。 */
    record RunId(String value) implements StableId {
        public RunId {
            value = StableId.validate("runId", value);
        }
    }

    /** 单个执行事件的全局唯一标识。 */
    record EventId(String value) implements StableId {
        public EventId {
            value = StableId.validate("eventId", value);
        }
    }

    /** 同一用户目标调用链的关联标识。 */
    record CorrelationId(String value) implements StableId {
        public CorrelationId {
            value = StableId.validate("correlationId", value);
        }
    }

    /** 直接原因事件标识。 */
    record CausationId(String value) implements StableId {
        public CausationId {
            value = StableId.validate("causationId", value);
        }
    }

    /** 外部副作用或重放操作的幂等标识。 */
    record IdempotencyKey(String value) implements StableId {
        public IdempotencyKey {
            value = StableId.validate("idempotencyKey", value);
        }
    }
}
