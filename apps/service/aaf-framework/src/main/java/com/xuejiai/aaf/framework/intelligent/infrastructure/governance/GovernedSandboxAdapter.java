package com.xuejiai.aaf.framework.intelligent.infrastructure.governance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.xuejiai.aaf.framework.intelligent.agent.port.SandboxPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;

/** 任务 namespace 内的受控脚本执行；不暴露宿主 workspace 或凭据。 */
public final class GovernedSandboxAdapter implements SandboxPort {
    private static final long MAX_OUTPUT = 1024 * 1024;
    private final Path root;
    private final ConversationLeasePort leases;
    private final DelegatedTaskPort tasks;

    public GovernedSandboxAdapter(
            Path root, ConversationLeasePort leases, DelegatedTaskPort tasks) {
        this.root = Objects.requireNonNull(root, "root 不能为空").toAbsolutePath().normalize();
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public SandboxResult execute(SandboxRequest request) {
        requireCurrent(request);
        rejectCredentials(request.code());
        rejectHostEscape(request);
        var namespace = namespace(request);
        try {
            Files.createDirectories(namespace);
            var command = command(request, namespace);
            var processBuilder = new ProcessBuilder(command)
                    .directory(namespace.toFile())
                    .redirectErrorStream(false);
            processBuilder.environment().clear();
            processBuilder.environment().put("HOME", namespace.toString());
            processBuilder.environment().put("TMPDIR", namespace.toString());
            var process = processBuilder.start();
            var finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new SandboxResult(false, "", "执行超时", -1);
            }
            requireCurrent(request);
            return new SandboxResult(
                    process.exitValue() == 0,
                    limited(process.getInputStream().readAllBytes()),
                    limited(process.getErrorStream().readAllBytes()),
                    process.exitValue());
        } catch (IOException failure) {
            throw new IllegalStateException("启动 sandbox 失败", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sandbox 执行被中断", failure);
        }
    }

    private List<String> command(SandboxRequest request, Path namespace) throws IOException {
        return switch (request.language()) {
            case PYTHON -> {
                var script = namespace.resolve("invocation.py");
                Files.writeString(script, request.code());
                yield List.of("python3", script.toString());
            }
            case SHELL -> List.of("sh", "-c", request.code());
        };
    }

    private Path namespace(SandboxRequest request) {
        var context = request.context();
        var namespace = root.resolve("tenant=" + safe(context.tenantId().value()))
                .resolve("user=" + safe(context.userId().value()))
                .resolve("task=" + safe(context.taskId().value()))
                .normalize();
        if (!namespace.startsWith(root)) throw new IllegalStateException("sandbox namespace 越过 root");
        return namespace;
    }

    private void requireCurrent(SandboxRequest request) {
        if (request.context().lease() != null) leases.requireCurrent(request.context().lease());
        tasks.requireAgentExecution(request.context());
    }

    private static void rejectHostEscape(SandboxRequest request) {
        var code = request.code().toLowerCase(Locale.ROOT);
        var commonForbidden = List.of(
                "../", "..\\", "/etc/", "/proc/", "/sys/", "c:\\", "\\\\",
                "curl ", "wget ", "ssh ", "nc ", "netcat ");
        if (commonForbidden.stream().anyMatch(code::contains)) {
            throw new IllegalArgumentException("sandbox 代码禁止访问宿主路径或网络");
        }
        if (request.language() == Language.PYTHON
                && List.of("import os", "import pathlib", "import subprocess", "import socket",
                                "import urllib", "import requests", "__import__", "open(")
                        .stream().anyMatch(code::contains)) {
            throw new IllegalArgumentException("Python sandbox 禁止文件、网络和子进程 API");
        }
        if (request.language() == Language.SHELL
                && List.of("$(", "`", ">", "<", "env", "export ", "source ", ". ")
                        .stream().anyMatch(code::contains)) {
            throw new IllegalArgumentException("Shell sandbox 禁止重定向、环境读取和子命令");
        }
    }

    private static void rejectCredentials(String code) {
        var normalized = code.toLowerCase(Locale.ROOT);
        if (normalized.contains("api_key") || normalized.contains("apikey")
                || normalized.contains("access_token") || normalized.contains("password=")
                || normalized.contains("vaultref") || normalized.contains("credentialhandle")) {
            throw new IllegalArgumentException("sandbox 输入禁止包含凭据或凭据句柄");
        }
    }

    private static String limited(byte[] bytes) {
        return new String(bytes, 0, (int) Math.min(bytes.length, MAX_OUTPUT));
    }

    private static String safe(String value) {
        if (!value.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("非法 namespace 标识");
        return value;
    }
}
