package com.xuejiai.aaf.module.ai.aigc.project.api;

/** Project 聚合正式生命周期。持久化与 API 均使用枚举名的大写值。 */
public enum AigcProjectLifecycle {
    CONFIGURING,
    MATERIALIZED,
    CREATING,
    EXECUTING,
    ADOPTING,
    REVIEWING,
    DELIVERING,
    COMPLETED,
    ARCHIVED;

    public boolean allowsCreativeMutation() {
        return this == CREATING || this == EXECUTING || this == ADOPTING;
    }
}
