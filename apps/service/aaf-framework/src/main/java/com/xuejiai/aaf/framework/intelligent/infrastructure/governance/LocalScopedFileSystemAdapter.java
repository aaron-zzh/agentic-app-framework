package com.xuejiai.aaf.framework.intelligent.infrastructure.governance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.port.ScopedFileSystemPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;

/** 本地文件系统生产适配器，所有真实路径严格限定在 tenant/user/task namespace。 */
public final class LocalScopedFileSystemAdapter implements ScopedFileSystemPort {
    private final Path root;
    private final ConversationLeasePort leases;
    private final DelegatedTaskPort tasks;

    public LocalScopedFileSystemAdapter(
            Path root, ConversationLeasePort leases, DelegatedTaskPort tasks) {
        this.root = Objects.requireNonNull(root, "root 不能为空").toAbsolutePath().normalize();
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public String read(ScopedPath path) {
        requireCurrent(path);
        try {
            return Files.readString(resolveExisting(path));
        } catch (IOException failure) {
            throw new IllegalStateException("读取任务文件失败", failure);
        }
    }

    @Override
    public void write(ScopedPath path, String content) {
        requireCurrent(path);
        try {
            var target = resolveWritable(path);
            var temporary = target.resolveSibling(target.getFileName() + ".fence-"
                    + fencingToken(path) + ".tmp");
            Files.writeString(temporary, Objects.requireNonNull(content, "content 不能为空"));
            requireCurrent(path);
            Files.move(
                    temporary,
                    target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            requireCurrent(path);
        } catch (IOException failure) {
            throw new IllegalStateException("写入任务文件失败", failure);
        }
    }

    @Override
    public List<String> list(ScopedPath path) {
        requireCurrent(path);
        try (var entries = Files.list(resolveExisting(path))) {
            return entries.map(item -> item.getFileName().toString()).sorted().toList();
        } catch (IOException failure) {
            throw new IllegalStateException("列出任务文件失败", failure);
        }
    }

    private Path resolveExisting(ScopedPath path) throws IOException {
        var namespace = ensureNamespace(path);
        var target = lexicalTarget(namespace, path.relativePath());
        var realTarget = target.toRealPath();
        var realNamespace = namespace.toRealPath();
        if (!realTarget.startsWith(realNamespace)) {
            throw new IllegalArgumentException("文件真实路径越过 task namespace");
        }
        return realTarget;
    }

    private Path resolveWritable(ScopedPath path) throws IOException {
        var namespace = ensureNamespace(path);
        var target = lexicalTarget(namespace, path.relativePath());
        Files.createDirectories(target.getParent());
        var realNamespace = namespace.toRealPath();
        var realParent = target.getParent().toRealPath();
        if (!realParent.startsWith(realNamespace)
                || (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target))) {
            throw new IllegalArgumentException("写入路径通过符号链接越过 task namespace");
        }
        return target;
    }

    private Path ensureNamespace(ScopedPath path) throws IOException {
        var context = path.context();
        var namespace = root.resolve("tenant=" + safe(context.tenantId().value()))
                .resolve("user=" + safe(context.userId().value()))
                .resolve("task=" + safe(context.taskId().value()))
                .normalize();
        if (!namespace.startsWith(root)) {
            throw new IllegalArgumentException("task namespace 越过 root");
        }
        Files.createDirectories(namespace);
        return namespace;
    }

    private static Path lexicalTarget(Path namespace, String relativePath) {
        var relative = Path.of(relativePath);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("任务文件路径必须为相对路径");
        }
        var target = namespace.resolve(relative).normalize();
        if (!target.startsWith(namespace) || target.equals(namespace)) {
            throw new IllegalArgumentException("文件路径越过 task namespace");
        }
        return target;
    }

    private void requireCurrent(ScopedPath path) {
        if (path.context().lease() != null) leases.requireCurrent(path.context().lease());
        tasks.requireAgentExecution(path.context());
    }

    private static long fencingToken(ScopedPath path) {
        return path.context().lease() == null ? 0L : path.context().lease().fencingToken();
    }

    private static String safe(String value) {
        if (!value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("namespace 标识包含非法字符");
        }
        return value;
    }
}
