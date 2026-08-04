package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;

public record AigcProjectGraphVO(
        AigcProjectVO project,
        Integer graphRevision,
        List<AigcProjectObjectVO> objects,
        List<AigcProjectRelationVO> relations) {

    public AigcProjectGraphVO {
        objects = objects == null ? List.of() : List.copyOf(objects);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }
}
