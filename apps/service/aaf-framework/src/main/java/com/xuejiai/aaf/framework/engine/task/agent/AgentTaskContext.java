package com.xuejiai.aaf.framework.engine.task.agent;

import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort.Lease;

/** 智能体任务单次执行上下文。 */
public record AgentTaskContext(String taskId, String tenantId, String triggerType, Lease lease) {}
