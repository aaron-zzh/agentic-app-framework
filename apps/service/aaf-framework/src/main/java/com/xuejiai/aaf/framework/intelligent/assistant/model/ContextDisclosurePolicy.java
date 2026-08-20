package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceType;

/** 执行画像冻结的上下文披露策略；只声明摘要引用范围，不携带原始上下文正文。 */
public record ContextDisclosurePolicy(
        Audience audience,
        DetailLevel detailLevel,
        Set<SourceType> allowedSourceTypes,
        int maxSources) {

    public ContextDisclosurePolicy {
        Objects.requireNonNull(audience, "audience 不能为空");
        Objects.requireNonNull(detailLevel, "detailLevel 不能为空");
        allowedSourceTypes =
                Set.copyOf(Objects.requireNonNull(allowedSourceTypes, "allowedSourceTypes 不能为空"));
        if (allowedSourceTypes.isEmpty()) {
            throw new IllegalArgumentException("allowedSourceTypes 不能为空");
        }
        if (maxSources < 1 || maxSources > 32) {
            throw new IllegalArgumentException("maxSources 必须在 1..32");
        }
    }

    public static ContextDisclosurePolicy coordinatorSummary() {
        return summary(Audience.COORDINATOR);
    }

    public static ContextDisclosurePolicy aggregatorSummary() {
        return summary(Audience.AGGREGATOR);
    }

    private static ContextDisclosurePolicy summary(Audience audience) {
        return new ContextDisclosurePolicy(
                audience, DetailLevel.SUMMARY_ONLY, EnumSet.allOf(SourceType.class), 8);
    }

    public static ContextDisclosurePolicy executorMinimal() {
        return new ContextDisclosurePolicy(
                Audience.EXECUTOR, DetailLevel.MINIMAL, EnumSet.allOf(SourceType.class), 16);
    }

    public static ContextDisclosurePolicy primaryMinimal() {
        return new ContextDisclosurePolicy(
                Audience.PRIMARY, DetailLevel.MINIMAL, EnumSet.allOf(SourceType.class), 16);
    }

    public enum Audience {
        PRIMARY,
        COORDINATOR,
        AGGREGATOR,
        EXECUTOR
    }

    public enum DetailLevel {
        SUMMARY_ONLY,
        MINIMAL
    }
}
