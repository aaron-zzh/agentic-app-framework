package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.AigcCanonicalRequest;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcResolvedObjectSpec;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectRelationSemantics;

import tools.jackson.core.type.TypeReference;

/**
 * BlueprintSnapshot 两来源共享校验与 canonical 哈希计算。
 *
 * <p>纯函数校验，不做持久化；CATALOG 与 ASSISTANT_GENERATED 必须复用同一语义合同。
 */
@Component
public class BlueprintSnapshotValidator {

    /** 校验两种合法来源的 BlueprintSnapshot 结构完整性与语义合法性。 */
    public void validate(AigcProjectBlueprintSnapshotPayload snapshot) {
        if (snapshot.sourceType() == null) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot sourceType 不能为空");
        }
        if (snapshot.snapshotVersion() != 2) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot 版本必须为 2");
        }
        if (snapshot.processPolicy() == null) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot processPolicy 不能为空");
        }
        if (snapshot.sourceRef() == null || snapshot.sourceRef().isBlank()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot sourceRef 不能为空");
        }
        var validSourceRef =
                switch (snapshot.sourceType()) {
                    case CATALOG -> snapshot.sourceRef().startsWith("blueprint:");
                    case ASSISTANT_GENERATED ->
                            snapshot.sourceRef().matches("assistant-proposal:[^@]+@[1-9][0-9]*");
                };
        if (!validSourceRef) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot sourceRef 与来源不匹配");
        }
        var projectSpecification = snapshot.projectSpecification();
        if (projectSpecification.projectTypeCode() == null
                || projectSpecification.projectTypeCode().isBlank()
                || projectSpecification.projectTypeVersion() == null
                || projectSpecification.projectTypeVersion().isBlank()
                || projectSpecification.productionMode() == null
                || projectSpecification.productionMode().isBlank()
                || snapshot.objectSpecs().isEmpty()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot 缺少项目规格或对象规格");
        }
        var objectsByKey = new LinkedHashMap<String, AigcResolvedObjectSpec>();
        for (var object : snapshot.objectSpecs()) {
            if (object == null) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot objectSpec 不能为空");
            }
            if (object.stableKey() == null
                    || object.stableKey().isBlank()
                    || objectsByKey.putIfAbsent(object.stableKey(), object) != null) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot object localKey 为空或重复");
            }
            requireSnapshotSemantic(object);
        }
        validateParentGraph(objectsByKey);
        var contracts = new LinkedHashMap<String, String>();
        for (var contract : snapshot.outputContracts()) {
            if (contract == null
                    || contract.outputContract() == null
                    || contract.outputContract().isBlank()) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot outputContract 不能为空");
            }
            if (contract.localKey() == null
                    || contracts.putIfAbsent(contract.localKey(), contract.outputContract())
                            != null) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST,
                        "BlueprintSnapshot outputContract localKey 重复");
            }
        }
        if (contracts.size() != objectsByKey.size()
                || objectsByKey.values().stream()
                        .anyMatch(
                                object ->
                                        !object.outputContract()
                                                .equals(contracts.get(object.stableKey())))) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "BlueprintSnapshot outputContracts 与 objectSpecs 不一致");
        }
        var relationKeys = new LinkedHashSet<List<String>>();
        var acyclicTargets = new LinkedHashMap<String, List<String>>();
        for (var relation : snapshot.relationSpecs()) {
            if (relation == null
                    || relation.relationType() == null
                    || relation.relationType().isBlank()) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot relationType 不能为空");
            }
            if ("contains".equals(relation.relationType())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "contains 必须由 parentId 派生，不能持久化");
            }
            var source = objectsByKey.get(relation.sourceKey());
            var target = objectsByKey.get(relation.targetKey());
            if (source == null || target == null || source.stableKey().equals(target.stableKey())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot 关系端点不存在或指向自身");
            }
            var relationKey =
                    List.of(relation.sourceKey(), relation.targetKey(), relation.relationType());
            if (!relationKeys.add(relationKey)) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot 存在重复关系");
            }
            final AigcProjectRelationSemantics.Definition semantics;
            try {
                semantics = AigcProjectRelationSemantics.require(relation.relationType());
            } catch (IllegalArgumentException error) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, error.getMessage());
            }
            if (!semantics.configurationAllowed()
                    || !semantics.canonicalLayer().equals(relation.canonicalLayer())
                    || !semantics.direction().equals(relation.direction())
                    || semantics.acyclic() != relation.acyclic()
                    || semantics.affectsReadiness() != relation.affectsReadiness()
                    || !semantics.allowedSourceSemanticRoles().contains(source.semanticRole())
                    || !semantics.allowedTargetSemanticRoles().contains(target.semanticRole())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST,
                        "BlueprintSnapshot relationType 规范语义不一致: " + relation.relationType());
            }
            if (semantics.acyclic()) {
                acyclicTargets
                        .computeIfAbsent(relation.sourceKey(), ignored -> new ArrayList<>())
                        .add(relation.targetKey());
            }
        }
        validateAcyclicRelations(objectsByKey.keySet(), acyclicTargets);
        validateProjectInitialization(snapshot.projectInitialization());
        validateActions(snapshot, objectsByKey);
        validateDeliverableSets(snapshot, objectsByKey);
        validateProcessPolicy(snapshot);
    }

    private void validateProjectInitialization(
            com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcProjectInitializationSpec
                    initialization) {
        var documentKeys = new LinkedHashSet<String>();
        for (var document : initialization.documents()) {
            if (document == null || !documentKeys.add(document.key())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST,
                        "BlueprintSnapshot projectInitialization 文档键为空或重复");
            }
            if (!Set.of("PROJECT_DOCUMENT", "EXTERNAL_REFERENCE")
                    .contains(document.referenceType())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST,
                        "BlueprintSnapshot projectInitialization referenceType 非法");
            }
        }
    }

    private void validateActions(
            AigcProjectBlueprintSnapshotPayload snapshot,
            Map<String, AigcResolvedObjectSpec> objectsByKey) {
        var templates =
                objectsByKey.values().stream()
                        .map(AigcResolvedObjectSpec::blueprintTemplateKey)
                        .collect(java.util.stream.Collectors.toSet());
        var contextKeys = new LinkedHashSet<String>();
        snapshot.projectInitialization().fields().stream()
                .map(
                        com.xuejiai.aaf.module.ai.aigc.configuration.api
                                        .AigcProjectInitializationSpec.FieldSpec
                                ::key)
                .forEach(contextKeys::add);
        snapshot.projectInitialization().documents().stream()
                .map(
                        com.xuejiai.aaf.module.ai.aigc.configuration.api
                                        .AigcProjectInitializationSpec.DocumentSpec
                                ::key)
                .forEach(contextKeys::add);
        contextKeys.addAll(objectsByKey.keySet());
        contextKeys.addAll(templates);
        var actionsByKey =
                new LinkedHashMap<
                        String,
                        com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcBlueprintActionSpec>();
        var defaultByTarget = new LinkedHashMap<String, String>();
        for (var action : snapshot.actions()) {
            if (action == null
                    || action.actionKey() == null
                    || action.actionKey().isBlank()
                    || actionsByKey.putIfAbsent(action.actionKey(), action) != null
                    || !templates.contains(action.targetTemplateKey())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot actionKey 重复或目标不存在");
            }
            if (!Set.of("REQUIRED", "OPTIONAL").contains(action.confirmationPolicy())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST,
                        "BlueprintSnapshot action confirmationPolicy 非法");
            }
            if (!templates.containsAll(action.dependencyTemplateKeys())
                    || !contextKeys.containsAll(action.requiredContextKeys())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot action 依赖或上下文键不存在");
            }
            if (action.isDefault()
                    && defaultByTarget.putIfAbsent(action.targetTemplateKey(), action.actionKey())
                            != null) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot 同一对象模板只能有一个默认动作");
            }
        }
        var bindingActions =
                snapshot.projectSpecification().executionBindings().stream()
                        .map(
                                com.xuejiai.aaf.module.ai.aigc.configuration.api
                                                .AigcExecutionBindingVersionRef
                                        ::actionKey)
                        .toList();
        if (bindingActions.stream().distinct().count() != bindingActions.size()
                || !new LinkedHashSet<>(bindingActions).equals(actionsByKey.keySet())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "BlueprintSnapshot executionBindings 必须与 actions 一一对应");
        }
        for (var object : objectsByKey.values()) {
            if (object.defaultActionKey() != null
                    && !Objects.equals(
                            object.defaultActionKey(),
                            defaultByTarget.get(object.blueprintTemplateKey()))) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST,
                        "BlueprintSnapshot object defaultActionKey 与默认动作不一致");
            }
        }
    }

    private void validateDeliverableSets(
            AigcProjectBlueprintSnapshotPayload snapshot,
            Map<String, AigcResolvedObjectSpec> objectsByKey) {
        var templates =
                objectsByKey.values().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcResolvedObjectSpec::blueprintTemplateKey,
                                        java.util.function.Function.identity(),
                                        (left, ignored) -> left));
        var setKeys = new LinkedHashSet<String>();
        for (var deliverableSet : snapshot.deliverableSets()) {
            var root = templates.get(deliverableSet.setTemplateKey());
            if (root == null
                    || !"COMPOSITE_MANIFEST".equals(root.outputContract())
                    || !setKeys.add(deliverableSet.setTemplateKey())
                    || !templates.keySet().containsAll(deliverableSet.allowedSlotTemplateKeys())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot DeliverableSet 根或槽位非法");
            }
        }
    }

    private void validateProcessPolicy(AigcProjectBlueprintSnapshotPayload snapshot) {
        var actionKeys =
                snapshot.actions().stream()
                        .map(
                                com.xuejiai.aaf.module.ai.aigc.configuration.api
                                                .AigcBlueprintActionSpec
                                        ::actionKey)
                        .collect(java.util.stream.Collectors.toSet());
        if (!actionKeys.containsAll(snapshot.processPolicy().autoActionKeys())
                || !actionKeys.containsAll(snapshot.processPolicy().confirmationGateKeys())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot ProcessPolicy 引用了未知动作");
        }
    }

    /** 计算 BlueprintSnapshot 的 canonical SHA-256 摘要，供 insert-only 校验和重放比对使用。 */
    public String canonicalHash(AigcProjectBlueprintSnapshotPayload snapshot) {
        var canonicalMap = parseMap(JsonUtils.toJsonString(snapshot));
        return AigcCanonicalRequest.canonicalSha256(canonicalMap);
    }

    private void requireSnapshotSemantic(AigcResolvedObjectSpec object) {
        if (object.objectType() == null || object.objectType().isBlank()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot objectType 不能为空");
        }
        if (!Set.of("REQUIRED", "OPTIONAL", "EXCLUDED").contains(object.contractRole())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot contractRole 非法");
        }
        if (object.adoptionPolicy() == null || object.adoptionPolicy().isBlank()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot adoptionPolicy 不能为空");
        }
        if (!Set.of("STRUCTURE", "WORKING_ARTIFACT", "DELIVERABLE", "EVIDENCE", "REFERENCE")
                .contains(object.semanticRole())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot semanticRole 非法");
        }
        if (!Set.of(
                        "INLINE_OBJECT_VERSION",
                        "DOCUMENT_VERSION",
                        "MEDIA_VERSION",
                        "COMPOSITE_MANIFEST",
                        "REFERENCE_ONLY")
                .contains(object.outputContract())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot outputContract 非法");
        }
    }

    private void validateParentGraph(Map<String, AigcResolvedObjectSpec> objectsByKey) {
        for (var object : objectsByKey.values()) {
            var visited = new LinkedHashSet<String>();
            var current = object;
            while (current.parentKey() != null) {
                if (!visited.add(current.stableKey())) {
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot 对象父级存在环");
                }
                current = objectsByKey.get(current.parentKey());
                if (current == null) {
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot 对象父级不存在");
                }
            }
        }
    }

    private void validateAcyclicRelations(
            Set<String> objectKeys, Map<String, List<String>> targetsBySource) {
        var visited = new LinkedHashSet<String>();
        var visiting = new LinkedHashSet<String>();
        for (var objectKey : objectKeys) {
            if (relationCycle(objectKey, targetsBySource, visited, visiting)) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "BlueprintSnapshot 规范关系存在有向环");
            }
        }
    }

    private boolean relationCycle(
            String objectKey,
            Map<String, List<String>> targetsBySource,
            Set<String> visited,
            Set<String> visiting) {
        if (visiting.contains(objectKey)) {
            return true;
        }
        if (visited.contains(objectKey)) {
            return false;
        }
        visiting.add(objectKey);
        for (var target : targetsBySource.getOrDefault(objectKey, List.of())) {
            if (relationCycle(target, targetsBySource, visited, visiting)) {
                return true;
            }
        }
        visiting.remove(objectKey);
        visited.add(objectKey);
        return false;
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
    }
}
