package com.xuejiai.aaf.framework.engine.workflow.node;

import java.time.Duration;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ScriptExecutor;
import com.xuejiai.aaf.framework.engine.tool.ScriptSandbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 代码执行节点——在沙箱中执行 JS/Python 代码片段。
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
 * <p>m25：两种语言统一走 {@link ScriptExecutor}（GraalVM Polyglot 优先，缺依赖时降级子进程）。 原实现 JS 走 {@code
 * sandbox.executeShell("node -e " + code)}——多套一层 shell、依赖宿主机 node， 隔离强度明显弱于 Python 路径，且 shell
 * 侧只有关键词黑名单防护。现在不再有 shell 外壳。
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
                    default -> ScriptSandbox.ScriptResult.error("不支持的语言: " + language);
                };

        execution.setVariable("output", result.stdout());
        execution.setVariable("success", result.success());
        if (!result.success()) {
            execution.setVariable("error", result.stderr());
        }
    }
}
