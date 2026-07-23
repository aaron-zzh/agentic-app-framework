package com.xuejiai.aaf.framework.crud.resource;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import com.xuejiai.aaf.framework.crud.definition.ResourceKey;

/** 进程内已编译资源目录；请求路径仅查询已发布快照。 */
@Component
public final class CrudResourceRegistry {

    private volatile CatalogState state = CatalogState.empty();

    public Optional<CrudResourceCatalogEntry> find(ResourceKey key) {
        return Optional.ofNullable(state.entries().get(key));
    }

    public Optional<CrudResourceCatalogEntry> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            return find(ResourceKey.of(key));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public CrudResourceCatalogEntry require(ResourceKey key) {
        return find(key)
                .orElseThrow(() -> new IllegalStateException("Catalog 不存在资源: " + key.value()));
    }

    public Optional<CrudResourceCatalogEntry> findByController(Class<?> controllerType) {
        return Optional.ofNullable(
                state.entriesByController().get(ClassUtils.getUserClass(controllerType)));
    }

    public CrudResourceCatalogEntry requireByController(Class<?> controllerType) {
        return findByController(controllerType)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Catalog 不存在端点: " + controllerType.getName()));
    }

    public CrudResourceCatalogEntry requireByEntityType(Class<?> entityType) {
        var entry = state.entriesByEntity().get(entityType);
        if (entry == null) {
            throw new IllegalStateException("Catalog 不存在实体资源: " + entityType.getName());
        }
        return entry;
    }

    public List<CrudResourceCatalogEntry> entries() {
        return state.entries().values().stream()
                .sorted(Comparator.comparing(entry -> entry.key().value()))
                .toList();
    }

    public List<CrudResourceSnapshot> snapshots() {
        return entries().stream().map(CrudResourceCatalogEntry::snapshot).toList();
    }

    public boolean isPublished() {
        return state.published();
    }

    void publish(Map<ResourceKey, CrudResourceCatalogEntry> compiledEntries) {
        if (compiledEntries.isEmpty()) {
            throw new IllegalStateException("禁止发布空 CRUD 资源目录");
        }
        var immutableEntries = Map.copyOf(compiledEntries);
        Map<Class<?>, CrudResourceCatalogEntry> byController =
                immutableEntries.values().stream()
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        entry -> (Class<?>) entry.endpointBinding().controllerType(),
                                        entry -> entry));
        Map<Class<?>, CrudResourceCatalogEntry> byEntity =
                immutableEntries.values().stream()
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        entry -> (Class<?>) entry.entityType(), entry -> entry));
        state = new CatalogState(immutableEntries, byController, byEntity, true);
    }

    private record CatalogState(
            Map<ResourceKey, CrudResourceCatalogEntry> entries,
            Map<Class<?>, CrudResourceCatalogEntry> entriesByController,
            Map<Class<?>, CrudResourceCatalogEntry> entriesByEntity,
            boolean published) {

        private static CatalogState empty() {
            return new CatalogState(Map.of(), Map.of(), Map.of(), false);
        }
    }
}
