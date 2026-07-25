package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;

/** Agent 文件系统边界，路径只能位于 tenant/user/task namespace。 */
public interface ScopedFileSystemPort {

    String read(ScopedPath path);

    void write(ScopedPath path, String content);

    List<String> list(ScopedPath path);

    record ScopedPath(InvocationContext context, String relativePath) {
        public ScopedPath {
            Objects.requireNonNull(context, "context 不能为空");
            if (relativePath == null || relativePath.isBlank()) {
                throw new IllegalArgumentException("relativePath 不能为空白");
            }
        }
    }
}
