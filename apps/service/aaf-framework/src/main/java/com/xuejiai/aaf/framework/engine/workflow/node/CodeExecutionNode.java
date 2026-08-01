package com.xuejiai.aaf.framework.engine.workflow.node;

import java.time.Duration;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ScriptExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 代码执行节点——在受限运行时中执行代码片段。
 *
 * <p>BPMN 用法：{@code flowable:delegateExpression="${codeExecutionNode}"}
 *
 * <p>流程变量：
 *
 * <ul>
 *   <li>code（必填）——待执行代码
 *   <li>language（必填）——语言类型：js/python
 *   <li>args（可选）——传给脚本的 JSON 参数，脚本内通过 {@code args} 访问
 *   <li>timeout（可选，默认30）——超时秒数
 *   <li>output/success/error（节点写入）
 * </ul>
 *
 * <p>B5：所有语言统一走 {@link ScriptExecutor}。当前仅 JavaScript 具备 GraalVM 受限运行时；Python 在安全运行时未启用前明确拒绝，
 * 不再降级到宿主机子进程或 shell。
 */
@Slf4j
@Component("codeExecutionNode")
@RequiredArgsConstructor
public class CodeExecutionNode implements JavaDelegate {

    private final ScriptExecutor scriptExecutor;

    @Override
    public void execute(DelegateExecution execution) {
        var code = (String) execution.getVariable("code");
        var language = (String) execution.getVariable("language");
        var argsJson =
                execution.getVariable("args") != null
                        ? String.valueOf(execution.getVariable("args"))
                        : "{}";
        var timeoutSec =
                execution.getVariable("timeout") != null
                        ? ((Number) execution.getVariable("timeout")).longValue()
                        : 30L;
        var timeout = Duration.ofSeconds(timeoutSec);

        var result =
                switch (language == null ? "" : language.toLowerCase()) {
                    case "python" -> scriptExecutor.executePython(code, argsJson, timeout);
                    case "js", "javascript" -> scriptExecutor.executeJs(code, argsJson, timeout);
                    default -> ScriptExecutor.ScriptResult.error("不支持的语言: " + language);
                };

        execution.setVariable("output", result.stdout());
        execution.setVariable("success", result.success());
        if (!result.success()) {
            execution.setVariable("error", result.stderr());
        }
    }
}
