package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从项目权威配置编译的只读语义目录，不拥有独立持久化事实。 */
public record AigcProjectSemanticCatalog(
        Long projectId,
        Integer projectVersion,
        Long graphRevision,
        String semanticCatalogVersion,
        String cacheInvalidationKey,
        Map<String, SemanticDefinition> definitions) {

    public AigcProjectSemanticCatalog {
        definitions = Map.copyOf(definitions);
    }

    public record SemanticDefinition(
            String key,
            String definition,
            Map<String, Object> schema,
            Map<String, Object> constraints,
            Set<String> allowedActions,
            Map<String, Object> completionEvidence,
            boolean deprecated) {
        public SemanticDefinition {
            schema = schema == null ? Map.of() : Map.copyOf(schema);
            constraints = constraints == null ? Map.of() : Map.copyOf(constraints);
            allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
            completionEvidence =
                    completionEvidence == null ? Map.of() : Map.copyOf(completionEvidence);
        }
    }

    public List<SemanticDefinition> requireDefinitions(
            String expectedVersion, Set<String> semanticKeys) {
        if (!semanticCatalogVersion.equals(expectedVersion)) {
            throw new IllegalArgumentException("semanticCatalogVersion 已失效");
        }
        return semanticKeys.stream()
                .map(
                        key -> {
                            var definition = definitions.get(key);
                            if (definition == null || definition.deprecated()) {
                                throw new IllegalArgumentException("未知或已废弃的项目语义: " + key);
                            }
                            return definition;
                        })
                .toList();
    }
}
