package com.xuejiai.aaf.framework.intelligent.assistant.model;

/** Assistant 当前行修订号，仅用于执行快照审计，不作为运行时选择参数。 */
public record AssistantVersion(long value) implements Comparable<AssistantVersion> {

    public AssistantVersion {
        if (value < 0) {
            throw new IllegalArgumentException("AssistantVersion 不能小于 0");
        }
    }

    @Override
    public int compareTo(AssistantVersion other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
