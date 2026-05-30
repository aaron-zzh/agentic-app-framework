package com.xuejiai.aaf.framework.engine.dataprocess.table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 动态数据表服务——创建表/DDL 执行/通用 CRUD。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicTableService {

    private final DataTableRepository tableRepository;
    private final EntityManager entityManager;

    /** SQL 标识符白名单：小写字母/下划线开头，最长 63 字符 */
    private static final Pattern IDENT = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

    private static final String TABLE_PREFIX = "data_";

    public DataTableRepository getTableRepository() {
        return tableRepository;
    }

    /** 校验 SQL 标识符合法性 */
    private void requireIdent(String s) {
        if (s == null || !IDENT.matcher(s).matches()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "非法标识符: " + s);
        }
    }

    /** 校验列名必须属于表定义已知列（id 例外） */
    private void requireKnownColumns(DataTableDefinition table, Collection<String> cols) {
        var known = table.getColumns().stream()
                .map(DataColumnDefinition::getName)
                .collect(Collectors.toSet());
        for (var c : cols) {
            if (!"id".equals(c) && !known.contains(c)) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "未知列: " + c);
            }
        }
    }

    /** 创建自定义表（元数据 + 实际 DDL）。 */
    @Transactional
    public DataTableDefinition createTable(
            String slug,
            String displayName,
            String description,
            List<DataColumnDefinition> columns) {
        requireIdent(slug);
        for (var col : columns) {
            requireIdent(col.getName());
        }

        if (tableRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("表 slug 已存在: " + slug);
        }

        var tableName = TABLE_PREFIX + slug;
        var definition = new DataTableDefinition();
        definition.setSlug(slug);
        definition.setDisplayName(displayName);
        definition.setDescription(description);
        definition.setTableName(tableName);
        definition.setCreatedAt(Instant.now());

        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).setSortOrder(i);
        }
        definition.setColumns(columns);

        // 保存元数据
        var saved = tableRepository.save(definition);

        // 执行 DDL
        executeDdl(tableName, columns);
        log.info("动态表已创建: {} ({})", slug, tableName);
        return saved;
    }

    /** 插入数据（单条）。 */
    @Transactional
    public Map<String, Object> insertRow(String slug, Map<String, Object> row) {
        var table = getTable(slug);
        requireKnownColumns(table, row.keySet());
        var columns = new ArrayList<>(row.keySet());
        var placeholders = columns.stream().map(c -> ":" + c).toList();

        var sql =
                "INSERT INTO %s (id, %s) VALUES (nextval('%s_id_seq'), %s) RETURNING id"
                        .formatted(
                                table.getTableName(),
                                String.join(", ", columns),
                                table.getTableName(),
                                String.join(", ", placeholders));

        var query = entityManager.createNativeQuery(sql);
        for (var col : columns) {
            query.setParameter(col, row.get(col));
        }
        var id = ((Number) query.getSingleResult()).longValue();
        row.put("id", id);
        return row;
    }

    /** 批量插入。 */
    @Transactional
    public int insertBatch(String slug, List<Map<String, Object>> rows) {
        int count = 0;
        for (var row : rows) {
            insertRow(slug, row);
            count++;
        }
        return count;
    }

    /** 分页查询。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queryRows(
            String slug, int page, int size, Map<String, Object> filters) {
        var table = getTable(slug);
        if (filters != null && !filters.isEmpty()) {
            requireKnownColumns(table, filters.keySet());
        }
        var sb = new StringBuilder("SELECT * FROM " + table.getTableName());

        var params = new HashMap<String, Object>();
        if (filters != null && !filters.isEmpty()) {
            var conditions = new ArrayList<String>();
            for (var entry : filters.entrySet()) {
                conditions.add(entry.getKey() + " = :" + entry.getKey());
                params.put(entry.getKey(), entry.getValue());
            }
            sb.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        sb.append(" ORDER BY id DESC LIMIT :limit OFFSET :offset");
        params.put("limit", size);
        params.put("offset", page * size);

        var query = entityManager.createNativeQuery(sb.toString());
        for (var entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        // 将结果转为 Map 列表
        var columns = table.getColumns().stream().map(DataColumnDefinition::getName).toList();
        var results = new ArrayList<Map<String, Object>>();
        for (var row : (List<Object[]>) query.getResultList()) {
            var map = new HashMap<String, Object>();
            map.put("id", row[0]);
            for (int i = 0; i < columns.size() && i + 1 < row.length; i++) {
                map.put(columns.get(i), row[i + 1]);
            }
            results.add(map);
        }
        return results;
    }

    /** 更新单条。 */
    @Transactional
    public void updateRow(String slug, Long id, Map<String, Object> fields) {
        var table = getTable(slug);
        requireKnownColumns(table, fields.keySet());
        var sets = fields.keySet().stream().map(k -> k + " = :" + k).toList();

        var sql =
                "UPDATE %s SET %s WHERE id = :id"
                        .formatted(table.getTableName(), String.join(", ", sets));
        var query = entityManager.createNativeQuery(sql);
        for (var entry : fields.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        query.setParameter("id", id);
        query.executeUpdate();
    }

    /** 删除单条。 */
    @Transactional
    public void deleteRow(String slug, Long id) {
        var table = getTable(slug);
        entityManager
                .createNativeQuery("DELETE FROM %s WHERE id = :id".formatted(table.getTableName()))
                .setParameter("id", id)
                .executeUpdate();
    }

    /** 获取表定义。 */
    public DataTableDefinition getTable(String slug) {
        return tableRepository
                .findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("表不存在: " + slug));
    }

    // ========== DDL ==========

    private void executeDdl(String tableName, List<DataColumnDefinition> columns) {
        var sb = new StringBuilder("CREATE TABLE %s (\n".formatted(tableName));
        sb.append("    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,\n");

        for (int i = 0; i < columns.size(); i++) {
            var col = columns.get(i);
            sb.append("    ")
                    .append(col.getName())
                    .append(" ")
                    .append(mapType(col.getColumnType()));
            if (!col.isNullable()) sb.append(" NOT NULL");
            if (col.isUniqueCol()) sb.append(" UNIQUE");
            if (col.getDefaultValue() != null) sb.append(" DEFAULT ").append(col.getDefaultValue());
            if (i < columns.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(")");

        entityManager.createNativeQuery(sb.toString()).executeUpdate();

        // 创建序列（IDENTITY 自动处理，无需额外操作）
    }

    private String mapType(String columnType) {
        return switch (columnType) {
            case "string" -> "VARCHAR(256)";
            case "text" -> "TEXT";
            case "integer" -> "BIGINT";
            case "decimal" -> "NUMERIC(18,4)";
            case "boolean" -> "BOOLEAN";
            case "timestamp" -> "TIMESTAMPTZ";
            case "json" -> "JSONB";
            default -> "TEXT";
        };
    }
}
