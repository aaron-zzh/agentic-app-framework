package com.xuejiai.aaf.module.ai.assistant.port;

import java.util.Optional;

/** 程序化 Assistant Task 的只读查询边界，不包含 Task 命令执行依赖。 */
public interface ProgrammaticAssistantTaskQueryPort {

    /** 按当前操作者范围读取 canonical Task 状态。 */
    Optional<ProgrammaticAssistantTaskPort.TaskSnapshot> findTask(String taskId);
}
