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

    /**
     * 协调者披露策略：与执行者同级，允许内容级披露。
     *
     * <p>协调者要自己直答或按步骤执行，必须能读取完整内容；放宽的代价是协调者直接暴露在原始内容与附件的提示注入面前， 由工具走 ToolGateway 授权、计划校验不扩大
     * Role/Skill、执行者数量与并行度预算、CompletionValidator 判定完成兜底（ADR-008）。
     */
    public static ContextDisclosurePolicy coordinator() {
        return new ContextDisclosurePolicy(
                Audience.COORDINATOR, DetailLevel.MINIMAL, EnumSet.allOf(SourceType.class), 16);
    }

    public static ContextDisclosurePolicy aggregatorSummary() {
        return new ContextDisclosurePolicy(
                Audience.AGGREGATOR, DetailLevel.SUMMARY_ONLY, EnumSet.allOf(SourceType.class), 8);
    }

    public static ContextDisclosurePolicy executorMinimal() {
        return new ContextDisclosurePolicy(
                Audience.EXECUTOR, DetailLevel.MINIMAL, EnumSet.allOf(SourceType.class), 16);
    }

    public enum Audience {
        COORDINATOR,
        AGGREGATOR,
        EXECUTOR
    }

    public enum DetailLevel {
        SUMMARY_ONLY,
        MINIMAL
    }
}
