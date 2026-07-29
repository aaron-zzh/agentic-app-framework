package com.xuejiai.aaf.framework.crud.resource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.definition.*;
import com.xuejiai.aaf.framework.crud.enforcement.CompiledFieldPolicy;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.CrudReferenceDefinition;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;
import com.xuejiai.aaf.framework.crud.reference.ReferencePolicy;
import com.xuejiai.aaf.framework.crud.relation.AssociationKind;
import com.xuejiai.aaf.framework.crud.relation.CrudAssociation;
import com.xuejiai.aaf.framework.crud.relation.RelationDefinition;
import com.xuejiai.aaf.framework.crud.view.CrudViewMapper;
import com.xuejiai.aaf.framework.crud.view.CrudViewPlan;
import com.xuejiai.aaf.framework.crud.web.NestedCrudResourceController;
import com.xuejiai.aaf.framework.crud.web.ResourceOptionsController;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;

@Component
public final class CrudResourceCompiler implements SmartInitializingSingleton {

    private static final String OPTIONS_PATH = "/_options";

    private final List<CrudResourceDefinitionProvider<?>> providers;
    private final List<BaseCrudController<?, ?, ?, ?, ?>> crudControllers;
    private final List<NestedCrudResourceController<?, ?, ?, ?, ?>> nestedCrudControllers;
    private final List<ResourceOptionsController<?, ?>> optionsControllers;
    private final CrudResourceRegistry catalog;
    private final ApplicationContext applicationContext;
    private final EntityManagerFactory entityManagerFactory;

    public CrudResourceCompiler(
            List<CrudResourceDefinitionProvider<?>> providers,
            List<BaseCrudController<?, ?, ?, ?, ?>> crudControllers,
            List<NestedCrudResourceController<?, ?, ?, ?, ?>> nestedCrudControllers,
            List<ResourceOptionsController<?, ?>> optionsControllers,
            CrudResourceRegistry catalog,
            ApplicationContext applicationContext,
            EntityManagerFactory entityManagerFactory) {
        this.providers = List.copyOf(providers);
        this.crudControllers = List.copyOf(crudControllers);
        this.nestedCrudControllers = List.copyOf(nestedCrudControllers);
        this.optionsControllers = List.copyOf(optionsControllers);
        this.catalog = catalog;
        this.applicationContext = applicationContext;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (providers.isEmpty()) {
            throw new IllegalStateException("CRUD 资源 Provider 目录不能为空");
        }
        var entries = new LinkedHashMap<ResourceKey, CrudResourceCatalogEntry>();
        var keysBySlug = new LinkedHashMap<String, ResourceKey>();
        var keysByApiPath = new LinkedHashMap<String, ResourceKey>();
        var keysByController = new LinkedHashMap<Class<?>, ResourceKey>();
        var builtAt = Instant.now();
        for (var provider : providers) {
            compile(provider, entries, keysBySlug, keysByApiPath, keysByController, builtAt);
        }
        validateEndpointBijection(keysByController.keySet());
        validateRelationTargets(entries);
        catalog.publish(entries);
    }

    private void compile(
            CrudResourceDefinitionProvider<?> provider,
            Map<ResourceKey, CrudResourceCatalogEntry> entries,
            Map<String, ResourceKey> keysBySlug,
            Map<String, ResourceKey> keysByApiPath,
            Map<Class<?>, ResourceKey> keysByController,
            Instant builtAt) {
        Objects.requireNonNull(provider, "CrudResourceDefinitionProvider");
        var rawDefinition = Objects.requireNonNull(provider.definition(), "provider.definition()");
        var definition = compileAssociations(rawDefinition);
        var endpoint =
                Objects.requireNonNull(provider.endpointBinding(), "provider.endpointBinding()");
        validateDefinitionAndEndpoint(definition, endpoint);
        var snapshot = snapshot(definition, endpoint, builtAt);
        var entry =
                new CrudResourceCatalogEntry(
                        definition,
                        endpoint,
                        snapshot,
                        compileFieldPolicy(definition),
                        compileViewPlans(definition));
        putUnique(entries, entry.key(), entry, "ResourceKey");
        putUnique(keysBySlug, entry.key().slug(), entry.key(), "资源 slug");
        putUnique(keysByApiPath, endpoint.apiPath(), entry.key(), "资源 API 路径");
        putUnique(keysByController, endpoint.controllerType(), entry.key(), "资源 Controller");
    }

    private <E extends BaseEntity> CrudResourceDefinition<E> compileAssociations(
            CrudResourceDefinition<E> rawDefinition) {
        var entityType = rawDefinition.types().entityType();
        var references = new java.util.ArrayList<>(rawDefinition.references());
        for (var declared : declaredReferences(entityType)) {
            var annotation = declared.annotation();
            var idProperty = defaultText(annotation.idProperty(), declared.fieldName());
            var key = defaultText(annotation.key(), referenceKey(idProperty));
            if (references.stream().anyMatch(reference -> reference.key().equals(key))) {
                throw new IllegalStateException(
                        "资源 %s 的显式引用与实体注解重复: %s".formatted(rawDefinition.key().value(), key));
            }
            var capabilities = Set.of(annotation.capabilities());
            var target =
                    annotation.targetResource().isBlank()
                            ? null
                            : ResourceKey.of(annotation.targetResource());
            var inputField = annotation.inputField();
            if (inputField.isBlank() && capabilities.contains(ReferenceCapability.REFERENCE)) {
                inputField = target == null ? key : idProperty;
            }
            references.add(
                    new CrudReferenceDefinition(
                            key,
                            idProperty,
                            target,
                            annotation.resourceProperty(),
                            inputField,
                            defaultText(annotation.viewField(), key),
                            annotation.additionalPolicyBean(),
                            capabilities));
        }

        var relations = new java.util.ArrayList<>(rawDefinition.relations());
        entityManagerFactory.getMetamodel().getEntities().stream()
                .map(jakarta.persistence.metamodel.EntityType::getJavaType)
                .filter(type -> type.isAnnotationPresent(CrudAssociation.class))
                .forEach(
                        associationType -> {
                            var annotation = associationType.getAnnotation(CrudAssociation.class);
                            if (!annotation.sourceEntity().equals(entityType)) {
                                return;
                            }
                            if (relations.stream()
                                    .anyMatch(
                                            relation -> relation.key().equals(annotation.key()))) {
                                throw new IllegalStateException(
                                        "资源 %s 的显式关系与关联注解重复: %s"
                                                .formatted(
                                                        rawDefinition.key().value(),
                                                        annotation.key()));
                            }
                            var inputField =
                                    annotation.inputField().isBlank()
                                                    && annotation.syncMode()
                                                            != RelationDefinition.SyncMode.READ_ONLY
                                            ? annotation.key()
                                            : annotation.inputField();
                            relations.add(
                                    new RelationDefinition<>(
                                            annotation.key(),
                                            ResourceKey.of(annotation.targetResource()),
                                            RelationDefinition.Cardinality.MANY,
                                            annotation.syncMode(),
                                            annotation.maxCardinality(),
                                            associationType,
                                            annotation.kind(),
                                            annotation.sourceProperty(),
                                            annotation.targetProperty(),
                                            inputField,
                                            defaultText(annotation.viewField(), annotation.key()),
                                            annotation.additionalPolicyBean()));
                        });

        var createFields = new LinkedHashSet<>(rawDefinition.mutation().createFields());
        var updateFields = new LinkedHashSet<>(rawDefinition.mutation().updateFields());
        var relationFields = new LinkedHashMap<>(rawDefinition.mutation().relationFields());
        relations.stream()
                .filter(relation -> relation.syncMode() != RelationDefinition.SyncMode.READ_ONLY)
                .forEach(
                        relation -> {
                            relationFields.put(relation.key(), relation.inputField());
                            if (typeFields(rawDefinition.types().createType())
                                    .contains(relation.inputField())) {
                                createFields.add(relation.inputField());
                            }
                            if (typeFields(rawDefinition.types().updateType())
                                    .contains(relation.inputField())) {
                                updateFields.add(relation.inputField());
                            }
                        });
        var mutation =
                new CrudMutationDefinition(
                        createFields,
                        updateFields,
                        relationFields,
                        rawDefinition.mutation().customUpdateCommands());

        var fieldSets = new LinkedHashMap<String, Set<String>>();
        rawDefinition
                .view()
                .fieldSets()
                .forEach((name, fields) -> fieldSets.put(name, new LinkedHashSet<>(fields)));
        var detail = new LinkedHashSet<>(fieldSets.getOrDefault("detail", Set.of()));
        references.stream()
                .map(CrudReferenceDefinition::viewField)
                .filter(field -> !field.isBlank())
                .forEach(detail::add);
        relations.stream()
                .map(RelationDefinition::viewField)
                .filter(field -> !field.isBlank())
                .forEach(detail::add);
        if (!detail.isEmpty()) {
            fieldSets.put("detail", detail);
        }
        var view = new CrudViewDefinition(fieldSets, rawDefinition.view().viewMapperBean());

        var fieldCapabilities = new LinkedHashMap<String, Set<FieldCapability>>();
        rawDefinition
                .fieldCapabilities()
                .forEach(
                        (field, capabilities) ->
                                fieldCapabilities.put(field, new LinkedHashSet<>(capabilities)));
        references.forEach(
                reference -> {
                    if (!reference.viewField().isBlank()) {
                        grantCapability(
                                fieldCapabilities, reference.viewField(), FieldCapability.READ);
                    }
                    if (!reference.inputField().isBlank()
                            && reference.supports(ReferenceCapability.REFERENCE)) {
                        grantCapability(
                                fieldCapabilities,
                                reference.inputField(),
                                FieldCapability.WRITE,
                                FieldCapability.REFERENCE);
                    }
                });
        relations.forEach(
                relation -> {
                    if (!relation.viewField().isBlank()) {
                        grantCapability(
                                fieldCapabilities, relation.viewField(), FieldCapability.READ);
                    }
                    if (!relation.inputField().isBlank()) {
                        grantCapability(
                                fieldCapabilities,
                                relation.inputField(),
                                FieldCapability.WRITE,
                                FieldCapability.REFERENCE);
                    }
                });
        var immutableCapabilities = new LinkedHashMap<String, Set<FieldCapability>>();
        fieldCapabilities.forEach(
                (field, capabilities) ->
                        immutableCapabilities.put(field, Set.copyOf(capabilities)));

        return new CrudResourceDefinition<>(
                rawDefinition.key(),
                rawDefinition.types(),
                rawDefinition.descriptor(),
                rawDefinition.capabilities(),
                rawDefinition.query(),
                mutation,
                view,
                immutableCapabilities,
                references,
                relations,
                rawDefinition.tenantScope(),
                rawDefinition.personalScope(),
                rawDefinition.exposures(),
                rawDefinition.schemaVersion());
    }

    private void grantCapability(
            Map<String, Set<FieldCapability>> target,
            String field,
            FieldCapability... capabilities) {
        var values = target.computeIfAbsent(field, ignored -> new LinkedHashSet<>());
        values.addAll(List.of(capabilities));
    }

    private List<DeclaredReference> declaredReferences(Class<?> entityType) {
        var declared = new java.util.ArrayList<DeclaredReference>();
        Arrays.stream(entityType.getAnnotationsByType(CrudReference.class))
                .map(annotation -> new DeclaredReference(annotation, ""))
                .forEach(declared::add);
        for (var current = entityType;
                current != null && !Object.class.equals(current);
                current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
                Arrays.stream(field.getAnnotationsByType(CrudReference.class))
                        .map(annotation -> new DeclaredReference(annotation, field.getName()))
                        .forEach(declared::add);
            }
        }
        return List.copyOf(declared);
    }

    private String referenceKey(String idProperty) {
        if (idProperty != null && idProperty.endsWith("Id") && idProperty.length() > 2) {
            return idProperty.substring(0, idProperty.length() - 2);
        }
        return idProperty;
    }

    private record DeclaredReference(CrudReference annotation, String fieldName) {}

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void validateDefinitionAndEndpoint(
            CrudResourceDefinition<?> definition, CrudResourceEndpointBinding endpoint) {
        if (!definition.key().equals(endpoint.resourceKey())) {
            throw new IllegalStateException("Definition 与端点绑定的 ResourceKey 不一致");
        }
        if (!definition.descriptor().apiPath().equals(endpoint.apiPath())) {
            throw new IllegalStateException("Definition 与端点绑定的 API 路径不一致");
        }
        if (definition.schemaVersion() != CrudResourceDefinition.CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的资源 schemaVersion: " + definition.schemaVersion());
        }
        validateProperties(definition);
        validateEndpoint(definition, endpoint);
    }

    private void validateProperties(CrudResourceDefinition<?> definition) {
        var entityType = definition.types().entityType();
        var query = definition.query();
        query.filterSchema().metas().forEach(meta -> validateProperty(entityType, meta.field()));
        query.sortableFields().forEach(property -> validateProperty(entityType, property));
        if (definition.personalScope().enabled()) {
            validateProperty(entityType, definition.personalScope().property());
        }
        validateCapabilities(definition);
        validateMutation(definition);
        validateReferences(definition);
        validateRelations(definition);
        validateView(definition);
        validateFieldCapabilities(definition);
    }

    private void validateCapabilities(CrudResourceDefinition<?> definition) {
        var forbidden = new LinkedHashSet<CrudOperation>();
        var createReadonly = Void.class.equals(definition.types().createType());
        var updateReadonly = Void.class.equals(definition.types().updateType());
        if (createReadonly) {
            forbidden.addAll(
                    Set.of(CrudOperation.CREATE, CrudOperation.IMPORT, CrudOperation.VALIDATE));
        }
        if (updateReadonly) {
            forbidden.addAll(Set.of(CrudOperation.UPDATE, CrudOperation.RESTORE));
        }
        if (createReadonly && updateReadonly) {
            forbidden.addAll(
                    Set.of(
                            CrudOperation.DELETE,
                            CrudOperation.DELETE_BATCH,
                            CrudOperation.ARCHIVE));
        }
        forbidden.retainAll(definition.capabilities().operations());
        if (!forbidden.isEmpty()) {
            throw new IllegalStateException(
                    "资源 %s 的操作能力与输入类型契约不一致: %s".formatted(definition.key().value(), forbidden));
        }
    }

    private void validateFieldCapabilities(CrudResourceDefinition<?> definition) {
        var fieldCapabilities = definition.fieldCapabilities();
        var knownFields = new LinkedHashSet<String>();
        knownFields.addAll(typeFields(definition.types().entityType()));
        knownFields.addAll(typeFields(definition.types().createType()));
        knownFields.addAll(typeFields(definition.types().updateType()));
        knownFields.addAll(typeFields(definition.types().viewType()));
        knownFields.addAll(typeFields(definition.types().pageType()));
        knownFields.addAll(definition.mutation().customUpdateFields());
        fieldCapabilities.forEach(
                (field, capabilities) -> {
                    if (!knownFields.contains(field)) {
                        throw new IllegalStateException(
                                "资源 %s 的字段能力引用了未知字段: %s"
                                        .formatted(definition.key().value(), field));
                    }
                    if (capabilities.isEmpty()) {
                        throw new IllegalStateException(
                                "资源 %s 的字段能力不能为空: %s".formatted(definition.key().value(), field));
                    }
                });
        definition.view().fieldSets().values().stream()
                .flatMap(Set::stream)
                .forEach(field -> requireFieldCapability(definition, field, FieldCapability.READ));
        definition
                .view()
                .fieldSets()
                .getOrDefault("export", Set.of())
                .forEach(
                        field -> requireFieldCapability(definition, field, FieldCapability.EXPORT));
        Stream.of(
                        definition.mutation().createFields().stream(),
                        definition.mutation().updateFields().stream(),
                        definition.mutation().customUpdateFields().stream())
                .flatMap(stream -> stream)
                .forEach(field -> requireFieldCapability(definition, field, FieldCapability.WRITE));
        definition
                .mutation()
                .relationFields()
                .values()
                .forEach(
                        field ->
                                requireFieldCapability(
                                        definition, field, FieldCapability.REFERENCE));
        definition
                .query()
                .filterSchema()
                .metas()
                .forEach(
                        meta ->
                                requireFieldCapability(
                                        definition, meta.field(), FieldCapability.FILTER));
        definition
                .query()
                .sortableFields()
                .forEach(field -> requireFieldCapability(definition, field, FieldCapability.SORT));
        if (definition.exposures().contains(CrudResourceExposure.REFERENCE)) {
            requireFieldCapability(definition, "id", FieldCapability.REFERENCE);
        }
    }

    private void requireFieldCapability(
            CrudResourceDefinition<?> definition, String field, FieldCapability capability) {
        if (!definition.fieldCapabilities().getOrDefault(field, Set.of()).contains(capability)) {
            throw new IllegalStateException(
                    "资源 %s 的字段 %s 未声明能力 %s".formatted(definition.key().value(), field, capability));
        }
    }

    private void validateMutation(CrudResourceDefinition<?> definition) {
        var mutation = definition.mutation();
        mutation.createFields()
                .forEach(
                        field -> validateTypeField(definition.types().createType(), field, "创建输入"));
        mutation.updateFields()
                .forEach(
                        field -> validateTypeField(definition.types().updateType(), field, "更新输入"));
        var relationsByKey =
                definition.relations().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        RelationDefinition::key, relation -> relation));
        mutation.relationFields()
                .forEach(
                        (relationKey, inputField) -> {
                            var relation = relationsByKey.get(relationKey);
                            if (relation == null) {
                                throw new IllegalStateException(
                                        "资源 %s 的 Mutation 引用了未知关系: %s"
                                                .formatted(definition.key().value(), relationKey));
                            }
                            if (relation.syncMode() == RelationDefinition.SyncMode.READ_ONLY) {
                                throw new IllegalStateException(
                                        "资源 %s 的 Mutation 不能绑定只读关系: %s"
                                                .formatted(definition.key().value(), relationKey));
                            }
                            var declared =
                                    mutation.createFields().contains(inputField)
                                            || mutation.updateFields().contains(inputField);
                            if (!declared) {
                                throw new IllegalStateException(
                                        "资源 %s 的关系输入字段未声明为 create/update 字段: %s"
                                                .formatted(definition.key().value(), inputField));
                            }
                            Stream.of(
                                            definition.types().createType(),
                                            definition.types().updateType())
                                    .filter(type -> typeFields(type).contains(inputField))
                                    .map(type -> typeFieldType(type, inputField))
                                    .filter(Objects::nonNull)
                                    .filter(type -> !Patch.class.isAssignableFrom(type))
                                    .findFirst()
                                    .ifPresent(
                                            type -> {
                                                throw new IllegalStateException(
                                                        "资源 %s 的关系输入字段必须使用 Patch: %s"
                                                                .formatted(
                                                                        definition.key().value(),
                                                                        inputField));
                                            });
                        });
    }

    private void validateReferences(CrudResourceDefinition<?> definition) {
        var keys = new LinkedHashSet<String>();
        var inputFields = new LinkedHashSet<String>();
        var viewFields = new LinkedHashSet<String>();
        for (var reference : definition.references()) {
            if (!keys.add(reference.key())) {
                throw new IllegalStateException(
                        "资源 %s 存在重复引用 key: %s"
                                .formatted(definition.key().value(), reference.key()));
            }
            validateLongProperty(definition.types().entityType(), reference.idProperty());
            if (reference.polymorphic()) {
                validateStringProperty(
                        definition.types().entityType(), reference.resourceProperty());
            }
            if (!reference.inputField().isBlank()) {
                if (!inputFields.add(reference.inputField())) {
                    throw new IllegalStateException(
                            "资源 %s 的多个引用使用同一输入字段: %s"
                                    .formatted(definition.key().value(), reference.inputField()));
                }
                validateInputField(definition, reference.inputField());
            }
            if (!reference.viewField().isBlank()) {
                if (!viewFields.add(reference.viewField())) {
                    throw new IllegalStateException(
                            "资源 %s 的多个引用使用同一输出字段: %s"
                                    .formatted(definition.key().value(), reference.viewField()));
                }
                validateResourceRefViewField(
                        definition.types().viewType(), reference.viewField(), false);
                requireFieldCapability(definition, reference.viewField(), FieldCapability.READ);
            }
            if (reference.supports(ReferenceCapability.REFERENCE)
                    && !reference.inputField().isBlank()) {
                requireFieldCapability(
                        definition, reference.inputField(), FieldCapability.REFERENCE);
            }
            if (!reference.additionalPolicyBean().isBlank()) {
                validateBean(
                        reference.additionalPolicyBean(), ReferencePolicy.class, "ReferencePolicy");
            }
        }
    }

    private void validateRelations(CrudResourceDefinition<?> definition) {
        var relationKeys = new LinkedHashSet<String>();
        var referenceKeys =
                definition.references().stream()
                        .map(CrudReferenceDefinition::key)
                        .collect(Collectors.toSet());
        var inputFields =
                definition.references().stream()
                        .map(CrudReferenceDefinition::inputField)
                        .filter(field -> !field.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        var viewFields =
                definition.references().stream()
                        .map(CrudReferenceDefinition::viewField)
                        .filter(field -> !field.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        for (var relation : definition.relations()) {
            if (!relationKeys.add(relation.key()) || referenceKeys.contains(relation.key())) {
                throw new IllegalStateException(
                        "资源 %s 存在重复引用/关系 key: %s"
                                .formatted(definition.key().value(), relation.key()));
            }
            validateLongProperty(relation.associationEntity(), relation.sourceProperty());
            validateLongProperty(relation.associationEntity(), relation.targetProperty());
            if (relation.associationKind() == AssociationKind.MANY_TO_MANY_JOIN) {
                validatePureJoinEntity(relation);
            } else if (relation.syncMode() != RelationDefinition.SyncMode.READ_ONLY) {
                throw new IllegalStateException("ONE_TO_MANY_CHILD 只允许 READ_ONLY");
            }
            if (!relation.inputField().isBlank()) {
                if (!inputFields.add(relation.inputField())) {
                    throw new IllegalStateException(
                            "资源 %s 的多个引用/关系使用同一输入字段: %s"
                                    .formatted(definition.key().value(), relation.inputField()));
                }
                validateInputField(definition, relation.inputField());
            }
            if (!relation.viewField().isBlank()) {
                if (!viewFields.add(relation.viewField())) {
                    throw new IllegalStateException(
                            "资源 %s 的多个引用/关系使用同一输出字段: %s"
                                    .formatted(definition.key().value(), relation.viewField()));
                }
                validateResourceRefViewField(
                        definition.types().viewType(), relation.viewField(), true);
            }
            if (!relation.additionalPolicyBean().isBlank()) {
                validateBean(
                        relation.additionalPolicyBean(), ReferencePolicy.class, "ReferencePolicy");
            }
        }
    }

    private void validateView(CrudResourceDefinition<?> definition) {
        var view = definition.view();
        var outputFields = Set.copyOf(viewFields(definition.types().viewType()));
        view.fieldSets()
                .forEach(
                        (fieldSet, fields) ->
                                fields.forEach(
                                        field -> {
                                            if (!outputFields.contains(field)) {
                                                throw new IllegalStateException(
                                                        "资源 %s 的 fieldSet %s 引用了未知输出字段: %s"
                                                                .formatted(
                                                                        definition.key().value(),
                                                                        fieldSet,
                                                                        field));
                                            }
                                        }));
        if (!view.viewMapperBean().isBlank()) {
            validateBean(view.viewMapperBean(), CrudViewMapper.class, "CrudViewMapper");
        }
    }

    private void validateRelationTargets(Map<ResourceKey, CrudResourceCatalogEntry> entries) {
        entries.values()
                .forEach(
                        entry -> {
                            entry.definition().references().stream()
                                    .filter(reference -> !reference.polymorphic())
                                    .forEach(
                                            reference ->
                                                    requireReferenceTarget(
                                                            entries,
                                                            entry.key(),
                                                            reference.key(),
                                                            reference.targetResource()));
                            entry.definition()
                                    .relations()
                                    .forEach(
                                            relation ->
                                                    requireReferenceTarget(
                                                            entries,
                                                            entry.key(),
                                                            relation.key(),
                                                            relation.targetResource()));
                        });
    }

    private void requireReferenceTarget(
            Map<ResourceKey, CrudResourceCatalogEntry> entries,
            ResourceKey source,
            String field,
            ResourceKey target) {
        var targetEntry = entries.get(target);
        if (targetEntry == null || !targetEntry.snapshot().referenceable()) {
            throw new IllegalStateException(
                    "资源 %s 的引用 %s 指向未知或不可引用目标资源: %s"
                            .formatted(source.value(), field, target.value()));
        }
    }

    private Map<String, CrudViewPlan> compileViewPlans(CrudResourceDefinition<?> definition) {
        var dependencies = new LinkedHashMap<String, Set<String>>();
        definition.references().stream()
                .filter(reference -> !reference.viewField().isBlank())
                .forEach(
                        reference ->
                                dependencies.put(reference.viewField(), Set.of(reference.key())));
        definition.relations().stream()
                .filter(relation -> !relation.viewField().isBlank())
                .forEach(
                        relation -> dependencies.put(relation.viewField(), Set.of(relation.key())));
        var mapperBean = definition.view().viewMapperBean();
        if (mapperBean.isBlank() && !dependencies.isEmpty()) {
            mapperBean = "defaultCrudViewMapper";
        }
        if (!mapperBean.isBlank()) {
            validateBean(mapperBean, CrudViewMapper.class, "CrudViewMapper");
        }
        var plans = new LinkedHashMap<String, CrudViewPlan>();
        var finalMapperBean = mapperBean;
        definition
                .view()
                .fieldSets()
                .forEach(
                        (fieldSet, outputFields) -> {
                            var activeDependencies = new LinkedHashMap<String, Set<String>>();
                            dependencies.forEach(
                                    (field, keys) -> {
                                        if (outputFields.contains(field)) {
                                            activeDependencies.put(field, keys);
                                        }
                                    });
                            plans.put(
                                    fieldSet,
                                    new CrudViewPlan(
                                            fieldSet,
                                            outputFields,
                                            activeDependencies,
                                            finalMapperBean));
                        });
        return Map.copyOf(plans);
    }

    private void validateBean(String beanName, Class<?> expectedType, String role) {
        if (!applicationContext.containsBean(beanName)
                || !applicationContext.isTypeMatch(beanName, expectedType)) {
            throw new IllegalStateException("%s Bean 不存在或类型不匹配: %s".formatted(role, beanName));
        }
    }

    private void validateTypeField(Class<?> type, String field, String role) {
        if (Void.class.equals(type) || !typeFields(type).contains(field)) {
            throw new IllegalStateException(
                    "%s声明了不存在的字段: %s.%s".formatted(role, type.getName(), field));
        }
    }

    private void validateEndpoint(
            CrudResourceDefinition<?> definition, CrudResourceEndpointBinding endpoint) {
        var controllerType = endpoint.controllerType();
        if (!controllerType.isAnnotationPresent(RestController.class)) {
            throw new IllegalStateException(
                    "资源端点必须标记 @RestController: " + controllerType.getName());
        }
        switch (endpoint.kind()) {
            case CRUD -> validateCrudEndpoint(definition, controllerType);
            case NESTED_CRUD -> validateNestedCrudEndpoint(definition, controllerType);
            case OPTIONS -> validateOptionsEndpoint(definition, controllerType);
        }
        var mapping = controllerType.getDeclaredAnnotation(RequestMapping.class);
        if (mapping == null || !mappingPaths(mapping).equals(Set.of(endpoint.apiPath()))) {
            throw new IllegalStateException(
                    "资源端点绑定与 @RequestMapping 不一致: " + controllerType.getName());
        }
    }

    private void validateCrudEndpoint(
            CrudResourceDefinition<?> definition, Class<?> controllerType) {
        if (!BaseCrudController.class.isAssignableFrom(controllerType)) {
            throw new IllegalStateException(
                    "CRUD 资源端点必须继承 BaseCrudController: " + controllerType.getName());
        }
        var contract = ResolvableType.forClass(controllerType).as(BaseCrudController.class);
        validateControllerTypes(definition, contract, true);
    }

    private void validateNestedCrudEndpoint(
            CrudResourceDefinition<?> definition, Class<?> controllerType) {
        if (!NestedCrudResourceController.class.isAssignableFrom(controllerType)
                || BaseCrudController.class.isAssignableFrom(controllerType)) {
            throw new IllegalStateException(
                    "嵌套 CRUD 端点必须且只能实现 NestedCrudResourceController: " + controllerType.getName());
        }
        var contract =
                ResolvableType.forClass(controllerType).as(NestedCrudResourceController.class);
        validateControllerTypes(definition, contract, true);
    }

    private void validateOptionsEndpoint(
            CrudResourceDefinition<?> definition, Class<?> controllerType) {
        if (!ResourceOptionsController.class.isAssignableFrom(controllerType)
                || BaseCrudController.class.isAssignableFrom(controllerType)) {
            throw new IllegalStateException(
                    "options 资源端点必须且只能实现 ResourceOptionsController: " + controllerType.getName());
        }
        var contract = ResolvableType.forClass(controllerType).as(ResourceOptionsController.class);
        validateControllerTypes(definition, contract, false);
        var mappings =
                Arrays.stream(controllerType.getDeclaredMethods())
                        .map(method -> method.getDeclaredAnnotation(GetMapping.class))
                        .filter(Objects::nonNull)
                        .map(mapping -> mappingPaths(mapping.value(), mapping.path()))
                        .filter(paths -> paths.contains(OPTIONS_PATH))
                        .toList();
        if (mappings.size() != 1 || !mappings.getFirst().equals(Set.of(OPTIONS_PATH))) {
            throw new IllegalStateException(
                    "options 资源端点必须声明唯一 GET /_options: " + controllerType.getName());
        }
    }

    private void validateControllerTypes(
            CrudResourceDefinition<?> definition, ResolvableType contract, boolean fullCrud) {
        var types = definition.types();
        validateControllerType(
                definition, "实体", contract.getGeneric(0).resolve(), types.entityType());
        validateControllerType(
                definition, "输出", contract.getGeneric(1).resolve(), types.viewType());
        if (!fullCrud) {
            return;
        }
        validateControllerType(
                definition, "创建输入", contract.getGeneric(2).resolve(), types.createType());
        validateControllerType(
                definition, "更新输入", contract.getGeneric(3).resolve(), types.updateType());
        validateControllerType(
                definition, "分页输入", contract.getGeneric(4).resolve(), types.pageType());
    }

    private void validateControllerType(
            CrudResourceDefinition<?> definition, String role, Class<?> actual, Class<?> expected) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "资源 %s 类型与 Controller 泛型不一致: %s".formatted(role, definition.key().value()));
        }
    }

    private void validateEndpointBijection(Set<Class<?>> providerControllerTypes) {
        var actualControllerTypes =
                Stream.of(
                                crudControllers.stream().map(AopUtils::getTargetClass),
                                nestedCrudControllers.stream().map(AopUtils::getTargetClass),
                                optionsControllers.stream().map(AopUtils::getTargetClass))
                        .flatMap(stream -> stream)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (actualControllerTypes.isEmpty()) {
            throw new IllegalStateException("Spring 中未发现 CRUD 资源端点");
        }
        var missingProviders = new LinkedHashSet<>(actualControllerTypes);
        missingProviders.removeAll(providerControllerTypes);
        var orphanProviders = new LinkedHashSet<>(providerControllerTypes);
        orphanProviders.removeAll(actualControllerTypes);
        if (!missingProviders.isEmpty() || !orphanProviders.isEmpty()) {
            throw new IllegalStateException(
                    "CRUD 资源端点与 Provider 非双射，缺少 Provider=%s，孤立 Provider=%s"
                            .formatted(classNames(missingProviders), classNames(orphanProviders)));
        }
    }

    private CrudResourceSnapshot snapshot(
            CrudResourceDefinition<?> definition,
            CrudResourceEndpointBinding endpoint,
            Instant builtAt) {
        var fields = viewFields(definition.types().viewType());
        return new CrudResourceSnapshot(
                definition.key(),
                definition.entitySlug(),
                definition.descriptor(),
                definition.capabilities().operations().stream()
                        .map(CrudOperation::capability)
                        .toList(),
                definition.view().fieldSets().keySet().stream().sorted().toList(),
                definition.tenantScope(),
                definition.exposures(),
                definition.schemaVersion(),
                fields,
                definition.references().stream()
                        .sorted(Comparator.comparing(CrudReferenceDefinition::key))
                        .toList(),
                definition.exposures().contains(CrudResourceExposure.REFERENCE),
                fingerprint(definition, endpoint),
                builtAt);
    }

    private List<String> viewFields(Class<?> viewType) {
        var fields = new LinkedHashSet<String>();
        if (viewType.isRecord()) {
            Arrays.stream(viewType.getRecordComponents())
                    .map(component -> component.getName())
                    .forEach(fields::add);
        } else {
            for (var current = viewType;
                    current != null && !Object.class.equals(current);
                    current = current.getSuperclass()) {
                Arrays.stream(current.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .filter(field -> !field.isSynthetic())
                        .map(Field::getName)
                        .forEach(fields::add);
            }
        }
        if (fields.isEmpty()) {
            throw new IllegalStateException("资源输出类型没有可公开字段: " + viewType.getName());
        }
        return fields.stream().sorted().toList();
    }

    private String fingerprint(
            CrudResourceDefinition<?> definition, CrudResourceEndpointBinding endpoint) {
        var source =
                String.join(
                        "|",
                        definition.key().value(),
                        definition.types().entityType().getName(),
                        definition.types().createType().getName(),
                        definition.types().updateType().getName(),
                        definition.types().viewType().getName(),
                        definition.types().pageType().getName(),
                        definition.descriptor().toString(),
                        definition.capabilities().operations().stream()
                                .map(CrudOperation::capability)
                                .collect(Collectors.joining(",")),
                        definition.query().filterSchema().metas().toString(),
                        definition.query().sortableFields().stream()
                                .sorted()
                                .collect(Collectors.joining(",")),
                        definition.query().defaultSort().toString(),
                        definition.mutation().toString(),
                        definition.view().toString(),
                        definition.fieldCapabilities().toString(),
                        definition.references().stream()
                                .sorted(Comparator.comparing(CrudReferenceDefinition::key))
                                .map(CrudReferenceDefinition::toString)
                                .collect(Collectors.joining(";")),
                        definition.relations().toString(),
                        definition.tenantScope().name(),
                        definition.personalScope().toString(),
                        definition.exposures().stream()
                                .map(Enum::name)
                                .sorted()
                                .collect(Collectors.joining(",")),
                        String.valueOf(definition.schemaVersion()),
                        endpoint.controllerType().getName(),
                        endpoint.kind().name());
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    private CompiledFieldPolicy compileFieldPolicy(CrudResourceDefinition<?> definition) {
        return new CompiledFieldPolicy(definition.fieldCapabilities());
    }

    private Class<?> typeFieldType(Class<?> type, String fieldName) {
        if (type.isRecord()) {
            return Arrays.stream(type.getRecordComponents())
                    .filter(component -> component.getName().equals(fieldName))
                    .map(component -> component.getType())
                    .findFirst()
                    .orElse(null);
        }
        for (var current = type;
                current != null && !Object.class.equals(current);
                current = current.getSuperclass()) {
            var match =
                    Arrays.stream(current.getDeclaredFields())
                            .filter(field -> field.getName().equals(fieldName))
                            .findFirst();
            if (match.isPresent()) {
                return match.get().getType();
            }
        }
        return null;
    }

    private Set<String> typeFields(Class<?> type) {
        if (Void.class.equals(type)) {
            return Set.of();
        }
        var fields = new LinkedHashSet<String>();
        if (type.isRecord()) {
            Arrays.stream(type.getRecordComponents())
                    .map(component -> component.getName())
                    .forEach(fields::add);
            return Set.copyOf(fields);
        }
        for (var current = type;
                current != null && !Object.class.equals(current);
                current = current.getSuperclass()) {
            Arrays.stream(current.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .filter(field -> !field.isSynthetic())
                    .map(Field::getName)
                    .forEach(fields::add);
        }
        return Set.copyOf(fields);
    }

    private Set<String> mappingPaths(RequestMapping mapping) {
        return mappingPaths(mapping.value(), mapping.path());
    }

    private Set<String> mappingPaths(String[] values, String[] paths) {
        var mappings = new LinkedHashSet<String>();
        mappings.addAll(Arrays.asList(values));
        mappings.addAll(Arrays.asList(paths));
        mappings.removeIf(String::isBlank);
        return Set.copyOf(mappings);
    }

    private void validateLongProperty(Class<?> type, String property) {
        validatePropertyType(type, property, Long.class);
    }

    private void validateStringProperty(Class<?> type, String property) {
        validatePropertyType(type, property, String.class);
    }

    private void validatePropertyType(Class<?> type, String property, Class<?> expectedType) {
        var actual = typeFieldType(type, property);
        if (!expectedType.equals(actual)) {
            throw new IllegalStateException(
                    "属性类型不匹配: %s.%s，期望 %s"
                            .formatted(type.getName(), property, expectedType.getSimpleName()));
        }
    }

    private void validateInputField(CrudResourceDefinition<?> definition, String field) {
        var exists =
                typeFields(definition.types().createType()).contains(field)
                        || typeFields(definition.types().updateType()).contains(field);
        if (!exists) {
            throw new IllegalStateException(
                    "资源 %s 的引用输入字段不存在: %s".formatted(definition.key().value(), field));
        }
    }

    private void validateResourceRefViewField(Class<?> viewType, String field, boolean collection) {
        var actual = typeFieldGenericType(viewType, field);
        var valid =
                collection
                        ? actual instanceof ParameterizedType parameterized
                                && parameterized.getRawType().equals(List.class)
                                && parameterized.getActualTypeArguments().length == 1
                                && parameterized
                                        .getActualTypeArguments()[0]
                                        .getTypeName()
                                        .equals("com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO")
                        : actual != null
                                && actual.getTypeName()
                                        .equals(
                                                "com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO");
        if (!valid) {
            throw new IllegalStateException(
                    "引用输出字段类型非法: %s.%s".formatted(viewType.getName(), field));
        }
    }

    private Type typeFieldGenericType(Class<?> type, String fieldName) {
        if (type.isRecord()) {
            return Arrays.stream(type.getRecordComponents())
                    .filter(component -> component.getName().equals(fieldName))
                    .map(component -> component.getGenericType())
                    .findFirst()
                    .orElse(null);
        }
        for (var current = type;
                current != null && !Object.class.equals(current);
                current = current.getSuperclass()) {
            var match =
                    Arrays.stream(current.getDeclaredFields())
                            .filter(field -> field.getName().equals(fieldName))
                            .findFirst();
            if (match.isPresent()) {
                return match.get().getGenericType();
            }
        }
        return null;
    }

    private void validatePureJoinEntity(RelationDefinition<?, ?> relation) {
        var fields =
                Arrays.stream(relation.associationEntity().getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .filter(field -> !field.isSynthetic())
                        .toList();
        if (fields.size() != 2
                || fields.stream().filter(field -> field.isAnnotationPresent(Id.class)).count() != 2
                || fields.stream().map(Field::getName).collect(Collectors.toSet()).size() != 2
                || !fields.stream()
                        .map(Field::getName)
                        .collect(Collectors.toSet())
                        .equals(Set.of(relation.sourceProperty(), relation.targetProperty()))) {
            throw new IllegalStateException(
                    "MANY_TO_MANY_JOIN 必须是仅含两个复合主键的纯关联实体: "
                            + relation.associationEntity().getName());
        }
    }

    private void validateProperty(Class<? extends BaseEntity> type, String property) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(property)) {
                    return;
                }
            }
        }
        throw new IllegalStateException("资源声明了不存在的实体属性: %s.%s".formatted(type.getName(), property));
    }

    private List<String> classNames(Set<? extends Class<?>> types) {
        return types.stream().map(Class::getName).sorted().toList();
    }

    private <K, V> void putUnique(Map<K, V> values, K key, V value, String label) {
        var existing = values.putIfAbsent(key, value);
        if (existing != null) {
            throw new IllegalStateException("重复 " + label + ": " + key);
        }
    }
}
