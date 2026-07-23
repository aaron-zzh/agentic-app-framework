package com.xuejiai.aaf.framework.crud.runtime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.reference.CrudReferenceTargetAccess;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;

/** 按 Catalog 资源定位统一 CRUD Service 或显式引用目标适配器。 */
@Component
public final class CrudResourceAccessRegistry {

    private final CrudResourceRegistry resourceRegistry;
    private final Map<Class<?>, BaseCrudService<?, ?, ?, ?, ?>> servicesByEntityType;
    private final Map<String, CrudReferenceTargetAccess> targetAccesses;

    public CrudResourceAccessRegistry(
            CrudResourceRegistry resourceRegistry,
            List<BaseCrudService<?, ?, ?, ?, ?>> services,
            List<CrudReferenceTargetAccess> targetAccesses) {
        this.resourceRegistry = resourceRegistry;
        var indexedServices = new LinkedHashMap<Class<?>, BaseCrudService<?, ?, ?, ?, ?>>();
        for (var service : services) {
            var entityType = resolveEntityType(service);
            var previous = indexedServices.putIfAbsent(entityType, service);
            if (previous != null) {
                throw new IllegalStateException("同一实体存在多个 CRUD Service: " + entityType.getName());
            }
        }
        servicesByEntityType = Map.copyOf(indexedServices);
        var indexedTargets = new LinkedHashMap<String, CrudReferenceTargetAccess>();
        for (var access : targetAccesses) {
            var key = access.resourceKey().value();
            if (indexedTargets.putIfAbsent(key, access) != null) {
                throw new IllegalStateException("同一资源存在多个引用目标访问器: " + key);
            }
        }
        this.targetAccesses = Map.copyOf(indexedTargets);
    }

    @EventListener(ContextRefreshedEvent.class)
    public void validateBindings() {
        if (!resourceRegistry.isPublished()) {
            throw new IllegalStateException("CRUD 资源注册表尚未发布，无法校验运行时 Service 目录");
        }
        servicesByEntityType.keySet().forEach(resourceRegistry::requireByEntityType);
        targetAccesses.keySet().stream()
                .map(com.xuejiai.aaf.framework.crud.definition.ResourceKey::of)
                .forEach(resourceRegistry::require);
        resourceRegistry.entries().stream()
                .filter(entry -> entry.snapshot().referenceable())
                .filter(entry -> !hasBinding(entry.key().value(), entry.entityType()))
                .findFirst()
                .ifPresent(
                        entry -> {
                            throw new IllegalStateException(
                                    "可引用 CRUD 资源缺少运行时访问绑定: " + entry.key().value());
                        });
    }

    public Set<ResourceReference> readable(Collection<ResourceReference> references) {
        return filter(references, false);
    }

    public Set<ResourceReference> referenceable(Collection<ResourceReference> references) {
        return filter(references, true);
    }

    public Map<ResourceReference, ResourceRefDTO> loadReadable(
            Collection<ResourceReference> references) {
        var result = new LinkedHashMap<ResourceReference, ResourceRefDTO>();
        groupValid(references)
                .forEach(
                        (resource, ids) ->
                                access(resource)
                                        .loadReadableRefs(ids)
                                        .forEach(
                                                (id, ref) -> {
                                                    var target = new ResourceReference(resource, id);
                                                    result.put(target, ref.withResource(resource));
                                                }));
        return Map.copyOf(result);
    }

    private Set<ResourceReference> filter(
            Collection<ResourceReference> references, boolean referenceable) {
        var result = new LinkedHashSet<ResourceReference>();
        groupValid(references)
                .forEach(
                        (resource, ids) -> {
                            var allowed =
                                    referenceable
                                            ? access(resource).referenceableIds(ids)
                                            : access(resource).readableIds(ids);
                            allowed.forEach(id -> result.add(new ResourceReference(resource, id)));
                        });
        return Set.copyOf(result);
    }

    private Map<String, Set<Long>> groupValid(Collection<ResourceReference> references) {
        var grouped = new LinkedHashMap<String, Set<Long>>();
        if (references == null) {
            return Map.of();
        }
        references.stream()
                .filter(java.util.Objects::nonNull)
                .filter(
                        reference ->
                                reference.resource() != null
                                        && !reference.resource().isBlank()
                                        && reference.id() != null
                                        && reference.id() > 0)
                .forEach(
                        reference ->
                                grouped.computeIfAbsent(
                                                reference.resource(), ignored -> new LinkedHashSet<>())
                                        .add(reference.id()));
        return Map.copyOf(grouped);
    }

    private CrudReferenceTargetAccess access(String resource) {
        var entry =
                resourceRegistry
                        .find(resource)
                        .filter(candidate -> candidate.snapshot().referenceable())
                        .orElseThrow(() -> new UnknownTargetException(resource));
        var explicit = targetAccesses.get(resource);
        if (explicit != null) {
            return explicit;
        }
        var service = servicesByEntityType.get(entry.entityType());
        if (service == null) {
            throw new IllegalStateException("可引用 CRUD 资源缺少运行时访问绑定: " + resource);
        }
        return new BaseCrudTargetAccess(entry.key(), service);
    }

    private boolean hasBinding(String resource, Class<?> entityType) {
        return targetAccesses.containsKey(resource) || servicesByEntityType.containsKey(entityType);
    }

    private Class<?> resolveEntityType(BaseCrudService<?, ?, ?, ?, ?> service) {
        var serviceType = AopUtils.getTargetClass(service);
        var entityType =
                ResolvableType.forClass(serviceType)
                        .as(BaseCrudService.class)
                        .getGeneric(0)
                        .resolve();
        if (entityType == null) {
            throw new IllegalStateException("无法解析 CRUD Service 实体类型: " + serviceType.getName());
        }
        return entityType;
    }

    private record BaseCrudTargetAccess(
            com.xuejiai.aaf.framework.crud.definition.ResourceKey resourceKey,
            BaseCrudService<?, ?, ?, ?, ?> service)
            implements CrudReferenceTargetAccess {

        @Override
        public Set<Long> readableIds(Collection<Long> ids) {
            return service.readableReferenceIds(ids);
        }

        @Override
        public Set<Long> referenceableIds(Collection<Long> ids) {
            return service.referenceableIds(ids);
        }

        @Override
        public Map<Long, ResourceRefDTO> loadReadableRefs(Collection<Long> ids) {
            return service.loadReadableRefs(ids);
        }
    }

    private static final class UnknownTargetException extends RuntimeException {
        private UnknownTargetException(String resource) {
            super("未知或不可引用资源: " + resource);
        }
    }
}
