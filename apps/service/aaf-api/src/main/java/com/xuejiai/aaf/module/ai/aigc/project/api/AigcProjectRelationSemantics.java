package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.Map;
import java.util.Set;

/** ProjectGraph 关系语义目录，配置与客户端不能自定义规范语义。 */
public final class AigcProjectRelationSemantics {

    private static final Set<String> ALL_ROLES =
            Set.of("STRUCTURE", "WORKING_ARTIFACT", "DELIVERABLE", "EVIDENCE", "REFERENCE");

    private static final Map<String, Definition> DEFINITIONS =
            Map.ofEntries(
                    Map.entry(
                            "contains",
                            new Definition(
                                    "domain",
                                    "SOURCE_TO_TARGET",
                                    Set.of("STRUCTURE", "DELIVERABLE"),
                                    ALL_ROLES,
                                    true,
                                    false,
                                    false)),
                    Map.entry(
                            "derives",
                            new Definition(
                                    "domain",
                                    "SOURCE_TO_TARGET",
                                    ALL_ROLES,
                                    Set.of("WORKING_ARTIFACT", "DELIVERABLE"),
                                    true,
                                    true,
                                    true)),
                    Map.entry(
                            "constrains",
                            new Definition(
                                    "domain",
                                    "SOURCE_TO_TARGET",
                                    Set.of("STRUCTURE", "WORKING_ARTIFACT", "REFERENCE"),
                                    Set.of("WORKING_ARTIFACT", "DELIVERABLE"),
                                    true,
                                    true,
                                    true)),
                    Map.entry(
                            "composes",
                            new Definition(
                                    "domain",
                                    "SOURCE_TO_TARGET",
                                    Set.of("DELIVERABLE"),
                                    Set.of("DELIVERABLE"),
                                    true,
                                    false,
                                    true)),
                    Map.entry(
                            "variant",
                            new Definition(
                                    "domain",
                                    "SOURCE_TO_TARGET",
                                    Set.of("DELIVERABLE"),
                                    Set.of("DELIVERABLE"),
                                    true,
                                    false,
                                    true)),
                    Map.entry(
                            "references",
                            new Definition(
                                    "reference",
                                    "SOURCE_TO_TARGET",
                                    ALL_ROLES,
                                    Set.of("REFERENCE"),
                                    false,
                                    false,
                                    true)),
                    Map.entry(
                            "orders",
                            new Definition(
                                    "story_order",
                                    "SOURCE_TO_TARGET",
                                    ALL_ROLES,
                                    ALL_ROLES,
                                    true,
                                    false,
                                    true)),
                    Map.entry(
                            "execution_depends",
                            new Definition(
                                    "execution",
                                    "SOURCE_TO_TARGET",
                                    ALL_ROLES,
                                    ALL_ROLES,
                                    true,
                                    true,
                                    true)));

    private AigcProjectRelationSemantics() {}

    public static Definition require(String relationType) {
        var definition = DEFINITIONS.get(relationType);
        if (definition == null) {
            throw new IllegalArgumentException("未注册 relationType: " + relationType);
        }
        return definition;
    }

    public static String canonicalLayer(String relationType) {
        return require(relationType).canonicalLayer();
    }

    public record Definition(
            String canonicalLayer,
            String direction,
            Set<String> allowedSourceSemanticRoles,
            Set<String> allowedTargetSemanticRoles,
            boolean acyclic,
            boolean affectsReadiness,
            boolean configurationAllowed) {

        public Definition {
            allowedSourceSemanticRoles = Set.copyOf(allowedSourceSemanticRoles);
            allowedTargetSemanticRoles = Set.copyOf(allowedTargetSemanticRoles);
        }
    }
}
