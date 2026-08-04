package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcProjectGraphView(
        AigcProjectView project,
        List<AigcProjectObjectView> objects,
        List<AigcProjectRelationView> relations,
        Long revisionNo) {

    public AigcProjectGraphView {
        objects = objects == null ? List.of() : List.copyOf(objects);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }
}
