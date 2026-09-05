package com.xuejiai.aaf.module.ai.aigc.project.api;

/** 项目物化结果。 */
public record AigcProjectMaterializeView(
        AigcProjectView project, AigcProjectCoverStatus coverStatus, Long runId) {}
