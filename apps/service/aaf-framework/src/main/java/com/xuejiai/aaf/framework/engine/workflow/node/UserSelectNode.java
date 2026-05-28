package com.xuejiai.aaf.framework.engine.workflow.node;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户选择节点——暂停工作流等待用户从选项中选择。
 *
 * <p>流程变量：options（JSON 数组）、selectedOption（用户选择后写入）、waitingForInput（节点写入）
 */
@Slf4j
@Component("userSelectNode")
public class UserSelectNode implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("waitingForInput", true);
        execution.setVariable("nodeType", "userSelect");
        log.info("UserSelectNode 等待用户选择: processInstance={}", execution.getProcessInstanceId());
    }
}
