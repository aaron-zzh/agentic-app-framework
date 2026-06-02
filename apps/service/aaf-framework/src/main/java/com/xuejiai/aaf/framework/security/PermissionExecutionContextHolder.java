package com.xuejiai.aaf.framework.security;

import java.util.Objects;

/** 权限执行上下文。用于内部流程临时以指定用户的权限边界执行。 */
public final class PermissionExecutionContextHolder {

    private static final ThreadLocal<PermissionExecutionContext> HOLDER = new ThreadLocal<>();

    private PermissionExecutionContextHolder() {}

    public static PermissionExecutionContext get() {
        return HOLDER.get();
    }

    public static Scope useOwner(Long ownerId, String reason) {
        var previous = HOLDER.get();
        HOLDER.set(
                new PermissionExecutionContext(Objects.requireNonNull(ownerId, "ownerId"), reason));
        return new Scope(previous);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record PermissionExecutionContext(Long ownerId, String reason) {}

    /** 自动恢复上一个上下文，避免线程复用时权限上下文泄漏。 */
    public static final class Scope implements AutoCloseable {
        private final PermissionExecutionContext previous;
        private boolean closed;

        private Scope(PermissionExecutionContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
            closed = true;
        }
    }
}
