package com.xuejiai.aaf.module.system.entity.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.module.system.ErrorCodeConstants;
import com.xuejiai.aaf.module.system.entity.domain.EntityDef;
import com.xuejiai.aaf.module.system.entity.repository.EntityDefRepository;
import com.xuejiai.aaf.module.system.entity.vo.CodeEntityResourceVO;
import com.xuejiai.aaf.module.system.entity.vo.EntityDefBootstrapVO;
import com.xuejiai.aaf.module.system.entity.vo.EntityDefCreateDTO;
import com.xuejiai.aaf.module.system.entity.vo.EntityDefUpdateDTO;
import com.xuejiai.aaf.module.system.entity.vo.EntityDefVO;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 实体定义业务逻辑。
 *
 * <p>实体定义只保存 UI 元数据，不创建数据表、提供通用记录 CRUD 或决定服务端接口能力。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityDefService {

    private final EntityDefRepository entityDefRepository;
    private final CrudResourceRegistry crudResourceRegistry;

    /** 查询启用的实体定义，供运行时视图引擎加载。 */
    public List<EntityDefVO> listAll() {
        return entityDefRepository.findAll().stream()
                .filter(EntityDef::getEnabled)
                .map(this::toVO)
                .toList();
    }

    /** 查询工作区启动所需的实体定义与受信任资源目录。 */
    public EntityDefBootstrapVO bootstrap() {
        return new EntityDefBootstrapVO(
                listAll(),
                crudResourceRegistry.entries().stream().map(this::toCodeResource).toList());
    }

    /** 查询单个实体定义。 */
    public EntityDefVO getById(Long id) {
        return toVO(findById(id));
    }

    /** 创建实体定义。 */
    @Transactional
    public EntityDefVO create(EntityDefCreateDTO dto) {
        if (entityDefRepository.existsBySlug(dto.slug())) {
            throw exception(ErrorCodeConstants.ENTITY_DEF_SLUG_EXISTS, dto.slug());
        }
        validateConfig(dto.slug(), dto.config());
        var entity = new EntityDef();
        entity.setSlug(dto.slug());
        entity.setConfig(JsonUtils.toJsonString(dto.config()));
        entity.setBuiltin(false);
        entity.setEnabled(dto.enabled() != null ? dto.enabled() : true);
        entityDefRepository.save(entity);
        return toVO(entity);
    }

    /** 更新实体定义（内置配置不可覆盖）。 */
    @Transactional
    public EntityDefVO update(Long id, EntityDefUpdateDTO dto) {
        var entity = findById(id);
        if (entity.getBuiltin()) {
            throw exception(ErrorCodeConstants.ENTITY_DEF_BUILTIN_UPDATE_FORBIDDEN);
        }
        if (dto.config() != null) {
            validateConfig(entity.getSlug(), dto.config());
            entity.setConfig(JsonUtils.toJsonString(dto.config()));
        }
        if (dto.enabled() != null) {
            entity.setEnabled(dto.enabled());
        }
        entityDefRepository.save(entity);
        return toVO(entity);
    }

    /** 删除实体定义。 */
    @Transactional
    public void delete(Long id) {
        var entity = findById(id);
        if (entity.getBuiltin()) {
            throw exception(ErrorCodeConstants.ENTITY_DEF_BUILTIN_DELETE_FORBIDDEN);
        }
        entityDefRepository.delete(entity);
    }

    /** 审计已启用或内置的持久化代码实体定义，避免 Flyway 种子绕过 API 校验。 */
    public void auditPersistedCodeDefinitions() {
        entityDefRepository.findAll().stream()
                .filter(
                        entity ->
                                Boolean.TRUE.equals(entity.getEnabled())
                                        || Boolean.TRUE.equals(entity.getBuiltin()))
                .forEach(this::validatePersistedCodeDefinition);
    }

    private void validatePersistedCodeDefinition(EntityDef entity) {
        var config = JsonUtils.readTree(entity.getConfig());
        if ("code".equals(config.path("kind").asText())) {
            validateConfig(entity.getSlug(), config);
        }
    }

    private void validateConfig(String slug, JsonNode config) {
        if (config == null || !config.isObject()) {
            throw exception(ErrorCodeConstants.ENTITY_DEF_CONFIG_INVALID);
        }
        if ("code".equals(config.path("kind").asText())) {
            validateCodeResource(slug, config);
        }
    }

    private void validateCodeResource(String slug, JsonNode config) {
        if (config.has("slug") || config.has("apiPath")) {
            throw exception(ErrorCodeConstants.ENTITY_DEF_CONFIG_INVALID);
        }
        var descriptor = requireCodeResource(config.path("resource").asText());
        if (!slug.equals(descriptor.slug())) {
            throw exception(ErrorCodeConstants.ENTITY_DEF_CONFIG_INVALID);
        }
        validateRelationshipResources(config.path("fields"));
        var allowedFields =
                Set.copyOf(requireCatalogResource(descriptor.resource()).snapshot().fields());
        validateFieldReferences(config, allowedFields);
    }

    private void validateRelationshipResources(JsonNode fields) {
        if (!fields.isArray()) {
            return;
        }
        for (var field : fields) {
            if (!field.isObject() || !"relationship".equals(field.path("type").asText())) {
                continue;
            }
            if (field.has("pickerPath")) {
                throw exception(ErrorCodeConstants.ENTITY_DEF_CONFIG_INVALID);
            }
            requireCodeResource(field.path("relationTo").asText());
        }
    }

    private void validateFieldReferences(JsonNode config, Set<String> allowedFields) {
        validateFormFields(config.path("fields"), allowedFields);

        var listView = config.path("listView");
        validateStringArray(listView.path("columns"), allowedFields);
        validateStringArray(listView.path("searchableFields"), allowedFields);
        validateStringArray(listView.path("filterableFields"), allowedFields);
        validateStringArray(listView.path("filterFields"), allowedFields);
        var quickFilters = listView.path("quickFilters");
        if (!quickFilters.isMissingNode() && !quickFilters.isArray()) {
            throw exception(ErrorCodeConstants.ENTITY_DEF_CONFIG_INVALID);
        }
        for (var quickFilter : quickFilters) {
            var conditions = quickFilter.path("conditions");
            if (!quickFilter.isObject() || !conditions.isArray()) {
                throw exception(ErrorCodeConstants.ENTITY_DEF_CONFIG_INVALID);
            }
            for (var condition : conditions) {
                validateFieldName(condition.path("field"), allowedFields);
            }
        }
        validateFieldName(listView.path("tabs").path("field"), allowedFields);
    }

    private void validateFormFields(JsonNode fields, Set<String> allowedFields) {
        if (!fields.isArray()) {
            return;
        }
        for (var field : fields) {
            if (!field.isObject()) {
                continue;
            }
            var type = field.path("type").asText();
            if ("group".equals(type) || "tabs".equals(type) || "row".equals(type)) {
                continue;
            }
            validateFieldName(field.path("name"), allowedFields);
        }
    }

    private void validateStringArray(JsonNode fieldNames, Set<String> allowedFields) {
        if (!fieldNames.isArray()) {
            return;
        }
        for (var fieldName : fieldNames) {
            validateFieldName(fieldName, allowedFields);
        }
    }

    private void validateFieldName(JsonNode fieldName, Set<String> allowedFields) {
        if (!fieldName.isTextual() || fieldName.asText().isBlank()) {
            return;
        }
        if (!allowedFields.contains(fieldName.asText())) {
            throw exception(
                    ErrorCodeConstants.ENTITY_DEF_VIEW_FIELD_UNDECLARED, fieldName.asText());
        }
    }

    private CodeEntityResourceVO requireCodeResource(String resource) {
        return toCodeResource(requireCatalogResource(resource));
    }

    private CrudResourceCatalogEntry requireCatalogResource(String resource) {
        if (resource == null || resource.isBlank()) {
            throw exception(ErrorCodeConstants.ENTITY_DEF_CONFIG_INVALID);
        }
        return crudResourceRegistry
                .find(resource)
                .orElseThrow(() -> exception(ErrorCodeConstants.ENTITY_DEF_CONFIG_INVALID));
    }

    private CodeEntityResourceVO toCodeResource(CrudResourceCatalogEntry entry) {
        var snapshot = entry.snapshot();
        return new CodeEntityResourceVO(
                snapshot.key().value(),
                snapshot.slug(),
                snapshot.descriptor().label(),
                snapshot.descriptor().clientApiPath(),
                snapshot.descriptor().permissionNamespace(),
                snapshot.fields(),
                snapshot.operations(),
                snapshot.fieldSets(),
                snapshot.tenantScope(),
                snapshot.exposures(),
                snapshot.schemaVersion(),
                snapshot.referenceable(),
                snapshot.fingerprint(),
                snapshot.builtAt());
    }

    private EntityDef findById(Long id) {
        return entityDefRepository
                .findById(id)
                .orElseThrow(() -> exception(ErrorCodeConstants.ENTITY_DEF_NOT_FOUND));
    }

    private EntityDefVO toVO(EntityDef entity) {
        var persistedConfig = JsonUtils.readTree(entity.getConfig());
        var config = enrichCodeConfig(persistedConfig);
        return new EntityDefVO(
                entity.getId(),
                entity.getSlug(),
                resolveApiPath(persistedConfig),
                config,
                entity.getBuiltin(),
                entity.getEnabled(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    private String resolveApiPath(JsonNode config) {
        if (!"code".equals(config.path("kind").asText())) {
            return null;
        }
        return requireCodeResource(config.path("resource").asText()).apiPath();
    }

    private JsonNode enrichCodeConfig(JsonNode config) {
        if (!"code".equals(config.path("kind").asText()) || !(config instanceof ObjectNode)) {
            return config;
        }
        var enriched = (ObjectNode) config.deepCopy();
        injectRelationshipPickerPaths(enriched.path("fields"));
        return enriched;
    }

    private void injectRelationshipPickerPaths(JsonNode fields) {
        if (!fields.isArray()) {
            return;
        }
        for (var field : fields) {
            if (!(field instanceof ObjectNode relationship)
                    || !"relationship".equals(relationship.path("type").asText())) {
                continue;
            }
            var relation = requireCodeResource(relationship.path("relationTo").asText());
            relationship.put("pickerPath", relation.apiPath() + "/_options");
        }
    }
}
