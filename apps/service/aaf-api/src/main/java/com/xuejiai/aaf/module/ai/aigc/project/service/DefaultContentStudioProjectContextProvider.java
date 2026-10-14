package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationScope;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.ContentStudioProjectContextProvider;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultContentStudioProjectContextProvider
        implements ContentStudioProjectContextProvider {

    private final AigcProjectApi projectApi;
    private final AigcProjectSemanticCatalogCompiler semanticCatalogCompiler;
    private final AigcProjectConfigSnapshotRepository snapshotRepository;

    @Override
    @Transactional(readOnly = true)
    public ProjectContextSnapshot load(ProjectScope scope, Long focusedObjectId) {
        var projectId = scope.projectId();
        var graph = projectApi.getGraph(projectId);
        var project = graph.project();
        if (!scope.workspaceId().equals(project.workspaceId())) {
            throw new IllegalArgumentException("受信 workspace 与项目不一致");
        }
        var snapshot = semanticCatalogCompiler.compile(projectId);
        if (!project.version().equals(snapshot.projectVersion())
                || !graph.revisionNo().equals(snapshot.graphRevision())) {
            throw new IllegalArgumentException("项目语义目录版本与上下文快照不一致");
        }
        var readiness =
                projectApi.readiness(projectId, new AigcGenerationScope(java.util.List.of()));
        var progress = projectApi.progress(projectId);
        if (!project.version().equals(progress.projectVersion())
                || !graph.revisionNo().equals(progress.graphRevision())) {
            throw new IllegalArgumentException("项目进度版本与上下文快照不一致");
        }
        var focused =
                focusedObjectId == null
                        ? null
                        : graph.objects().stream()
                                .filter(object -> focusedObjectId.equals(object.id()))
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "focusedObject 不属于当前项目"));
        var keys = new java.util.LinkedHashSet<String>();
        keys.add("projectType:" + project.projectTypeCode());
        graph.objects().forEach(object -> keys.add("objectType:" + object.objectType()));
        graph.relations().forEach(relation -> keys.add("relationType:" + relation.relationType()));
        var semanticSubset =
                new LinkedHashMap<
                        String,
                        com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectSemanticCatalog
                                .SemanticDefinition>();
        keys.forEach(
                key -> {
                    var definition = snapshot.definitions().get(key);
                    if (definition == null || definition.deprecated()) {
                        throw new IllegalArgumentException("项目使用了未知或废弃语义: " + key);
                    }
                    semanticSubset.put(key, definition);
                });
        var allowedActions =
                readiness.stream()
                        .flatMap(value -> value.allowedActions().stream())
                        .collect(Collectors.toUnmodifiableSet());
        var configSnapshot =
                snapshotRepository
                        .findById(project.configurationSnapshotId())
                        .filter(value -> projectId.equals(value.getProjectId()))
                        .orElseThrow(() -> new IllegalArgumentException("项目配置快照不存在"));
        // ProjectSpecification 直接来自不可变结构快照，不从项目实体拼凑第二份配置事实。
        var specification = configSnapshot.getSnapshot().projectSpecification();
        return new ProjectContextSnapshot(
                projectId,
                project.version(),
                graph.revisionNo(),
                project.lifecycleStage().name(),
                project.generationMode(),
                snapshot.semanticCatalogVersion(),
                specification,
                focused,
                readiness,
                progress,
                semanticSubset,
                allowedActions);
    }
}
