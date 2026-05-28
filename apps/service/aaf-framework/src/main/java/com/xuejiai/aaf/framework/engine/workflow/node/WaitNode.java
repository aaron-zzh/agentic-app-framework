package com.xuejiai.aaf.framework.engine.workflow.node;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 等待节点——设置等待标记，暂停流程执行直到外部信号恢复。
 *
 * <p>BPMN 用法：{@code flowable:delegateExpression="${waitNode}"}
 *
 * <p>流程变量：
 * <ul>
 *   <li>waitType（必填）——等待类型：signal/timer/human</li>
 *   <li>waitKey（必填）——等待标识，用于外部信号匹配</li>
 *   <li>waitStatus（节点写入）——当前等待状态：waiting/resumed</li>
 * </ul>
 *
 * <p>恢复方式：外部通过 RuntimeService.trigger() 或自定义信号机制恢复执行。
 */
@Slf4j
@Component("waitNode")
public class WaitNode implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        var waitType = (String) execution.getVariable("waitType");
        var waitKey = (String) execution.getVariable("waitKey");

        log.info("工作流进入等待状态: type={}, key={}, executionId={}",
                waitType, waitKey, execution.getId());

        execution.setVariable("waitStatus", "waiting");
        execution.setVariable("_waitType", waitType);
        execution.setVariable("_waitKey", waitKey);
        execution.setVariable("_waitExecutionId", execution.getId());

        // 标记为等待状态，Flowable 将在此暂停
        // 实际暂停由 BPMN 中间捕获事件或 receive task 配合实现
        // 此节点负责设置等待元数据
    }
}
