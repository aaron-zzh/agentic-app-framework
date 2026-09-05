package com.xuejiai.aaf.module.ai.aigc.project.event;

/** 项目作废仍在执行的封面请求。 */
public record AigcProjectCoverSupersededEvent(Long projectId, String reason) {}
