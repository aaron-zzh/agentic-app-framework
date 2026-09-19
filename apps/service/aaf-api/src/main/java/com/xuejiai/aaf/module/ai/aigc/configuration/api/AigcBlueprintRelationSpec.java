package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.Set;

import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectRelationSemantics;

/** 蓝图关系规格及其规范语义快照。 */
public record AigcBlueprintRelationSpec(
        String sourceKey,
        String targetKey,
        String relationType,
        String metadataJson,
        String canonicalLayer,
        String direction,
        Set<String> allowedSourceSemanticRoles,
        Set<String> allowedTargetSemanticRoles,
        boolean acyclic,
        boolean affectsReadiness) {

    public AigcBlueprintRelationSpec {
        allowedSourceSemanticRoles = Set.copyOf(allowedSourceSemanticRoles);
        allowedTargetSemanticRoles = Set.copyOf(allowedTargetSemanticRoles);
    }

    public AigcBlueprintRelationSpec(
            String sourceKey, String targetKey, String relationType, String metadataJson) {
        this(sourceKey, targetKey, relationType, metadataJson, semantics(relationType));
    }

    private AigcBlueprintRelationSpec(
            String sourceKey,
            String targetKey,
            String relationType,
            String metadataJson,
            AigcProjectRelationSemantics.Definition semantics) {
        this(
                sourceKey,
                targetKey,
                relationType,
                metadataJson,
                semantics.canonicalLayer(),
                semantics.direction(),
                semantics.allowedSourceSemanticRoles(),
                semantics.allowedTargetSemanticRoles(),
                semantics.acyclic(),
                semantics.affectsReadiness());
    }

    private static AigcProjectRelationSemantics.Definition semantics(String relationType) {
        return AigcProjectRelationSemantics.require(relationType);
    }
}
