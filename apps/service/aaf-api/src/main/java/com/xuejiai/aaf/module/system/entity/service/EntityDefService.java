package com.xuejiai.aaf.module.system.entity.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.entity.domain.EntityDef;
import com.xuejiai.aaf.module.system.entity.repository.EntityDefRepository;
import com.xuejiai.aaf.module.system.entity.vo.CustomFieldAddDTO;
import com.xuejiai.aaf.module.system.entity.vo.CustomFieldVO;
import com.xuejiai.aaf.module.system.entity.vo.EntityDefCreateDTO;
import com.xuejiai.aaf.module.system.entity.vo.EntityDefUpdateDTO;
import com.xuejiai.aaf.module.system.entity.vo.EntityDefVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 实体定义业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityDefService {

    private final EntityDefRepository entityDefRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 字段类型 → DDL 类型映射 */
    private static final Map<String, String> TYPE_MAPPING =
            Map.of(
                    "text", "VARCHAR(500)",
                    "number", "NUMERIC",
                    "date", "TIMESTAMP",
                    "boolean", "BOOLEAN",
                    "select", "VARCHAR(100)");

    /** 查询全量实体定义 */
    public List<EntityDefVO> listAll() {
        return entityDefRepository.findAll().stream().map(this::toVO).toList();
    }

    /** 查询单个实体定义 */
    public EntityDefVO getById(Long id) {
        return toVO(findById(id));
    }

    /** 创建实体定义 */
    @Transactional
    public EntityDefVO create(EntityDefCreateDTO dto) {
        if (entityDefRepository.existsBySlug(dto.slug())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "slug 已存在: " + dto.slug());
        }
        var entity = new EntityDef();
        entity.setSlug(dto.slug());
        entity.setConfig(dto.config());
        entity.setBuiltin(false);
        entity.setEnabled(dto.enabled() != null ? dto.enabled() : true);
        entityDefRepository.save(entity);
        syncTable(entity);
        return toVO(entity);
    }

    /** 更新实体定义（内置配置不可覆盖） */
    @Transactional
    public EntityDefVO update(Long id, EntityDefUpdateDTO dto) {
        var entity = findById(id);
        if (entity.getBuiltin()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "内置实体定义不可修改");
        }
        if (dto.config() != null) {
            entity.setConfig(dto.config());
        }
        if (dto.enabled() != null) {
            entity.setEnabled(dto.enabled());
        }
        entityDefRepository.save(entity);
        syncTable(entity);
        return toVO(entity);
    }

    /** 删除实体定义 */
    @Transactional
    public void delete(Long id) {
        var entity = findById(id);
        if (entity.getBuiltin()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "内置实体定义不可删除");
        }
        entityDefRepository.delete(entity);
    }

    /** 根据 slug 获取实体定义（供通用 CRUD 使用） */
    public EntityDef getBySlug(String slug) {
        return entityDefRepository
                .findBySlug(slug)
                .orElseThrow(
                        () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "实体定义不存在: " + slug));
    }

    // ========== 自定义字段管理 ==========

    /** 添加自定义字段：更新 config JSON + ALTER TABLE ADD COLUMN */
    @Transactional
    public CustomFieldVO addField(String slug, CustomFieldAddDTO dto) {
        var entity = getBySlug(slug);
        var config = parseConfig(entity.getConfig());
        var fields = getFieldsList(config);

        // 检查字段名是否已存在
        var exists = fields.stream().anyMatch(f -> dto.name().equals(f.get("name")));
        if (exists) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "字段已存在: " + dto.name());
        }

        // 构建新字段
        var field = new HashMap<String, Object>();
        field.put("name", dto.name());
        field.put("label", dto.label());
        field.put("type", dto.type());
        field.put("hidden", false);
        if (dto.options() != null && !dto.options().isEmpty()) {
            field.put("options", dto.options());
        }
        fields.add(field);

        // 更新 config
        config.put("fields", fields);
        entity.setConfig(toJson(config));
        entityDefRepository.save(entity);

        // ALTER TABLE ADD COLUMN
        var tableName = "data_" + slug;
        if (tableExists(tableName)) {
            var colName = sanitizeIdentifier(dto.name());
            var colType = mapType(dto.type());
            var sql = "ALTER TABLE %s ADD COLUMN %s %s".formatted(tableName, colName, colType);
            jdbcTemplate.execute(sql);
            log.info("自定义字段加列: {}.{}", tableName, colName);
        }

        return new CustomFieldVO(dto.name(), dto.label(), dto.type(), dto.options(), false);
    }

    /** 隐藏自定义字段：标记 hidden=true，不删除列 */
    @Transactional
    public void hideField(String slug, String fieldName) {
        var entity = getBySlug(slug);
        var config = parseConfig(entity.getConfig());
        var fields = getFieldsList(config);

        var found =
                fields.stream()
                        .filter(f -> fieldName.equals(f.get("name")))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "字段不存在: " + fieldName));
        found.put("hidden", true);

        config.put("fields", fields);
        entity.setConfig(toJson(config));
        entityDefRepository.save(entity);
        log.info("隐藏自定义字段: {}.{}", slug, fieldName);
    }

    /** 查询实体的所有字段（含隐藏状态） */
    public List<CustomFieldVO> listFields(String slug) {
        var entity = getBySlug(slug);
        var config = parseConfig(entity.getConfig());
        var fields = getFieldsList(config);
        return fields.stream()
                .map(
                        f ->
                                new CustomFieldVO(
                                        (String) f.get("name"),
                                        (String) f.get("label"),
                                        (String) f.get("type"),
                                        f.get("options") instanceof List<?> opts
                                                ? opts.stream().map(Object::toString).toList()
                                                : null,
                                        Boolean.TRUE.equals(f.get("hidden"))))
                .toList();
    }

    // ========== 自动建表逻辑 ==========

    /** 同步数据表结构：不存在则建表，存在则补列 */
    private void syncTable(EntityDef entity) {
        var tableName = "data_" + entity.getSlug();
        var fields = parseFields(entity.getConfig());
        if (fields.isEmpty()) {
            return;
        }

        if (tableExists(tableName)) {
            alterTable(tableName, fields);
        } else {
            createTable(tableName, fields);
        }
    }

    private boolean tableExists(String tableName) {
        var sql = "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, tableName));
    }

    private void createTable(String tableName, List<Map<String, String>> fields) {
        var sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(tableName).append(" (\n");
        sb.append("    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,\n");
        for (var field : fields) {
            var colName = sanitizeIdentifier(field.get("name"));
            var colType = mapType(field.get("type"));
            sb.append("    ").append(colName).append(" ").append(colType).append(",\n");
        }
        sb.append("    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n");
        sb.append("    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n");
        sb.append("    deleted BOOLEAN NOT NULL DEFAULT FALSE\n");
        sb.append(")");
        jdbcTemplate.execute(sb.toString());
        log.info("自动建表: {}", tableName);
    }

    private void alterTable(String tableName, List<Map<String, String>> fields) {
        var existingColumns = getExistingColumns(tableName);
        for (var field : fields) {
            var colName = sanitizeIdentifier(field.get("name"));
            if (!existingColumns.contains(colName)) {
                var colType = mapType(field.get("type"));
                var sql = "ALTER TABLE %s ADD COLUMN %s %s".formatted(tableName, colName, colType);
                jdbcTemplate.execute(sql);
                log.info("自动加列: {}.{}", tableName, colName);
            }
        }
    }

    private Set<String> getExistingColumns(String tableName) {
        var sql = "SELECT column_name FROM information_schema.columns WHERE table_name = ?";
        var columns = jdbcTemplate.queryForList(sql, String.class, tableName);
        return new HashSet<>(columns);
    }

    private String mapType(String fieldType) {
        var mapped = TYPE_MAPPING.get(fieldType);
        if (mapped == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "不支持的字段类型: " + fieldType);
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseFields(String config) {
        try {
            var map = objectMapper.readValue(config, new TypeReference<Map<String, Object>>() {});
            var fields = map.get("fields");
            if (fields instanceof List<?> list) {
                return list.stream().map(item -> (Map<String, String>) item).toList();
            }
            return List.of();
        } catch (Exception e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "config JSON 解析失败");
        }
    }

    private String sanitizeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("^[a-z][a-z0-9_]*$")) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "非法字段名: " + identifier);
        }
        return identifier;
    }

    // ========== JSON 辅助 ==========

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String config) {
        try {
            return objectMapper.readValue(config, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "config JSON 解析失败");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getFieldsList(Map<String, Object> config) {
        var fields = config.get("fields");
        if (fields instanceof List<?> list) {
            return new ArrayList<>(
                    list.stream().map(item -> new HashMap<>((Map<String, Object>) item)).toList());
        }
        return new ArrayList<>();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "JSON 序列化失败");
        }
    }

    // ========== 转换 ==========

    private EntityDef findById(Long id) {
        return entityDefRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "实体定义不存在"));
    }

    private EntityDefVO toVO(EntityDef e) {
        return new EntityDefVO(
                e.getId(),
                e.getSlug(),
                e.getConfig(),
                e.getBuiltin(),
                e.getEnabled(),
                e.getCreateTime(),
                e.getUpdateTime());
    }
}
