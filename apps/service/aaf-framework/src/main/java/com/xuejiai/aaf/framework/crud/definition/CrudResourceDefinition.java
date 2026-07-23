package com.xuejiai.aaf.framework.crud.definition;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.reference.CrudReferenceDefinition;
import com.xuejiai.aaf.framework.crud.relation.RelationDefinition;

/**
 * CRUD 资源静态定义的聚合入口。
 *
 * <p>统一声明资源身份、实体和请求/视图类型契约、可执行能力、查询与变更规则、字段能力、引用与关系、
 * 租户及个人数据范围，以及允许被受信任消费者发现的暴露面。
 *
 * <p>该定义描述服务端允许的能力上限。资源编译和运行时访问必须以此为准，业务代码不得绕过它手工放宽
 * 能力或访问范围。
 */
public record CrudResourceDefinition<E extends BaseEntity>(
        ResourceKey key,
        CrudResourceTypeContract<E> types,
        CrudResourceDescriptor descriptor,
        CrudCapabilityDefinition capabilities,
        CrudQueryDefinition<E> query,
        CrudMutationDefinition mutation,
        CrudViewDefinition view,
        Map<String, Set<FieldCapability>> fieldCapabilities,
        List<CrudReferenceDefinition> references,
        List<RelationDefinition<?, ?>> relations,
        TenantScope tenantScope,
        PersonalScope personalScope,
        Set<CrudResourceExposure> exposures,
        int schemaVersion) {

    public static final int CURRENT_SCHEMA_VERSION = 4;

    public CrudResourceDefinition {
        key = Objects.requireNonNull(key, "key");
        types = Objects.requireNonNull(types, "types");
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        capabilities = Objects.requireNonNull(capabilities, "capabilities");
        query = Objects.requireNonNull(query, "query");
        mutation = Objects.requireNonNull(mutation, "mutation");
        view = Objects.requireNonNull(view, "view");
        fieldCapabilities = copyFieldCapabilities(fieldCapabilities);
        references = List.copyOf(Objects.requireNonNull(references, "references"));
        if (references.stream().map(CrudReferenceDefinition::key).distinct().count()
                != references.size()) {
            throw new IllegalArgumentException("资源引用字段不能重复");
        }
        relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
        if (relations.stream().map(RelationDefinition::key).distinct().count()
                != relations.size()) {
            throw new IllegalArgumentException("资源关系 key 不能重复");
        }
        tenantScope = Objects.requireNonNull(tenantScope, "tenantScope");
        personalScope = Objects.requireNonNull(personalScope, "personalScope");
        exposures = Set.copyOf(Objects.requireNonNull(exposures, "exposures"));
        if (exposures.isEmpty()) {
            throw new IllegalArgumentException("资源暴露面不能为空");
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的资源 schemaVersion: " + schemaVersion);
        }
    }

    /** 未定制字段上限的代码资源按已声明的输入、输出和查询契约生成默认能力。 */
    public CrudResourceDefinition(
            ResourceKey key,
            CrudResourceTypeContract<E> types,
            CrudResourceDescriptor descriptor,
            CrudCapabilityDefinition capabilities,
            CrudQueryDefinition<E> query,
            CrudMutationDefinition mutation,
            CrudViewDefinition view,
            List<RelationDefinition<?, ?>> relations,
            TenantScope tenantScope,
            PersonalScope personalScope,
            Set<CrudResourceExposure> exposures,
            int schemaVersion) {
        this(
                key,
                types,
                descriptor,
                capabilities,
                query,
                mutation,
                view,
                inferFieldCapabilities(query, mutation, view),
                List.of(),
                relations,
                tenantScope,
                personalScope,
                exposures,
                schemaVersion);
    }

    /** 无关系扩展的资源使用由 API 类型推导出的显式 Mutation/View 契约。 */
    public CrudResourceDefinition(
            ResourceKey key,
            CrudResourceTypeContract<E> types,
            CrudResourceDescriptor descriptor,
            CrudCapabilityDefinition capabilities,
            CrudQueryDefinition<E> query,
            TenantScope tenantScope,
            PersonalScope personalScope,
            Set<CrudResourceExposure> exposures,
            int schemaVersion) {
        this(
                key,
                types,
                descriptor,
                capabilities,
                query,
                CrudMutationDefinition.forTypes(types),
                CrudViewDefinition.forTypes(types),
                List.of(),
                tenantScope,
                personalScope,
                exposures,
                schemaVersion);
    }

    /** 使用默认暴露面和推导字段能力创建标准 CRUD 资源定义。 */
    public static <E extends BaseEntity> CrudResourceDefinition<E> standard(
            ResourceKey key,
            CrudResourceTypeContract<E> types,
            CrudResourceDescriptor descriptor,
            CrudCapabilityDefinition capabilities,
            CrudQueryDefinition<E> query,
            CrudMutationDefinition mutation,
            CrudViewDefinition view,
            TenantScope tenantScope,
            PersonalScope personalScope) {
        return new CrudResourceDefinition<>(
                key,
                types,
                descriptor,
                capabilities,
                query,
                mutation,
                view,
                List.of(),
                tenantScope,
                personalScope,
                Set.of(
                        CrudResourceExposure.HTTP,
                        CrudResourceExposure.ENTITY_DEF,
                        CrudResourceExposure.REFERENCE),
                CURRENT_SCHEMA_VERSION);
    }

    /** 返回叠加具名自定义 UPDATE 命令后的资源定义，不改变标准 HTTP CRUD 能力列表。 */
    public CrudResourceDefinition<E> withCustomUpdateCommands(
            Map<String, Set<String>> customUpdateCommands) {
        var nextMutation = mutation.withCustomUpdateCommands(customUpdateCommands);
        var nextFieldCapabilities = new LinkedHashMap<String, Set<FieldCapability>>();
        fieldCapabilities.forEach(
                (field, capabilities) ->
                        nextFieldCapabilities.put(field, new LinkedHashSet<>(capabilities)));
        nextMutation
                .customUpdateFields()
                .forEach(
                        field ->
                                nextFieldCapabilities
                                        .computeIfAbsent(field, ignored -> new LinkedHashSet<>())
                                        .add(FieldCapability.WRITE));
        var immutableCapabilities = new LinkedHashMap<String, Set<FieldCapability>>();
        nextFieldCapabilities.forEach(
                (field, capabilities) ->
                        immutableCapabilities.put(field, Set.copyOf(capabilities)));
        return new CrudResourceDefinition<>(
                key,
                types,
                descriptor,
                capabilities,
                query,
                nextMutation,
                view,
                immutableCapabilities,
                references,
                relations,
                tenantScope,
                personalScope,
                exposures,
                schemaVersion);
    }

    private static Map<String, Set<FieldCapability>> copyFieldCapabilities(
            Map<String, Set<FieldCapability>> capabilities) {
        var copied = new LinkedHashMap<String, Set<FieldCapability>>();
        Objects.requireNonNull(capabilities, "fieldCapabilities")
                .forEach(
                        (field, values) -> {
                            if (field == null || field.isBlank()) {
                                throw new IllegalArgumentException("字段能力名称不能为空");
                            }
                            copied.put(
                                    field.trim(),
                                    Set.copyOf(Objects.requireNonNull(values, field)));
                        });
        return Map.copyOf(copied);
    }

    private static Map<String, Set<FieldCapability>> inferFieldCapabilities(
            CrudQueryDefinition<?> query,
            CrudMutationDefinition mutation,
            CrudViewDefinition view) {
        var inferred = new LinkedHashMap<String, EnumSet<FieldCapability>>();
        grant(
                inferred,
                view.fieldSets().values().stream().flatMap(Set::stream).toList(),
                FieldCapability.READ);
        grant(inferred, view.fieldSets().getOrDefault("export", Set.of()), FieldCapability.EXPORT);
        grant(inferred, mutation.createFields(), FieldCapability.WRITE);
        grant(inferred, mutation.updateFields(), FieldCapability.WRITE);
        grant(inferred, mutation.customUpdateFields(), FieldCapability.WRITE);
        grant(
                inferred,
                mutation.relationFields().values(),
                FieldCapability.WRITE,
                FieldCapability.REFERENCE);
        grant(
                inferred,
                query.filterSchema().metas().stream().map(meta -> meta.field()).toList(),
                FieldCapability.FILTER,
                FieldCapability.AGGREGATE);
        grant(inferred, query.sortableFields(), FieldCapability.SORT);
        grant(inferred, Set.of("id"), FieldCapability.REFERENCE);
        var immutable = new LinkedHashMap<String, Set<FieldCapability>>();
        inferred.forEach((field, values) -> immutable.put(field, Set.copyOf(values)));
        return Map.copyOf(immutable);
    }

    private static void grant(
            Map<String, EnumSet<FieldCapability>> target,
            Iterable<String> fields,
            FieldCapability... capabilities) {
        fields.forEach(
                field -> {
                    var values =
                            target.computeIfAbsent(
                                    field, ignored -> EnumSet.noneOf(FieldCapability.class));
                    values.addAll(List.of(capabilities));
                });
    }

    public String entitySlug() {
        return key.slug();
    }

    public String displayName() {
        return descriptor.label();
    }

    public String permissionNamespace() {
        return descriptor.permissionNamespace();
    }

    public String permissionCode(CrudAction action) {
        return "%s:%s".formatted(descriptor.permissionNamespace(), action.permissionSegment());
    }

    public String accessModePermissionCode(AccessMode accessMode) {
        return "%s:access-mode:%s"
                .formatted(descriptor.permissionNamespace(), accessMode.permissionSegment());
    }
}
