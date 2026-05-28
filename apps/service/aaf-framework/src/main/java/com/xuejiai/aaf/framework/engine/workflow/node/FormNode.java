package com.xuejiai.aaf.framework.engine.workflow.node;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 表单节点——暂停工作流等待用户输入表单数据。
 *
 * <p>实现方式：设置流程变量 waitingForInput=true，外部通过 completeTask 提交表单数据恢复流程。
 *
 * <p>流程变量：formSchema（JSON 表单定义）、formData（用户提交后写入）、waitingForInput（节点写入）
 */
@Slf4j
@Component("formNode")
public class FormNode implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // 标记等待用户输入，实际暂停由 Flowable UserTask 或信号事件实现
        execution.setVariable("waitingForInput", true);
        execution.setVariable("nodeType", "form");
        log.info("FormNode 等待用户输入: processInstance={}", execution.getProcessInstanceId());
    }
}
