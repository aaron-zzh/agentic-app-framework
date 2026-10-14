package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 为统一 Assistant 装配当前 Content Studio 项目的紧凑权威上下文。 */
public interface ContentStudioProjectContextProvider {

    ProjectContextSnapshot load(ProjectScope scope, Long focusedObjectId);

    /** 受信项目范围：projectId 与 workspaceId 均来自服务端冻结的执行画像，不接受模型传入。 */
    record ProjectScope(Long projectId, Long workspaceId) {
        public ProjectScope {
            if (projectId == null || projectId < 1) {
                throw new IllegalArgumentException("projectId 必须为正数");
            }
            if (workspaceId == null || workspaceId < 1) {
                throw new IllegalArgumentException("workspaceId 必须为正数");
            }
        }
    }

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
