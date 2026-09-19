package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;

/** 为统一 Assistant 装配当前 Content Studio 项目的紧凑权威上下文。 */
public interface ContentStudioProjectContextProvider {

    ProjectContextSnapshot load(
            Long projectId, Long focusedObjectId, InvocationContext invocationContext);

    record ProjectContextSnapshot(
            Long projectId,
            Integer projectVersion,
            Long graphRevision,
            String lifecycle,
            String generationMode,
            String semanticCatalogVersion,
            AigcProjectSpecification projectSpecification,
            AigcProjectObjectView focusedObject,
            List<AigcObjectReadiness> readiness,
            AigcProjectProgressSnapshot progress,
            Map<String, AigcProjectSemanticCatalog.SemanticDefinition> semanticSubset,
            Set<String> allowedActions) {
        public ProjectContextSnapshot {
            projectSpecification =
                    Objects.requireNonNull(projectSpecification, "projectSpecification 不能为空");
            readiness = List.copyOf(readiness);
            semanticSubset = Map.copyOf(semanticSubset);
            allowedActions = Set.copyOf(allowedActions);
        }
    }
}
