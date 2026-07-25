package com.xuejiai.aaf.framework.engine.task.agent;

/** 可由智能体任务运行时执行的技术任务契约。 */
public interface AgentTask {

    String taskType();

    AgentTaskOutcome execute(AgentTaskContext context);
}
