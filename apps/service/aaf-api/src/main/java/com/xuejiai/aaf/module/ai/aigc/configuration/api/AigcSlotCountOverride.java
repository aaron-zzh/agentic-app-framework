package com.xuejiai.aaf.module.ai.aigc.configuration.api;

/** 项目物化时对可重复槽位模板的数量覆盖。 */
public record AigcSlotCountOverride(String templateKey, Integer requestedCount) {}
