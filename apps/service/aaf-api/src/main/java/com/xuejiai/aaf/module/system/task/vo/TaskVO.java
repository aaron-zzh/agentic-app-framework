package com.xuejiai.aaf.module.system.task.vo;

/** 任务列表展示 VO。 */
public record TaskVO(String name, String cronExpression, boolean enabled, String description) {}
