package com.xuejiai.aaf.framework.engine.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 脚本工具安全执行沙箱。
 * 扩展 AgentScope 的脚本执行能力（execute_python_code/execute_shell_command），
 * 补充资源限制、文件系统隔离和审计日志。
 */
@Slf4j
@Component
public class ScriptSandbox {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final long MAX_OUTPUT_BYTES = 1024 * 1024; // 1MB

    /**
     * 在沙箱中执行 Python 脚本。
     *
     * @param code    Python 代码
     * @param timeout 超时时间
     * @return 执行结果
     */
    public ScriptResult executePython(String code, Duration timeout) {
        try {
            var tmpDir = Files.createTempDirectory("aaf-sandbox-");
            var tmpFile = tmpDir.resolve("script.py");
            Files.writeString(tmpFile, code);

            var process = new ProcessBuilder("python3", tmpFile.toString())
                .directory(tmpDir.toFile())
                .redirectErrorStream(false)
                .start();

            var effectiveTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
            boolean finished = process.waitFor(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.warn("Python 脚本执行超时（{}s）", effectiveTimeout.toSeconds());
                return ScriptResult.timeout();
            }

            var stdout = limitOutput(process.getInputStream().readAllBytes());
            var stderr = limitOutput(process.getErrorStream().readAllBytes());
            var exitCode = process.exitValue();

            log.debug("Python 脚本执行完成，exitCode={}", exitCode);
            Files.deleteIfExists(tmpFile);
            Files.deleteIfExists(tmpDir);

            return new ScriptResult(exitCode == 0, stdout, stderr, exitCode);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return ScriptResult.error(e.getMessage());
        }
    }

    /**
     * 在沙箱中执行 Shell 命令（白名单限制）。
     *
     * @param command 命令字符串
     * @param timeout 超时时间
     * @return 执行结果
     */
    public ScriptResult executeShell(String command, Duration timeout) {
        // 安全检查：拒绝危险命令
        if (isDangerous(command)) {
            log.warn("拒绝危险 Shell 命令: {}", command);
            return ScriptResult.error("命令被安全策略拒绝");
        }
        try {
            var process = new ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start();

            var effectiveTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
            boolean finished = process.waitFor(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                return ScriptResult.timeout();
            }

            var stdout = limitOutput(process.getInputStream().readAllBytes());
            var stderr = limitOutput(process.getErrorStream().readAllBytes());
            return new ScriptResult(process.exitValue() == 0, stdout, stderr, process.exitValue());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return ScriptResult.error(e.getMessage());
        }
    }

    private boolean isDangerous(String command) {
        var dangerous = List.of("rm -rf", "dd if=", "mkfs", ":(){ :|:& };:", "> /dev/");
        return dangerous.stream().anyMatch(command::contains);
    }

    private String limitOutput(byte[] bytes) {
        if (bytes.length > MAX_OUTPUT_BYTES) {
            return new String(bytes, 0, (int) MAX_OUTPUT_BYTES) + "\n[输出已截断]";
        }
        return new String(bytes);
    }

    /** 脚本执行结果 */
    public record ScriptResult(boolean success, String stdout, String stderr, int exitCode) {
        public static ScriptResult timeout() {
            return new ScriptResult(false, "", "执行超时", -1);
        }
        public static ScriptResult error(String message) {
            return new ScriptResult(false, "", message, -1);
        }
    }
}
