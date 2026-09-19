package com.xuejiai.aaf.module.ai.aigc.project.api;

/** 在同一项目图中移动对象。 */
public record AigcProjectObjectMoveCommand(
        Long projectId,
        Long objectId,
        Long newParentObjectId,
        Integer orderNo,
        Integer expectedProjectVersion,
        Long expectedGraphRevision) {}
