package com.xuejiai.aaf.framework.security.authorization;

/** 防止策略 JSON 造成深度、节点、数组或字符串资源消耗。 */
public record PolicyDslLimits(
        int maxJsonLength, int maxDepth, int maxNodes, int maxArrayLength, int maxStringLength) {

    public PolicyDslLimits {
        if (maxJsonLength <= 0
                || maxDepth <= 0
                || maxNodes <= 0
                || maxArrayLength <= 0
                || maxStringLength <= 0) {
            throw new IllegalArgumentException("DSL 限制必须全部大于零");
        }
    }

    public static PolicyDslLimits defaults() {
        return new PolicyDslLimits(16_384, 12, 128, 64, 1024);
    }
}
