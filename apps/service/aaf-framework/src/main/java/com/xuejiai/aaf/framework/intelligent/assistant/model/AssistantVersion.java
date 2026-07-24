package com.xuejiai.aaf.framework.intelligent.assistant.model;

/** Assistant 定义的单调版本。 */
public record AssistantVersion(long value) implements Comparable<AssistantVersion> {

    public AssistantVersion {
        if (value < 1) {
            throw new IllegalArgumentException("AssistantVersion 必须大于 0");
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
