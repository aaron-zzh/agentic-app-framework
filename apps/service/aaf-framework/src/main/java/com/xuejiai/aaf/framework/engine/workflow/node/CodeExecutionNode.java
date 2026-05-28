package com.xuejiai.aaf.framework.engine.workflow.node;

import java.time.Duration;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ScriptSandbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 代码执行节点——在沙箱中执行 JS/Python 代码片段。
 *
 * <p>BPMN 用法：{@code flowable:delegateExpression="${codeExecutionNode}"}
 *
 * <p>流程变量：
 * <ul>
 *   <li>code（必填）——待执行代码</li>
 *   <li>language（必填）——语言类型：js/python</li>
 *   <li>timeout（可选，默认30）——超时秒数</li>
 *   <li>output/success/error（节点写入）</li>
 * </ul>
 */
@Slf4j
@Component("codeExecutionNode")
@RequiredArgsConstructor
public class CodeExecutionNode implements JavaDelegate {

    private final ScriptSandbox sandbox;

    @Override
    public void execute(DelegateExecution execution) {
        var code = (String) execution.getVariable("code");
        var language = (String) execution.getVariable("language");
        var timeoutSec = execution.getVariable("timeout") != null
                ? ((Number) execution.getVariable("timeout")).longValue() : 30L;
        var timeout = Duration.ofSeconds(timeoutSec);

        var result = switch (language.toLowerCase()) {
            case "python" -> sandbox.executePython(code, timeout);
            case "js", "javascript" -> sandbox.executeShell("node -e " + escapeForShell(code), timeout);
            default -> ScriptSandbox.ScriptResult.error("不支持的语言: " + language);
        };

        execution.setVariable("output", result.stdout());
        execution.setVariable("success", result.success());
        if (!result.success()) {
            execution.setVariable("error", result.stderr());
        }
    }

    private String escapeForShell(String code) {
        return "'" + code.replace("'", "'\\''") + "'";
    }
}
