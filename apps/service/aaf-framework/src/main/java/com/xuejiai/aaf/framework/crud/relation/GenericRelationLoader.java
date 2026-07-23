package com.xuejiai.aaf.framework.crud.relation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.enforcement.ReferenceEnforcementService;
import com.xuejiai.aaf.framework.crud.reference.CrudReferenceDefinition;
import com.xuejiai.aaf.framework.crud.reference.ReferenceRequest;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;

import lombok.RequiredArgsConstructor;

/** 固定、多态、M2M 与只读 O2M 的通用批量视图 Loader。 */
@Component
@RequiredArgsConstructor
public final class GenericRelationLoader {

    private final GenericRelationHandler relationHandler;
    private final ReferenceEnforcementService referenceEnforcementService;

    public Map<Long, ResourceRefDTO> loadReference(
            ResourceKey sourceResource,
            CrudReferenceDefinition reference,
            Collection<? extends BaseEntity> parents) {
        var requests = new ArrayList<ReferenceRequest>();
        for (var parent : parents) {
            var bean = new BeanWrapperImpl(parent);
            var id = bean.getPropertyValue(reference.idProperty());
            var resource =
                    reference.polymorphic()
                            ? bean.getPropertyValue(reference.resourceProperty())
                            : reference.targetResource().value();
            if (id == null && resource == null) {
                continue;
            }
            if (!(id instanceof Long targetId)
                    || !(resource instanceof String targetResource)
                    || targetId <= 0
                    || targetResource.isBlank()) {
                continue;
            }
            requests.add(
                    new ReferenceRequest(
                            parent.getId(), new ResourceReference(targetResource, targetId)));
        }
        var loaded =
                referenceEnforcementService.resolveReadable(
                        sourceResource, reference.key(), requests);
        var result = new LinkedHashMap<Long, ResourceRefDTO>();
        loaded.forEach((request, ref) -> result.put(request.sourceId(), ref));
        return Map.copyOf(result);
    }

    public Map<Long, List<ResourceRefDTO>> loadRelation(
            ResourceKey sourceResource,
            RelationDefinition<?, ?> relation,
            Collection<? extends BaseEntity> parents) {
        var parentIds = parents.stream().map(BaseEntity::getId).toList();
        var targetsByParent = new LinkedHashMap<Long, List<Long>>();
        for (var row : relationHandler.findRows(relation, parentIds)) {
            var bean = new BeanWrapperImpl(row);
            var sourceId = longProperty(bean, relation.sourceProperty());
            var targetId = longProperty(bean, relation.targetProperty());
            targetsByParent.computeIfAbsent(sourceId, ignored -> new ArrayList<>()).add(targetId);
        }
        var requests = new ArrayList<ReferenceRequest>();
        targetsByParent.forEach(
                (sourceId, targetIds) ->
                        targetIds.forEach(
                                targetId ->
                                        requests.add(
                                                new ReferenceRequest(
                                                        sourceId,
                                                        new ResourceReference(
                                                                relation.targetResource().value(),
                                                                targetId)))));
        var loaded =
                referenceEnforcementService.resolveReadable(
                        sourceResource, relation.key(), requests);
        var result = new LinkedHashMap<Long, List<ResourceRefDTO>>();
        parentIds.forEach(parentId -> result.put(parentId, List.of()));
        var mutable = new LinkedHashMap<Long, List<ResourceRefDTO>>();
        loaded.forEach(
                (request, ref) ->
                        mutable.computeIfAbsent(request.sourceId(), ignored -> new ArrayList<>())
                                .add(ref));
        mutable.forEach((parentId, refs) -> result.put(parentId, List.copyOf(refs)));
        return Map.copyOf(result);
    }

    private Long longProperty(BeanWrapperImpl bean, String property) {
        var value = bean.getPropertyValue(property);
        if (!(value instanceof Long id)) {
            throw new IllegalStateException("关联属性不是 Long: " + property);
        }
        return id;
    }
}
