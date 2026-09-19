package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.AigcCanonicalRequest;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectRelationSemantics;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectSemanticCatalog;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectConfigSnapshotRepository;

import lombok.RequiredArgsConstructor;

/** 从当前项目权威事实即时编译语义目录。 */
@Service
@RequiredArgsConstructor
public class AigcProjectSemanticCatalogCompiler {

    private static final Set<String> CORE_ENUMS =
            Set.of(
                    "contractRole:REQUIRED",
                    "contractRole:OPTIONAL",
                    "contractRole:EXCLUDED",
                    "maturity:EMPTY",
                    "maturity:CANDIDATE_AVAILABLE",
                    "maturity:ADOPTED");

    private final AigcProjectApi projectApi;
    private final AigcProjectConfigSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public AigcProjectSemanticCatalog compile(Long projectId) {
        var graph = projectApi.getGraph(projectId);
        var project = graph.project();
        var snapshot =
                snapshotRepository
                        .findById(project.configurationSnapshotId())
                        .filter(value -> projectId.equals(value.getProjectId()))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "项目语义快照不存在"));
        var definitions =
                new LinkedHashMap<String, AigcProjectSemanticCatalog.SemanticDefinition>();
        CORE_ENUMS.forEach(key -> definitions.put(key, definition(key, "核心枚举值", Set.of())));
        definitions.put(
                "projectType:" + project.projectTypeCode(),
                definition("projectType:" + project.projectTypeCode(), "已发布项目类型", Set.of()));
        snapshot.getSnapshot()
                .objectSpecs()
                .forEach(
                        object -> {
                            var actions =
                                    snapshot.getSnapshot().actions().stream()
                                            .filter(
                                                    action ->
                                                            action.targetTemplateKey() == null
                                                                    || action.targetTemplateKey()
                                                                            .equals(
                                                                                    object
                                                                                            .blueprintTemplateKey()))
                                            .map(action -> action.actionKey())
                                            .collect(java.util.stream.Collectors.toSet());
                            definitions.put(
                                    "objectType:" + object.objectType(),
                                    new AigcProjectSemanticCatalog.SemanticDefinition(
                                            "objectType:" + object.objectType(),
                                            object.displayName(),
                                            object.initialPayloadJson() == null
                                                    ? Map.of()
                                                    : Map.of(
                                                            "initialPayloadJson",
                                                            object.initialPayloadJson()),
                                            Map.of(
                                                    "semanticRole", object.semanticRole(),
                                                    "outputContract", object.outputContract()),
                                            actions,
                                            Map.of("outputContract", object.outputContract()),
                                            false));
                        });
        snapshot.getSnapshot()
                .relationSpecs()
                .forEach(
                        relation -> {
                            var semantics =
                                    AigcProjectRelationSemantics.require(relation.relationType());
                            var key = "relationType:" + relation.relationType();
                            definitions.put(
                                    key,
                                    new AigcProjectSemanticCatalog.SemanticDefinition(
                                            key,
                                            "关系方向固定为来源到结果",
                                            Map.of(),
                                            Map.of(
                                                    "layer", semantics.canonicalLayer(),
                                                    "direction", semantics.direction(),
                                                    "affectsReadiness",
                                                            semantics.affectsReadiness()),
                                            Set.of(),
                                            Map.of(),
                                            false));
                        });
        snapshot.getSnapshot()
                .actions()
                .forEach(
                        action ->
                                definitions.put(
                                        "action:" + action.actionKey(),
                                        definition(
                                                "action:" + action.actionKey(),
                                                "项目动作元数据",
                                                Set.of(action.actionKey()))));
        var canonical =
                Map.of(
                        "projectId", projectId,
                        "projectVersion", project.version(),
                        "graphRevision", graph.revisionNo(),
                        "snapshotHash", snapshot.getSnapshotHash(),
                        "definitions", definitions);
        var version = AigcCanonicalRequest.canonicalSha256(canonical);
        var cacheKey =
                "project-semantic:%d:%d:%d:%s"
                        .formatted(projectId, project.version(), graph.revisionNo(), version);
        return new AigcProjectSemanticCatalog(
                projectId, project.version(), graph.revisionNo(), version, cacheKey, definitions);
    }

    private AigcProjectSemanticCatalog.SemanticDefinition definition(
            String key, String description, Set<String> actions) {
        return new AigcProjectSemanticCatalog.SemanticDefinition(
                key, description, Map.of(), Map.of(), actions, Map.of(), false);
    }
}
