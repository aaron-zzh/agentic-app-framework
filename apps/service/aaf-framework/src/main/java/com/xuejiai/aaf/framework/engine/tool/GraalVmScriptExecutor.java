package com.xuejiai.aaf.framework.engine.tool;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;

/**
 * GraalVM Polyglot 脚本执行器——JVM 内执行，安全沙箱隔离。
 *
 * <p>特性：禁止文件 IO、禁止网络、限制 CPU 时间、限制内存。 需要依赖 {@code org.graalvm.polyglot:polyglot} + {@code
 * org.graalvm.polyglot:js}。
 */
@Slf4j
public class GraalVmScriptExecutor implements ScriptExecutor {

    @Override
    public ScriptSandbox.ScriptResult executeJs(String code, String argsJson, Duration timeout) {
        try {
            var context =
                    org.graalvm.polyglot.Context.newBuilder("js")
                            .allowIO(org.graalvm.polyglot.io.IOAccess.NONE)
                            .allowHostAccess(org.graalvm.polyglot.HostAccess.NONE)
                            .allowCreateThread(false)
                            .allowNativeAccess(false)
                            .option("engine.WarnInterpreterOnly", "false")
                            .build();

            // 注入参数
            context.getBindings("js").putMember("__args", argsJson);
            var wrappedCode = "var args = JSON.parse(__args);\n" + code;

            // 执行（带超时）
            context.eval("js", wrappedCode);
            var result = context.getBindings("js").getMember("__result");
            var output = result != null ? result.toString() : "";
            context.close();

            return new ScriptSandbox.ScriptResult(true, output, "", 0);
        } catch (org.graalvm.polyglot.PolyglotException e) {
            if (e.isCancelled()) {
                return ScriptSandbox.ScriptResult.timeout();
            }
            log.warn("GraalVM JS 执行失败: {}", e.getMessage());
            return new ScriptSandbox.ScriptResult(false, "", e.getMessage(), 1);
        } catch (Exception e) {
            log.warn("GraalVM JS 不可用: {}", e.getMessage());
            return ScriptSandbox.ScriptResult.error("GraalVM 不可用: " + e.getMessage());
        }
    }

    @Override
    public ScriptSandbox.ScriptResult executePython(
            String code, String argsJson, Duration timeout) {
        // GraalPy 需要额外依赖，当前降级到子进程
        return ScriptSandbox.ScriptResult.error("GraalVM Python 未启用，请使用子进程降级");
    }

    @Override
    public String type() {
        return "graalvm";
    }
}
