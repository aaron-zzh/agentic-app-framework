package com.xuejiai.aaf.module.system.vo;

/** 任务列表展示 VO。 */
public record TaskVO(String name, String cronExpression, boolean enabled, String description) {}
