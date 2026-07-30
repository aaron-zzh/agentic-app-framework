package com.xuejiai.aaf.framework.engine.workflow.node;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher;

import lombok.RequiredArgsConstructor;

/** 工具节点——通过统一工具分发器执行权限、内容安全、置信度和审计检查。 */
@Component("toolNode")
@RequiredArgsConstructor
public class ToolNode implements JavaDelegate {

    private final ToolCallDispatcher toolDispatcher;

    @Override
    public void execute(DelegateExecution execution) {
        var toolName = requiredString(execution, "toolName");
        var arguments = stringVariable(execution, "arguments", "{}");
        var sessionId =
                stringVariable(
                        execution, "sessionId", "workflow:" + execution.getProcessInstanceId());
        var userId = longVariable(execution, "_aafUserId");
        var roleId = longVariable(execution, "_aafRoleId");
        var result =
                toolDispatcher.dispatchWithPermission(
                        sessionId, userId, roleId, toolName, arguments);
        execution.setVariable("success", result.success());
        execution.setVariable("output", result.output());
        execution.setVariable("toolResultCode", result.code());
        execution.setVariable("pendingApproval", result.pendingApproval());
        if (!result.success()) {
            execution.setVariable(
                    "error", result.error() != null ? result.error() : result.message());
        }
    }

    private String requiredString(DelegateExecution execution, String name) {
        var value = stringVariable(execution, name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("工具节点缺少变量: " + name);
        }
        return value;
    }

    private String stringVariable(
            DelegateExecution execution, String name, String defaultValue) {
        var value = execution.getVariable(name);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private Long longVariable(DelegateExecution execution, String name) {
        var value = execution.getVariable(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }
}
