package com.xuejiai.aaf.framework.intelligent.agent.application;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ScriptExecutor;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 在 GraalVM 受限运行时中执行智能体生成的纯 JavaScript 计算脚本。 */
public final class JavaScriptExecutionTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "script.execute.javascript";

    private static final Set<String> ALLOWED_ARGUMENTS =
            Set.of("code", "arguments", "timeoutSeconds");
    private static final int MAX_CODE_LENGTH = 20_000;
    private static final int MAX_ARGUMENTS_JSON_LENGTH = 65_536;
    private static final int MAX_STREAM_LENGTH = 16_000;
    private static final long DEFAULT_TIMEOUT_SECONDS = 5;
    private static final long MAX_TIMEOUT_SECONDS = 10;

    private final ScriptExecutor scriptExecutor;

    public JavaScriptExecutionTool(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "scriptExecutor 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "在受限 GraalVM JavaScript 运行时中执行纯计算脚本；通过 args 读取 JSON 参数，并将返回值赋给 __result。";
    }

    /** GraalVM 沙箱内执行的纯计算脚本，无外部 I/O、不修改任何持久状态——沙箱隔离本身已是安全边界。 */
    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "code",
                        Map.of("type", "string", "description", "纯计算 JavaScript 代码，不超过 20000 个字符"),
                        "arguments",
                        Map.of("type", "object", "description", "脚本可读取的 JSON 参数（可选）"),
                        "timeoutSeconds",
                        Map.of("type", "integer", "description", "超时秒数，1 到 10 之间（可选，默认 5）")),
                "required",
                List.of("code"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> execute(invocation))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult execute(ToolInvocation invocation) {
        var parameters = invocation.arguments();
        rejectUnknownArguments(parameters);

        var code = requiredCode(parameters.get("code"));
        var scriptArguments = scriptArguments(parameters.get("arguments"));
        var argumentsJson = JsonUtils.toJsonString(scriptArguments);
        if (argumentsJson.length() > MAX_ARGUMENTS_JSON_LENGTH) {
            throw new IllegalArgumentException("arguments 序列化后不能超过 65536 个字符");
        }

        var timeoutSeconds = timeoutSeconds(parameters.get("timeoutSeconds"));
        var result =
                scriptExecutor.executeJs(code, argumentsJson, Duration.ofSeconds(timeoutSeconds));

        var stdout = limited(result.stdout());
        var stderr = limited(result.stderr());
        var output = new LinkedHashMap<String, Object>();
        output.put("success", result.success());
        output.put("stdout", stdout.value());
        output.put("stderr", stderr.value());
        output.put("exitCode", result.exitCode());
        output.put("runtime", "graalvm");
        output.put("truncated", stdout.truncated() || stderr.truncated());
        return new ToolInvocationResult(
                JsonUtils.toJsonString(output), Map.of("runtime", "graalvm", "toolUnits", 1));
    }

    private static void rejectUnknownArguments(Map<String, Object> parameters) {
        parameters.keySet().stream()
                .filter(name -> !ALLOWED_ARGUMENTS.contains(name))
                .findFirst()
                .ifPresent(
                        name -> {
                            throw new IllegalArgumentException("不支持的参数: " + name);
                        });
    }

    private static String requiredCode(Object value) {
        if (!(value instanceof String code) || code.isBlank()) {
            throw new IllegalArgumentException("code 必须是非空 JavaScript 字符串");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("code 不能超过 20000 个字符");
        }
        return code;
    }

    private static Map<?, ?> scriptArguments(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> arguments)) {
            throw new IllegalArgumentException("arguments 必须是 JSON 对象");
        }
        return arguments;
    }

    private static long timeoutSeconds(Object value) {
        if (value == null) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("timeoutSeconds 必须是 1 到 10 的整数");
        }
        var doubleValue = number.doubleValue();
        var seconds = number.longValue();
        if (!Double.isFinite(doubleValue)
                || doubleValue != seconds
                || seconds < 1
                || seconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("timeoutSeconds 必须是 1 到 10 的整数");
        }
        return seconds;
    }

    private static LimitedText limited(String value) {
        var normalized = value == null ? "" : value;
        if (normalized.length() <= MAX_STREAM_LENGTH) {
            return new LimitedText(normalized, false);
        }
        return new LimitedText(normalized.substring(0, MAX_STREAM_LENGTH), true);
    }

    private record LimitedText(String value, boolean truncated) {}
}
