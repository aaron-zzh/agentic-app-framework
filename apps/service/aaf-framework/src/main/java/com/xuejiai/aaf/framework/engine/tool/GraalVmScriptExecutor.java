package com.xuejiai.aaf.framework.engine.tool;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.IOAccess;

import lombok.extern.slf4j.Slf4j;

/** GraalVM Polyglot 受限脚本执行器。 */
@Slf4j
public class GraalVmScriptExecutor implements ScriptExecutor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;

    @Override
    public ScriptResult executeJs(String code, String argsJson, Duration timeout) {
        var effectiveTimeout =
                timeout != null && !timeout.isNegative() && !timeout.isZero()
                        ? timeout
                        : DEFAULT_TIMEOUT;
        var contextRef = new AtomicReference<Context>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> executeJsInContext(code, argsJson, contextRef));
            try {
                return future.get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                var context = contextRef.get();
                if (context != null) {
                    context.close(true);
                }
                future.cancel(true);
                log.warn("GraalVM JS 执行超时（{}ms）", effectiveTimeout.toMillis());
                return ScriptResult.timeout();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                return ScriptResult.error("执行已中断");
            } catch (ExecutionException e) {
                var cause = e.getCause();
                return ScriptResult.error(cause != null ? cause.getMessage() : e.getMessage());
            }
        }
    }

    private ScriptResult executeJsInContext(
            String code, String argsJson, AtomicReference<Context> contextRef) {
        try (var context =
                Context.newBuilder("js")
                        .allowIO(IOAccess.NONE)
                        .allowHostAccess(HostAccess.NONE)
                        .allowCreateThread(false)
                        .allowNativeAccess(false)
                        .option("engine.WarnInterpreterOnly", "false")
                        .build()) {
            contextRef.set(context);
            context.getBindings("js").putMember("__args", argsJson != null ? argsJson : "{}");
            context.eval("js", "var args = JSON.parse(__args);\n" + code);
            var result = context.getBindings("js").getMember("__result");
            var output = result != null ? limitOutput(result.toString()) : "";
            return new ScriptResult(true, output, "", 0);
        } catch (PolyglotException e) {
            if (e.isCancelled() || e.isInterrupted()) {
                return ScriptResult.timeout();
            }
            log.warn("GraalVM JS 执行失败: {}", e.getMessage());
            return new ScriptResult(false, "", e.getMessage(), 1);
        } catch (Exception e) {
            log.warn("GraalVM JS 不可用: {}", e.getMessage());
            return ScriptResult.error("GraalVM 不可用: " + e.getMessage());
        } finally {
            contextRef.set(null);
        }
    }

    @Override
    public ScriptResult executePython(String code, String argsJson, Duration timeout) {
        return ScriptResult.error("Python 安全执行环境未启用");
    }

    @Override
    public String type() {
        return "graalvm";
    }

    private String limitOutput(String output) {
        var bytes = output.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_OUTPUT_BYTES) {
            return output;
        }
        return new String(bytes, 0, MAX_OUTPUT_BYTES, StandardCharsets.UTF_8) + "\n[输出已截断]";
    }
}
