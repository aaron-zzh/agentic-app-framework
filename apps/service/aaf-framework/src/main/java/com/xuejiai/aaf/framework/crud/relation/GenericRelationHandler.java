package com.xuejiai.aaf.framework.crud.relation;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.enforcement.ReferenceEnforcementService;
import com.xuejiai.aaf.framework.crud.reference.ReferenceRequest;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaQuery;
import lombok.RequiredArgsConstructor;

/** 通用纯关联表关系校验、差量同步与父记录删除清理。 */
@Component
@RequiredArgsConstructor
public final class GenericRelationHandler {

    private final ReferenceEnforcementService referenceEnforcementService;

    @PersistenceContext private EntityManager entityManager;

    public void validate(
            ResourceKey sourceResource,
            RelationDefinition<?, ?> relation,
            BaseEntity parent,
            Patch<?> patch) {
        requireWritableManyToMany(relation);
        if (patch.isNullValue() || !(patch.valueOrNull() instanceof List<?> values)) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        var ids = ids(values, relation.maxCardinality());
        var requests =
                ids.stream()
                        .map(
                                id ->
                                        new ReferenceRequest(
                                                parent.getId(),
                                                new ResourceReference(
                                                        relation.targetResource().value(), id)))
                        .toList();
        if (referenceEnforcementService
                        .referenceable(sourceResource, relation.key(), requests)
                        .size()
                != requests.size()) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, "关联记录");
        }
    }

    public void synchronize(RelationDefinition<?, ?> relation, BaseEntity parent, Patch<?> patch) {
        requireWritableManyToMany(relation);
        var requested = ids((List<?>) patch.valueOrNull(), relation.maxCardinality());
        var existingRows = findRows(relation, List.of(parent.getId()));
        var existing = new LinkedHashSet<Long>();
        existingRows.forEach(row -> existing.add(longProperty(row, relation.targetProperty())));

        existingRows.stream()
                .filter(row -> !requested.contains(longProperty(row, relation.targetProperty())))
                .forEach(entityManager::remove);
        requested.stream()
                .filter(id -> !existing.contains(id))
                .map(id -> newAssociation(relation, parent.getId(), id))
                .forEach(entityManager::persist);
    }

    public void cleanupSourceLinks(
            Collection<RelationDefinition<?, ?>> relations, Collection<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return;
        }
        relations.stream()
                .filter(relation -> relation.associationKind() == AssociationKind.MANY_TO_MANY_JOIN)
                .flatMap(relation -> findRows(relation, sourceIds).stream())
                .forEach(entityManager::remove);
    }

    List<?> findRows(RelationDefinition<?, ?> relation, Collection<Long> sourceIds) {
        var builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = builder.createQuery(Object.class);
        var root = query.from(relation.associationEntity());
        query.select(root).where(root.get(relation.sourceProperty()).in(sourceIds));
        return entityManager.createQuery(query).getResultList();
    }

    private Object newAssociation(RelationDefinition<?, ?> relation, Long sourceId, Long targetId) {
        try {
            var entity = relation.associationEntity().getDeclaredConstructor().newInstance();
            var bean = new BeanWrapperImpl(entity);
            bean.setPropertyValue(relation.sourceProperty(), sourceId);
            bean.setPropertyValue(relation.targetProperty(), targetId);
            return entity;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "无法创建关联实体: " + relation.associationEntity().getName(), exception);
        }
    }

    private LinkedHashSet<Long> ids(List<?> values, int maxCardinality) {
        if (values.size() > maxCardinality) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        var ids = new LinkedHashSet<Long>();
        for (var value : values) {
            if (!(value instanceof Long id) || id <= 0 || !ids.add(id)) {
                throw exception(GlobalErrorCode.BAD_REQUEST);
            }
        }
        return ids;
    }

    private Long longProperty(Object row, String property) {
        var value = new BeanWrapperImpl(row).getPropertyValue(property);
        if (!(value instanceof Long id)) {
            throw new IllegalStateException("关联属性不是 Long: " + property);
        }
        return id;
    }

    private void requireWritableManyToMany(RelationDefinition<?, ?> relation) {
        if (relation.associationKind() != AssociationKind.MANY_TO_MANY_JOIN
                || relation.syncMode() == RelationDefinition.SyncMode.READ_ONLY) {
            throw new IllegalStateException("关系不支持通用写入: " + relation.key());
        }
    }
}
