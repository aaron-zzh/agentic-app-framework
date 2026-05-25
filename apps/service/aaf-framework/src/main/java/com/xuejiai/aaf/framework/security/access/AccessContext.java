package com.xuejiai.aaf.framework.security.access;

import java.util.EnumSet;
import java.util.Set;

/**
 * 权限处理上下文——跟踪哪些层已完成校验。
 *
 * <p>基于 ThreadLocal，每次请求独立。前一层标记已处理后，后续层可跳过。
 */
public final class AccessContext {

    private static final ThreadLocal<Set<AccessLayer>> PROCESSED =
            ThreadLocal.withInitial(() -> EnumSet.noneOf(AccessLayer.class));

    private AccessContext() {}

    /** 标记某层已完成权限校验。 */
    public static void markProcessed(AccessLayer layer) {
        PROCESSED.get().add(layer);
    }

    /** 检查某层是否已处理（已处理则后续层可跳过）。 */
    public static boolean isProcessed(AccessLayer layer) {
        return PROCESSED.get().contains(layer);
    }

    /** 检查指定层或更高层是否已处理。 */
    public static boolean isHandledByHigherLayer(AccessLayer currentLayer) {
        for (var layer : AccessLayer.values()) {
            if (layer.ordinal() >= currentLayer.ordinal()) break;
            if (PROCESSED.get().contains(layer)) return true;
        }
        return false;
    }

    /** 请求结束时清理（由 Filter 调用）。 */
    public static void clear() {
        PROCESSED.remove();
    }
}
